package com.example.dhtcopy.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

public class Node {
    private final String id;
    private final String host;
    private final int port;
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private volatile boolean active = true;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private volatile LocalDateTime lastHealthCheck = LocalDateTime.now();
    private final AtomicLong operationCount = new AtomicLong(0);

    public Node(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public String put(String key, String value) {
        if (!active) {
            throw new IllegalStateException("Node " + id + " is not active");
        }
        operationCount.incrementAndGet();
        return storage.put(key, value);
    }

    public String get(String key) {
        if (!active) {
            throw new IllegalStateException("Node " + id + " is not active");
        }
        operationCount.incrementAndGet();
        return storage.get(key);
    }

    public String remove(String key) {
        if (!active) {
            throw new IllegalStateException("Node " + id + " is not active");
        }
        operationCount.incrementAndGet();
        return storage.remove(key);
    }

    public boolean containsKey(String key) {
        return active && storage.containsKey(key);
    }

    public Map<String, String> getAllData() {
        return new ConcurrentHashMap<>(storage);
    }

    public void transferData(Map<String, String> data) {
        if (!active) {
            throw new IllegalStateException("Cannot transfer data to inactive node");
        }
        storage.putAll(data);
    }

    public void clearData() {
        storage.clear();
    }

    public int getDataSize() {
        return storage.size();
    }

    public void updateHealthCheck() {
        this.lastHealthCheck = LocalDateTime.now();
    }

    public boolean isHealthy() {
        return active && lastHealthCheck.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    // Getters and setters
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
    public long getOperationCount() { return operationCount.get(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Node{id='%s', host='%s', port=%d, active=%s, dataSize=%d, operations=%d}",
                id, host, port, active, storage.size(), operationCount.get());
    }
}