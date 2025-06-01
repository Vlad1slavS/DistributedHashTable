package com.example.dhtcopy.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class StatusDto {
    private int totalNodes;
    private int activeNodes;
    private boolean isRebalancing;
    private long totalOperations;
    private long totalKeys;
    private double averageReadLatency;
    private double averageWriteLatency;
    private Map<String, Integer> dataDistribution;
    private LocalDateTime timestamp;

    public StatusDto() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and setters
    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public int getActiveNodes() { return activeNodes; }
    public void setActiveNodes(int activeNodes) { this.activeNodes = activeNodes; }

    public boolean isRebalancing() { return isRebalancing; }
    public void setRebalancing(boolean isRebalancing) { this.isRebalancing = isRebalancing; }

    public long getTotalOperations() { return totalOperations; }
    public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }

    public long getTotalKeys() { return totalKeys; }
    public void setTotalKeys(long totalKeys) { this.totalKeys = totalKeys; }

    public double getAverageReadLatency() { return averageReadLatency; }
    public void setAverageReadLatency(double averageReadLatency) { this.averageReadLatency = averageReadLatency; }

    public double getAverageWriteLatency() { return averageWriteLatency; }
    public void setAverageWriteLatency(double averageWriteLatency) { this.averageWriteLatency = averageWriteLatency; }

    public Map<String, Integer> getDataDistribution() { return dataDistribution; }
    public void setDataDistribution(Map<String, Integer> dataDistribution) { this.dataDistribution = dataDistribution; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}

