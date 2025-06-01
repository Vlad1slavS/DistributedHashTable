package com.example.dhtcopy.service;

import com.example.dhtcopy.core.Node;
import com.example.dhtcopy.core.ConsistentHashRing;
import com.example.dhtcopy.dto.NodeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class NodeService {
    private static final Logger logger = LoggerFactory.getLogger(NodeService.class);

    private final ConsistentHashRing hashRing;
    private final List<Node> failedNodes = new CopyOnWriteArrayList<>();

    @Autowired
    public NodeService(ConsistentHashRing hashRing) {
        this.hashRing = hashRing;
    }

    public Node createNode(String id, String host, int port) {
        return new Node(id, host, port);
    }

    public void addNode(Node node) {
        hashRing.addNode(node);
        logger.info("Added node: {}", node.getId());
    }

    public boolean removeNode(String nodeId) {
        boolean removed = hashRing.removeNode(nodeId);
        if (removed) {
            logger.info("Removed node: {}", nodeId);
        }
        return removed;
    }

    public List<NodeDto> getAllNodes() {
        return hashRing.getAllNodes().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public NodeDto getNode(String nodeId) {
        return hashRing.getAllNodes().stream()
                .filter(node -> node.getId().equals(nodeId))
                .map(this::convertToDto)
                .findFirst()
                .orElse(null);
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void performHealthCheck() {
        List<Node> allNodes = hashRing.getAllNodes();

        for (Node node : allNodes) {
            try {
                boolean isHealthy = checkNodeHealth(node);
                if (!isHealthy && node.isActive()) {
                    logger.warn("Node {} failed health check", node.getId());
                    node.setActive(false);
                    failedNodes.add(node);
                } else if (isHealthy && !node.isActive()) {
                    logger.info("Node {} recovered", node.getId());
                    node.setActive(true);
                    failedNodes.remove(node);
                }

                node.updateHealthCheck();
            } catch (Exception e) {
                logger.error("Health check failed for node {}: {}", node.getId(), e.getMessage());
            }
        }
    }

    private boolean checkNodeHealth(Node node) {
        try {
            // Simple health check - try to perform a basic operation
            String testKey = "__health_check__";
            node.put(testKey, "test");
            String value = node.get(testKey);
            node.remove(testKey);
            return "test".equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    public List<Node> getActiveNodes() {
        return hashRing.getAllNodes().stream()
                .filter(Node::isActive)
                .collect(Collectors.toList());
    }

    public List<Node> getFailedNodes() {
        return List.copyOf(failedNodes);
    }

    private NodeDto convertToDto(Node node) {
        NodeDto dto = new NodeDto(node.getId(), node.getHost(), node.getPort());
        dto.setActive(node.isActive());
        dto.setDataSize(node.getDataSize());
        dto.setOperationCount(node.getOperationCount());
        return dto;
    }
}

