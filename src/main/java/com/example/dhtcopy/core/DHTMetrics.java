package com.example.dhtcopy.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DHTMetrics {
    private final MeterRegistry meterRegistry;

    private final Counter readOperations;
    private final Counter writeOperations;
    private final Counter deleteOperations;
    private final Counter failedOperations;
    private final Counter nodeAdditions;
    private final Counter nodeRemovals;

    private final Timer readLatency;
    private final Timer writeLatency;

    private final Map<String, AtomicLong> nodeOperations = new ConcurrentHashMap<>();

    @Autowired
    public DHTMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.readOperations = Counter.builder("dht.operations.read")
                .description("Total number of read operations")
                .register(meterRegistry);

        this.writeOperations = Counter.builder("dht.operations.write")
                .description("Total number of write operations")
                .register(meterRegistry);

        this.deleteOperations = Counter.builder("dht.operations.delete")
                .description("Total number of delete operations")
                .register(meterRegistry);

        this.failedOperations = Counter.builder("dht.operations.failed")
                .description("Total number of failed operations")
                .register(meterRegistry);

        this.nodeAdditions = Counter.builder("dht.nodes.added")
                .description("Total number of nodes added")
                .register(meterRegistry);

        this.nodeRemovals = Counter.builder("dht.nodes.removed")
                .description("Total number of nodes removed")
                .register(meterRegistry);

        this.readLatency = Timer.builder("dht.latency.read")
                .description("Read operation latency")
                .register(meterRegistry);

        this.writeLatency = Timer.builder("dht.latency.write")
                .description("Write operation latency")
                .register(meterRegistry);
    }

    public void recordReadOperation(long latencyMs) {
        readOperations.increment();
        readLatency.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordWriteOperation(long latencyMs) {
        writeOperations.increment();
        writeLatency.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordDeleteOperation() {
        deleteOperations.increment();
    }

    public void recordFailedOperation() {
        failedOperations.increment();
    }

    public void recordNodeAddition() {
        nodeAdditions.increment();
    }

    public void recordNodeRemoval() {
        nodeRemovals.increment();
    }

    public void recordNodeOperation(String nodeId) {
        nodeOperations.computeIfAbsent(nodeId, k -> {
            AtomicLong counter = new AtomicLong(0);
            // Правильное создание Gauge с явными типами
            Gauge.builder("dht.node.operations", counter, value -> value.get())
                    .tag("node_id", nodeId)
                    .description("Operations per node")
                    .register(meterRegistry);
            return counter;
        }).incrementAndGet();
    }

    public long getReadOperations() {
        return (long) readOperations.count();
    }

    public long getWriteOperations() {
        return (long) writeOperations.count();
    }

    public long getDeleteOperations() {
        return (long) deleteOperations.count();
    }

    public long getFailedOperations() {
        return (long) failedOperations.count();
    }

    public double getAverageReadLatency() {
        return readLatency.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public double getAverageWriteLatency() {
        return writeLatency.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public Map<String, Long> getNodeOperations() {
        Map<String, Long> result = new HashMap<>();
        nodeOperations.forEach((nodeId, counter) -> result.put(nodeId, counter.get()));
        return result;
    }

    // Дополнительные методы для управления gauge метриками
    public void registerNodeGauge(String nodeId, AtomicLong operationCounter) {
        Gauge.builder("dht.node.operations", operationCounter, AtomicLong::get)
                .tag("node_id", nodeId)
                .description("Operations per node")
                .register(meterRegistry);
    }

    // Метод для регистрации других типов gauge метрик
    public void registerDataSizeGauge(String nodeId, java.util.function.Supplier<Integer> dataSizeSupplier) {
        Gauge.builder("dht.node.data_size", dataSizeSupplier, size -> size.get().doubleValue())
                .tag("node_id", nodeId)
                .description("Data size per node")
                .register(meterRegistry);
    }

    // Метод для системных метрик
    public void registerSystemMetrics() {
        // Общее количество нод
        Gauge.builder("dht.cluster.total_nodes", nodeOperations, map -> map.size())
                .description("Total number of nodes in the cluster")
                .register(meterRegistry);
    }
}
