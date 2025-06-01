package com.example.dhtcopy.core;

import com.example.dhtcopy.service.ReplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DistributedHashTable {
    private static final Logger logger = LoggerFactory.getLogger(DistributedHashTable.class);

    private final ConsistentHashRing hashRing;
    private final ReplicationService replicationService;
    private final DHTMetrics metrics;
    private final int replicationFactor;
    private final ExecutorService executorService;
    private final Map<String, CompletableFuture<Void>> rebalancingTasks = new ConcurrentHashMap<>();

    @Autowired
    public DistributedHashTable(
            ConsistentHashRing hashRing,
            ReplicationService replicationService,
            DHTMetrics metrics,
            @Value("${dht.replication-factor:3}") int replicationFactor) {
        this.hashRing = hashRing;
        this.replicationService = replicationService;
        this.metrics = metrics;
        this.replicationFactor = replicationFactor;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void addNode(Node node) {
        logger.info("Adding node: {}", node.getHost());

        // Сначала добавляем ноду в кольцо
        hashRing.addNode(node);

        // Затем выполняем перебалансировку
        CompletableFuture<Void> rebalanceTask = CompletableFuture.runAsync(() -> {
            rebalanceAfterAddition(node);
        }, executorService);

        rebalancingTasks.put(node.getId(), rebalanceTask);
        metrics.recordNodeAddition();
    }

    public boolean removeNode(String nodeId) {
        logger.info("Removing node: {}", nodeId);

        Node nodeToRemove = hashRing.getAllNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);

        if (nodeToRemove == null) {
            logger.warn("Node {} not found", nodeId);
            return false;
        }

        Map<String, String> dataToRedistribute = new HashMap<>(nodeToRemove.getAllData());

        nodeToRemove.setActive(false);
        boolean removed = hashRing.removeNode(nodeId);

        if (removed && !dataToRedistribute.isEmpty()) {
            // ВАЖНО: проверяем, есть ли другие активные ноды
            List<Node> remainingActiveNodes = hashRing.getAllNodes().stream()
                    .filter(Node::isActive)
                    .toList();

            if (!remainingActiveNodes.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    redistributeData(dataToRedistribute);
                }, executorService);
            } else {
                logger.warn("No remaining active nodes to redistribute data from {}", nodeId);
            }

            metrics.recordNodeRemoval();
        }

        return removed;
    }


    public void put(String key, String value) {
        long startTime = System.currentTimeMillis();

        try {
            List<Node> nodes = hashRing.getNodes(key, replicationFactor);

            if (nodes.isEmpty()) {
                throw new IllegalStateException("No active nodes available");
            }

            // Убираем дублирование и гарантируем что каждая нода используется только один раз
            Set<String> usedNodeIds = new HashSet<>();
            List<Node> targetNodes = nodes.stream()
                    .filter(node -> usedNodeIds.add(node.getId())) // Только уникальные ноды
                    .limit(replicationFactor) // Не больше чем replicationFactor
                    .toList();

            logger.debug("Storing key '{}' on {} nodes: {}", key, targetNodes.size(),
                    targetNodes.stream().map(Node::getId).collect(Collectors.toList()));

            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (Node node : targetNodes) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        node.put(key, value);
                        metrics.recordNodeOperation(node.getId());
                        return true;
                    } catch (Exception e) {
                        logger.error("Failed to store key {} on node {}: {}", key, node.getId(), e.getMessage());
                        return false;
                    }
                }, executorService);
                futures.add(future);
            }

            // Ждем кворума записей - корректируем для реального количества нод
            int requiredSuccess = Math.min((targetNodes.size() / 2) + 1, targetNodes.size());
            int successCount = 0;

            for (CompletableFuture<Boolean> future : futures) {
                try {
                    if (future.get(5, TimeUnit.SECONDS)) {
                        successCount++;
                        if (successCount >= requiredSuccess) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Write operation failed: {}", e.getMessage());
                }
            }

            if (successCount < requiredSuccess) {
                metrics.recordFailedOperation();
                throw new RuntimeException("Failed to achieve write quorum");
            }

            long latency = System.currentTimeMillis() - startTime;
            metrics.recordWriteOperation(latency);

        } catch (Exception e) {
            metrics.recordFailedOperation();
            throw e;
        }
    }

    public String get(String key) {
        long startTime = System.currentTimeMillis();

        try {
            List<Node> nodes = hashRing.getNodes(key, replicationFactor);

            if (nodes.isEmpty()) {
                return null;
            }

            // Try to read from nodes in order of preference
            for (Node node : nodes) {
                try {
                    String value = node.get(key);
                    if (value != null) {
                        metrics.recordNodeOperation(node.getId());
                        long latency = System.currentTimeMillis() - startTime;
                        metrics.recordReadOperation(latency);
                        return value;
                    }
                } catch (Exception e) {
                    logger.error("Failed to read key {} from node {}: {}", key, node.getId(), e.getMessage());
                }
            }

            long latency = System.currentTimeMillis() - startTime;
            metrics.recordReadOperation(latency);
            return null;

        } catch (Exception e) {
            metrics.recordFailedOperation();
            throw e;
        }
    }

    public boolean remove(String key) {
        try {
            List<Node> nodes = hashRing.getNodes(key, replicationFactor);
            boolean removed = false;

            // Удаляем только с уникальных нод
            Set<String> processedNodes = new HashSet<>();
            for (Node node : nodes) {
                if (processedNodes.add(node.getId())) { // Только если нода еще не обработана
                    try {
                        String removedValue = node.remove(key);
                        if (removedValue != null) {
                            removed = true;
                            metrics.recordNodeOperation(node.getId());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to remove key {} from node {}: {}", key, node.getId(), e.getMessage());
                    }
                }
            }

            if (removed) {
                metrics.recordDeleteOperation();
            }

            return removed;

        } catch (Exception e) {
            metrics.recordFailedOperation();
            throw e;
        }
    }

    private void rebalanceAfterAddition(Node newNode) {
        logger.info("Starting rebalancing after adding node: {}", newNode.getId());

        List<Node> allNodes = hashRing.getAllNodes();
        Set<String> keysToMove = new HashSet<>();
        Map<String, String> dataToMove = new HashMap<>();

        // Собираем все уникальные ключи из системы
        Set<String> allKeys = new HashSet<>();
        for (Node existingNode : allNodes) {
            if (!existingNode.getId().equals(newNode.getId())) {
                allKeys.addAll(existingNode.getAllData().keySet());
            }
        }

        // Для каждого ключа определяем правильных владельцев
        for (String key : allKeys) {
            List<Node> correctOwners = hashRing.getNodes(key, replicationFactor);

            // Получаем уникальные ID владельцев
            Set<String> correctOwnerIds = correctOwners.stream()
                    .map(Node::getId)
                    .collect(Collectors.toSet());

            // Если новая нода должна владеть этим ключом
            if (correctOwnerIds.contains(newNode.getId())) {

                // Найдем ноду, которая имеет этот ключ и может его отдать
                for (Node existingNode : allNodes) {
                    if (!existingNode.getId().equals(newNode.getId()) &&
                            existingNode.getAllData().containsKey(key)) {

                        // Если существующая нода больше не должна владеть этим ключом
                        if (!correctOwnerIds.contains(existingNode.getId())) {
                            String value = existingNode.getAllData().get(key);
                            if (value != null && !keysToMove.contains(key)) {
                                dataToMove.put(key, value);
                                keysToMove.add(key);
                                // Удаляем с неправильного владельца
                                existingNode.remove(key);
                                logger.debug("Moving key '{}' from node '{}' to node '{}'",
                                        key, existingNode.getId(), newNode.getId());
                                break; // Берем только одну копию
                            }
                        }
                    }
                }
            }
        }

        // Перемещаем данные на новую ноду
        int movedKeys = 0;
        for (Map.Entry<String, String> entry : dataToMove.entrySet()) {
            try {
                newNode.put(entry.getKey(), entry.getValue());
                movedKeys++;
            } catch (Exception e) {
                logger.error("Failed to move key {} to new node {}: {}",
                        entry.getKey(), newNode.getId(), e.getMessage());
            }
        }

        logger.info("Rebalancing completed for node: {}. Moved {} keys", newNode.getId(), movedKeys);
        rebalancingTasks.remove(newNode.getId());
    }

    private void redistributeData(Map<String, String> dataToRedistribute) {
        if (dataToRedistribute.isEmpty()) {
            logger.info("No data to redistribute");
            return;
        }

        logger.info("Redistributing {} keys", dataToRedistribute.size());

        List<Node> availableNodes = hashRing.getAllNodes().stream()
                .filter(Node::isActive)
                .collect(Collectors.toList());

        if (availableNodes.isEmpty()) {
            logger.error("Cannot redistribute data: no active nodes available");
            return;
        }

        int redistributedKeys = 0;
        int failedKeys = 0;

        for (Map.Entry<String, String> entry : dataToRedistribute.entrySet()) {
            try {
                // Используем внутренний метод без дополнительной репликации
                boolean success = putInternalSafe(entry.getKey(), entry.getValue());
                if (success) {
                    redistributedKeys++;
                } else {
                    failedKeys++;
                }
            } catch (Exception e) {
                logger.error("Failed to redistribute key {}: {}", entry.getKey(), e.getMessage());
                failedKeys++;
            }
        }

        logger.info("Data redistribution completed. Redistributed {} keys, failed {} keys",
                redistributedKeys, failedKeys);
    }

    // Безопасный внутренний метод для записи
    private boolean putInternalSafe(String key, String value) {
        try {
            List<Node> nodes = hashRing.getNodes(key, replicationFactor);

            if (nodes.isEmpty()) {
                logger.warn("No nodes available for key: {}", key);
                return false;
            }

            // Убираем дублирование нод и ограничиваем количество
            Set<String> usedNodeIds = new HashSet<>();
            List<Node> targetNodes = nodes.stream()
                    .filter(Node::isActive) // ТОЛЬКО активные ноды
                    .filter(node -> usedNodeIds.add(node.getId()))
                    .limit(replicationFactor)
                    .toList();

            if (targetNodes.isEmpty()) {
                logger.warn("No active target nodes for key: {}", key);
                return false;
            }

            // Записываем на все доступные ноды
            boolean anySuccess = false;
            for (Node node : targetNodes) {
                try {
                    node.put(key, value);
                    anySuccess = true;
                } catch (Exception e) {
                    logger.error("Failed to store key {} on node {} during redistribution: {}",
                            key, node.getId(), e.getMessage());
                }
            }

            return anySuccess;

        } catch (Exception e) {
            logger.error("Exception in putInternalSafe for key {}: {}", key, e.getMessage());
            return false;
        }
    }


    // Внутренний метод для репликации без логирования метрик
    private void putInternal(String key, String value) {
        List<Node> nodes = hashRing.getNodes(key, replicationFactor);

        if (nodes.isEmpty()) {
            throw new IllegalStateException("No active nodes available");
        }

        // Убираем дублирование нод
        Set<String> usedNodeIds = new HashSet<>();
        List<Node> targetNodes = nodes.stream()
                .filter(node -> usedNodeIds.add(node.getId()))
                .limit(replicationFactor)
                .toList();

        for (Node node : targetNodes) {
            try {
                node.put(key, value);
            } catch (Exception e) {
                logger.error("Failed to store key {} on node {} during redistribution: {}",
                        key, node.getId(), e.getMessage());
            }
        }
    }

    public List<Node> getAllNodes() {
        return hashRing.getAllNodes();
    }

    public Map<String, Integer> getDataDistribution() {
        return hashRing.getDataDistribution();
    }

    public boolean isRebalancing() {
        return !rebalancingTasks.isEmpty();
    }

    public DHTMetrics getMetrics() {
        return metrics;
    }

    public ConsistentHashRing getHashRing(){
        return this.hashRing;
    }

    // Диагностический метод для отладки
    public void printDetailedDistribution() {
        logger.info("=== DETAILED DATA DISTRIBUTION ===");
        List<Node> allNodes = hashRing.getAllNodes();

        for (Node node : allNodes) {
            logger.info("Node {}: {} keys", node.getId(), node.getDataSize());
            Map<String, String> nodeData = node.getAllData();

            // Показываем первые несколько ключей
            int count = 0;
            for (String key : nodeData.keySet()) {
                if (count++ < 3) {
                    logger.info("  - {}", key);
                }
            }
            if (nodeData.size() > 3) {
                logger.info("  ... and {} more keys", nodeData.size() - 3);
            }
        }
    }

    // Метод для подсчета уникальных ключей в системе
    public int getUniqueKeyCount() {
        Set<String> allUniqueKeys = new HashSet<>();
        List<Node> allNodes = hashRing.getAllNodes();

        for (Node node : allNodes) {
            allUniqueKeys.addAll(node.getAllData().keySet());
        }

        return allUniqueKeys.size();
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
