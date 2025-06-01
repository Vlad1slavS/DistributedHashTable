package com.example.dhtcopy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
public class ConsistentHashRing {
    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashRing.class);
    private final ConcurrentSkipListMap<Long, Node> ring = new ConcurrentSkipListMap<>();
    private final Map<String, Set<Long>> nodeHashes = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int virtualNodes;
    private final MessageDigest md5;

    public ConsistentHashRing() {
        this(150);
    }

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    public void addNode(Node node) {
        lock.writeLock().lock();
        try {
            // Проверяем, что нода еще не добавлена
            if (nodeHashes.containsKey(node.getId())) {
                logger.warn("Node {} is already in the ring", node.getId());
                return;
            }

            Set<Long> hashes = new HashSet<>();

            for (int i = 0; i < virtualNodes; i++) {
                String virtualNodeId = node.getId() + ":" + i;
                long hash = hash(virtualNodeId);
                ring.put(hash, node);
                hashes.add(hash);
            }

            nodeHashes.put(node.getId(), hashes);
            logger.debug("Added node {} with {} virtual nodes", node.getId(), virtualNodes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeNode(String nodeId) {
        lock.writeLock().lock();
        try {
            Set<Long> hashes = nodeHashes.remove(nodeId);
            if (hashes != null) {
                for (Long hash : hashes) {
                    ring.remove(hash);
                }
                logger.debug("Removed node {} with {} virtual nodes", nodeId, hashes.size());
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Node getNode(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }

            long hash = hash(key);
            Map.Entry<Long, Node> entry = ring.ceilingEntry(hash);

            if (entry == null) {
                entry = ring.firstEntry();
            }

            Node node = entry.getValue();
            return node.isActive() ? node : getNextActiveNode(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Node getNextActiveNode(long startHash) {
        Map.Entry<Long, Node> current = ring.ceilingEntry(startHash);

        // Search forward in the ring
        while (current != null) {
            if (current.getValue().isActive()) {
                return current.getValue();
            }
            current = ring.higherEntry(current.getKey());
        }

        // Wrap around to the beginning
        for (Map.Entry<Long, Node> entry : ring.entrySet()) {
            if (entry.getValue().isActive()) {
                return entry.getValue();
            }
        }

        return null; // No active nodes
    }

    // ОСНОВНОЕ ИСПРАВЛЕНИЕ: полностью переписанный метод getNodes
    public List<Node> getNodes(String key, int replicationFactor) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return new ArrayList<>();
            }

            // Получаем все уникальные активные ноды
            List<Node> allActiveNodes = getAllActiveNodes();

            if (allActiveNodes.isEmpty()) {
                logger.warn("No active nodes available for key: {}", key);
                return new ArrayList<>();
            }

            // Ограничиваем replicationFactor количеством доступных нод
            int effectiveReplicationFactor = Math.min(replicationFactor, allActiveNodes.size());

            List<Node> result = new ArrayList<>();
            Set<String> addedNodeIds = new LinkedHashSet<>(); // Для отслеживания уникальности

            long keyHash = hash(key);

            // Находим стартовую позицию в кольце
            Map.Entry<Long, Node> startEntry = ring.ceilingEntry(keyHash);
            if (startEntry == null) {
                startEntry = ring.firstEntry();
            }

            // Создаем упорядоченный список всех виртуальных нод, начиная с правильной позиции
            List<Map.Entry<Long, Node>> orderedVirtualNodes = getOrderedVirtualNodes(startEntry.getKey());

            // Проходим по виртуальным нодам в порядке кольца и добавляем уникальные физические ноды
            for (Map.Entry<Long, Node> entry : orderedVirtualNodes) {
                Node physicalNode = entry.getValue();

                // Добавляем только активные и еще не добавленные ноды
                if (physicalNode.isActive() && addedNodeIds.add(physicalNode.getId())) {
                    result.add(physicalNode);

                    if (result.size() >= effectiveReplicationFactor) {
                        break;
                    }
                }
            }

            logger.debug("Selected {} unique nodes for key '{}': {}",
                    result.size(), key,
                    result.stream().map(Node::getId).collect(Collectors.toList()));

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Вспомогательный метод для получения упорядоченного списка виртуальных нод
    private List<Map.Entry<Long, Node>> getOrderedVirtualNodes(long startHash) {
        List<Map.Entry<Long, Node>> orderedNodes = new ArrayList<>();

        // Добавляем все ноды начиная с startHash до конца кольца
        orderedNodes.addAll(ring.tailMap(startHash).entrySet());

        // Добавляем ноды от начала кольца до startHash (wrap around)
        orderedNodes.addAll(ring.headMap(startHash).entrySet());

        return orderedNodes;
    }

    // Вспомогательный метод для получения уникальных активных нод
    private List<Node> getAllActiveNodes() {
        return new ArrayList<>(ring.values().stream()
                .filter(Node::isActive)
                .collect(Collectors.toMap(
                        Node::getId,
                        node -> node,
                        (existing, replacement) -> existing)) // Убираем дубликаты по ID
                .values());
    }

    public int getUniqueNodesCount() {
        lock.readLock().lock();
        try {
            return nodeHashes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Node> getAllNodes() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(ring.values().stream()
                    .collect(Collectors.toMap(
                            Node::getId,
                            node -> node,
                            (existing, replacement) -> existing))
                    .values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Integer> getDataDistribution() {
        lock.readLock().lock();
        try {
            Map<String, Integer> distribution = new HashMap<>();
            for (Node node : getAllNodes()) {
                distribution.put(node.getId(), node.getDataSize());
            }
            return distribution;
        } finally {
            lock.readLock().unlock();
        }
    }

    private long hash(String key) {
        synchronized (md5) {
            md5.reset();
            md5.update(key.getBytes());
            byte[] digest = md5.digest();

            long hash = 0;
            for (int i = 0; i < 4; i++) {
                hash <<= 8;
                hash |= ((int) digest[i]) & 0xFF;
            }
            return hash;
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return ring.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Диагностические методы для отладки
    public void printRingStructure() {
        lock.readLock().lock();
        try {
            logger.info("=== CONSISTENT HASH RING STRUCTURE ===");
            logger.info("Total virtual nodes in ring: {}", ring.size());
            logger.info("Unique physical nodes: {}", getUniqueNodesCount());
            logger.info("Virtual nodes per physical node: {}", virtualNodes);

            Map<String, Integer> virtualNodeCount = new HashMap<>();
            for (Node node : ring.values()) {
                virtualNodeCount.merge(node.getId(), 1, Integer::sum);
            }

            logger.info("Actual virtual node distribution:");
            virtualNodeCount.forEach((nodeId, count) ->
                    logger.info("  Node {}: {} virtual nodes", nodeId, count));

        } finally {
            lock.readLock().unlock();
        }
    }

    // Метод для проверки консистентности кольца
    public boolean validateRingConsistency() {
        lock.readLock().lock();
        try {
            // Проверяем, что количество виртуальных нод соответствует ожидаемому
            for (Map.Entry<String, Set<Long>> entry : nodeHashes.entrySet()) {
                String nodeId = entry.getKey();
                Set<Long> hashes = entry.getValue();

                if (hashes.size() != virtualNodes) {
                    logger.error("Node {} has {} virtual nodes, expected {}",
                            nodeId, hashes.size(), virtualNodes);
                    return false;
                }

                // Проверяем, что все хэши есть в кольце
                for (Long hash : hashes) {
                    Node nodeInRing = ring.get(hash);
                    if (nodeInRing == null || !nodeInRing.getId().equals(nodeId)) {
                        logger.error("Inconsistency found for node {} at hash {}", nodeId, hash);
                        return false;
                    }
                }
            }

            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Тестовый метод для проверки распределения ключей
    public Map<String, Integer> testKeyDistribution(int keyCount) {
        Map<String, Integer> distribution = new HashMap<>();

        for (int i = 0; i < keyCount; i++) {
            String testKey = "test_key_" + i;
            List<Node> responsibleNodes = getNodes(testKey, 1);

            if (!responsibleNodes.isEmpty()) {
                String nodeId = responsibleNodes.get(0).getId();
                distribution.merge(nodeId, 1, Integer::sum);
            }
        }

        return distribution;
    }

    public void debugKeyPlacement(String key, int replicationFactor) {
        lock.readLock().lock();
        try {
            long keyHash = hash(key);
            System.out.println("=== DEBUG: Key '" + key + "' placement ===");
            System.out.println("Key hash: " + keyHash);

            // Находим стартовую позицию
            Map.Entry<Long, Node> startEntry = ring.ceilingEntry(keyHash);
            if (startEntry == null) {
                startEntry = ring.firstEntry();
            }

            System.out.println("Start position: " + startEntry.getKey() + " -> " + startEntry.getValue().getId());

            // Показываем процесс выбора нод
            List<Map.Entry<Long, Node>> orderedNodes = getOrderedVirtualNodes(startEntry.getKey());
            Set<String> selectedNodes = new LinkedHashSet<>();

            System.out.println("Walking the ring:");
            int count = 0;
            for (Map.Entry<Long, Node> entry : orderedNodes) {
                String nodeId = entry.getValue().getId();
                boolean isNew = selectedNodes.add(nodeId);

                System.out.println("  Position " + entry.getKey() + " -> " + nodeId +
                        (isNew ? " (SELECTED)" : " (duplicate, skipped)"));

                if (isNew) {
                    count++;
                    if (count >= replicationFactor) {
                        System.out.println("  Reached replication factor: " + replicationFactor);
                        break;
                    }
                }
            }

            System.out.println("Final selected nodes: " + selectedNodes);
            System.out.println("===============================");

        } finally {
            lock.readLock().unlock();
        }
    }
}
