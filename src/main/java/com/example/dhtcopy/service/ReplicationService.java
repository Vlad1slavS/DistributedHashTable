package com.example.dhtcopy.service;

import com.example.dhtcopy.core.Node;
import com.example.dhtcopy.core.ConsistentHashRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ReplicationService {
    private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

    private final ConsistentHashRing hashRing;
    private final int replicationFactor;
    private final ExecutorService executorService;

    @Autowired
    public ReplicationService(
            ConsistentHashRing hashRing,
            @Value("${dht.replication-factor:3}") int replicationFactor) {
        this.hashRing = hashRing;
        this.replicationFactor = replicationFactor;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public void replicateData(String key, String value) {
        List<Node> nodes = hashRing.getNodes(key, replicationFactor);

        for (Node node : nodes) {
            CompletableFuture.runAsync(() -> {
                try {
                    node.put(key, value);
                    logger.debug("Replicated key {} to node {}", key, node.getId());
                } catch (Exception e) {
                    logger.error("Failed to replicate key {} to node {}: {}",
                            key, node.getId(), e.getMessage());
                }
            }, executorService);
        }
    }

    public void removeReplicas(String key) {
        List<Node> nodes = hashRing.getNodes(key, replicationFactor);

        for (Node node : nodes) {
            CompletableFuture.runAsync(() -> {
                try {
                    node.remove(key);
                    logger.debug("Removed key {} from node {}", key, node.getId());
                } catch (Exception e) {
                    logger.error("Failed to remove key {} from node {}: {}",
                            key, node.getId(), e.getMessage());
                }
            }, executorService);
        }
    }

    public void repairInconsistency(String key, String correctValue) {
        List<Node> nodes = hashRing.getNodes(key, replicationFactor);

        for (Node node : nodes) {
            CompletableFuture.runAsync(() -> {
                try {
                    String currentValue = node.get(key);
                    if (!correctValue.equals(currentValue)) {
                        node.put(key, correctValue);
                        logger.info("Repaired inconsistency for key {} on node {}", key, node.getId());
                    }
                } catch (Exception e) {
                    logger.error("Failed to repair key {} on node {}: {}",
                            key, node.getId(), e.getMessage());
                }
            }, executorService);
        }
    }

    public void redistributeNodeData(Node failedNode, Node targetNode) {
        Map<String, String> data = failedNode.getAllData();

        for (Map.Entry<String, String> entry : data.entrySet()) {
            try {
                targetNode.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.error("Failed to redistribute key {} from {} to {}: {}",
                        entry.getKey(), failedNode.getId(), targetNode.getId(), e.getMessage());
            }
        }

        logger.info("Redistributed {} keys from node {} to node {}",
                data.size(), failedNode.getId(), targetNode.getId());
    }
}

