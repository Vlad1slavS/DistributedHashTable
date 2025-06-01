package com.example.dhtcopy.controller;

import com.example.dhtcopy.core.DistributedHashTable;
import com.example.dhtcopy.core.Node;
import com.example.dhtcopy.dto.KeyValueDto;
import com.example.dhtcopy.dto.NodeDto;
import com.example.dhtcopy.dto.StatusDto;
import com.example.dhtcopy.service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dht")
@Validated
public class DHTController {

    private final DistributedHashTable distributedHashTable;
    private final NodeService nodeService;

    @Autowired
    public DHTController(DistributedHashTable distributedHashTable, NodeService nodeService) {
        this.distributedHashTable = distributedHashTable;
        this.nodeService = nodeService;
    }

    // Key-Value Operations
    @PostMapping("/data")
    public ResponseEntity<String> put(@Valid @RequestBody KeyValueDto keyValue) {
        try {
            distributedHashTable.put(keyValue.getKey(), keyValue.getValue());
            return ResponseEntity.ok("Key stored successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to store key: " + e.getMessage());
        }
    }

    @GetMapping("/data/{key}")
    public ResponseEntity<String> get(@PathVariable String key) {
        try {
            String value = distributedHashTable.get(key);
            if (value != null) {
                return ResponseEntity.ok(value);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve key: " + e.getMessage());
        }
    }

    @DeleteMapping("/data/{key}")
    public ResponseEntity<String> delete(@PathVariable String key) {
        try {
            boolean removed = distributedHashTable.remove(key);
            if (removed) {
                return ResponseEntity.ok("Key deleted successfully");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete key: " + e.getMessage());
        }
    }

    // Node Management
    @PostMapping("/nodes")
    public ResponseEntity<String> addNode(@Valid @RequestBody NodeDto nodeDto) {
        try {
            Node node = nodeService.createNode(nodeDto.getId(), nodeDto.getHost(), nodeDto.getPort());
            distributedHashTable.addNode(node);
            return ResponseEntity.ok("Node added successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to add node: " + e.getMessage());
        }
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<String> removeNode(@PathVariable String nodeId) {
        try {
            boolean removed = distributedHashTable.removeNode(nodeId);
            if (removed) {
                return ResponseEntity.ok("Node removed successfully");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove node: " + e.getMessage());
        }
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<NodeDto>> getAllNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeDto> getNode(@PathVariable String nodeId) {
        NodeDto node = nodeService.getNode(nodeId);
        if (node != null) {
            return ResponseEntity.ok(node);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Status and Monitoring
    @GetMapping("/status")
    public ResponseEntity<StatusDto> getStatus() {
        StatusDto status = new StatusDto();
        List<Node> allNodes = distributedHashTable.getAllNodes();
        List<Node> activeNodes = nodeService.getActiveNodes();

        status.setTotalNodes(allNodes.size());
        status.setActiveNodes(activeNodes.size());
        status.setRebalancing(distributedHashTable.isRebalancing());
        status.setDataDistribution(distributedHashTable.getDataDistribution());

        // Set metrics
        var metrics = distributedHashTable.getMetrics();
        long totalOps = metrics.getReadOperations() +
                metrics.getWriteOperations() +
                metrics.getDeleteOperations();
        status.setTotalOperations(totalOps);
        status.setAverageReadLatency(metrics.getAverageReadLatency());
        status.setAverageWriteLatency(metrics.getAverageWriteLatency());

        // Calculate total keys
        long totalKeys = allNodes.stream()
                .mapToLong(Node::getDataSize)
                .sum();
        status.setTotalKeys(totalKeys);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/distribution")
    public ResponseEntity<Map<String, Integer>> getDataDistribution() {
        return ResponseEntity.ok(distributedHashTable.getDataDistribution());
    }

    @GetMapping("/metrics")
    public ResponseEntity<Object> getMetrics() {
        var metrics = distributedHashTable.getMetrics();
        Map<String, Object> metricsMap = Map.of(
                "readOperations", metrics.getReadOperations(),
                "writeOperations", metrics.getWriteOperations(),
                "deleteOperations", metrics.getDeleteOperations(),
                "failedOperations", metrics.getFailedOperations(),
                "averageReadLatency", metrics.getAverageReadLatency(),
                "averageWriteLatency", metrics.getAverageWriteLatency(),
                "nodeOperations", metrics.getNodeOperations()
        );
        return ResponseEntity.ok(metricsMap);
    }
}

