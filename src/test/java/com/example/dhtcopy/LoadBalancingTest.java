package com.example.dhtcopy;

import com.example.dhtcopy.core.DistributedHashTable;
import com.example.dhtcopy.core.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
public class LoadBalancingTest {

    @Autowired
    private DistributedHashTable distributedHashTable;

    @BeforeEach
    void setUp() {
        // Очищаем существующие ноды
        List<Node> existingNodes = distributedHashTable.getAllNodes();
        for (Node node : existingNodes) {
            distributedHashTable.removeNode(node.getId());
        }

        // Добавляем свежие ноды для теста
        Node node1 = new Node("test_node1", "localhost", 8001);
        Node node2 = new Node("test_node2", "localhost", 8002);
        Node node3 = new Node("test_node3", "localhost", 8003);

        distributedHashTable.addNode(node1);
        distributedHashTable.addNode(node2);
        distributedHashTable.addNode(node3);

        // Ждем небольшое время для стабилизации
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testDistributionWithManyKeys() {
        Node node1 = new Node("test_node1", "localhost", 8001);
        Node node2 = new Node("test_node2", "localhost", 8002);
        Node node3 = new Node("test_node3", "localhost", 8003);
        Node node4 = new Node("test_node4", "localhost", 8004);

        distributedHashTable.addNode(node1);
        distributedHashTable.addNode(node2);
        distributedHashTable.addNode(node3);
        distributedHashTable.addNode(node4);

        Map<String, Integer> totalDistribution = new HashMap<>();

        // Вставляем много ключей
        for (int i = 0; i < 1000; i++) {
            String key = "key_" + i;
            distributedHashTable.put(key, "value_" + i);

            var hashRing = distributedHashTable.getHashRing();


            List<Node> nodes = hashRing.getNodes(key, 3);
            for (Node node : nodes) {
                totalDistribution.merge(node.getId(), 1, Integer::sum);
            }
        }

        System.out.println("Distribution after 1000 keys: " + totalDistribution);
        // Здесь должно быть примерно равномерное распределение
    }


    @Test
    void testDataDistribution() {
        // Проверяем начальное состояние
        assertEquals(3, distributedHashTable.getAllNodes().size(),
                "Should start with 3 nodes");

        Map<String, Integer> initialDistribution = distributedHashTable.getDataDistribution();
        int initialTotalKeys = initialDistribution.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(0, initialTotalKeys, "Should start with no data");

        // Вставляем ключи
        int totalKeys = 100;
        Set<String> insertedKeys = new HashSet<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "key_" + i;
            distributedHashTable.put(key, "value_" + i);
            insertedKeys.add(key);
        }

        // Ждем завершения всех операций
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Ждем завершения перебалансировки
        while (distributedHashTable.isRebalancing()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Map<String, Integer> distribution = distributedHashTable.getDataDistribution();

        // Диагностическая информация
        System.out.println("=== DIAGNOSTIC INFO ===");
        System.out.println("Inserted unique keys: " + totalKeys);
        System.out.println("Expected replication factor: 3");
        System.out.println("Data distribution: " + distribution);
        System.out.println("Active nodes: " + distributedHashTable.getAllNodes().size());

        int totalStoredKeys = distribution.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Total stored keys (with replication): " + totalStoredKeys);
        System.out.println("Average replication per key: " + (double)totalStoredKeys / totalKeys);

        // Проверяем уникальность ключей
        int uniqueKeysInSystem = distributedHashTable.getUniqueKeyCount();
        System.out.println("Unique keys in system: " + uniqueKeysInSystem);

        // Проверяем консистентность - все ли ключи можно прочитать
        int readableKeys = 0;
        for (String key : insertedKeys) {
            if (distributedHashTable.get(key) != null) {
                readableKeys++;
            }
        }
        System.out.println("Readable keys: " + readableKeys + "/" + totalKeys);

        // Основные проверки
        assertTrue(distribution.size() >= 3, "Data should be distributed across all nodes");

        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            assertTrue(entry.getValue() > 0,
                    "Each node should have some data: " + entry.getKey() + " has " + entry.getValue());
        }

        // Проверяем разумные границы
        assertEquals(totalKeys, uniqueKeysInSystem,
                "Should have correct number of unique keys");
        assertEquals(totalKeys, readableKeys,
                "All inserted keys should be readable");

        // С replication factor = 3, ожидаем примерно 300 копий (с допуском ±10%)
        int expectedCopies = totalKeys * 3;
        assertTrue(totalStoredKeys >= expectedCopies * 0.9,
                "Should have at least " + (expectedCopies * 0.9) + " copies");
        assertTrue(totalStoredKeys <= expectedCopies * 1.1,
                "Should not have more than " + (expectedCopies * 1.1) + " copies, but got " + totalStoredKeys);
    }


    @Test
    void testNodeAdditionRebalancing() {
        // Добавляем начальные данные
        int initialKeys = 50;
        for (int i = 0; i < initialKeys; i++) {
            distributedHashTable.put("initial_key_" + i, "value_" + i);
        }

        Map<String, Integer> beforeDistribution = distributedHashTable.getDataDistribution();
        int totalKeysBefore = beforeDistribution.values().stream().mapToInt(Integer::intValue).sum();

        // Добавляем новую ноду
        Node newNode = new Node("test_node4", "localhost", 8004);
        distributedHashTable.addNode(newNode);

        // Ждем время для перебалансировки
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Integer> afterDistribution = distributedHashTable.getDataDistribution();

        // Проверяем, что новая нода присутствует в распределении
        assertTrue(afterDistribution.containsKey("test_node4"), "New node should be in distribution");
        assertEquals(4, afterDistribution.size(), "Should have 4 nodes");

        // Проверяем, что данные перераспределились
        boolean newNodeHasData = afterDistribution.get("test_node4") > 0;
        assertTrue(newNodeHasData, "New node should have received some data after rebalancing");
    }

    @Test
    void testLoadBalancingWithConcurrentWrites() throws InterruptedException {
        final int threadCount = 10;
        final int keysPerThread = 20;
        final int totalExpectedKeys = threadCount * keysPerThread;

        Thread[] threads = new Thread[threadCount];
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulWrites = new AtomicInteger(0);

        // Создаем потоки
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await(); // Ждем сигнала для старта

                    for (int j = 0; j < keysPerThread; j++) {
                        String key = "thread_" + threadId + "_key_" + j;
                        String value = "value_" + j;
                        try {
                            distributedHashTable.put(key, value);
                            successfulWrites.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Failed to write key " + key + ": " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
            threads[i].start();
        }

        // Запускаем все потоки одновременно
        startLatch.countDown();

        // Ждем завершения всех потоков
        finishLatch.await();

        // Проверяем результаты
        Map<String, Integer> distribution = distributedHashTable.getDataDistribution();
        int actualTotalStoredKeys = distribution.values().stream().mapToInt(Integer::intValue).sum();

        System.out.println("Successful writes: " + successfulWrites.get());
        System.out.println("Total keys stored (with replication): " + actualTotalStoredKeys);
        System.out.println("Distribution: " + distribution);

        // Проверяем, что записались все ожидаемые ключи
        assertEquals(totalExpectedKeys, successfulWrites.get(),
                "Should have successfully written all keys");

        // Проверяем, что данные распределены между нодами
        assertTrue(distribution.size() >= 3, "Data should be distributed across multiple nodes");

        // Проверяем, что общее количество ключей учитывает репликацию
        // При replication factor = 3, каждый ключ должен быть сохранен на ~3 нодах
        assertTrue(actualTotalStoredKeys >= totalExpectedKeys,
                "Should have at least " + totalExpectedKeys + " keys stored");

        // Но не более чем replicationFactor * totalExpectedKeys
        // (с небольшим допуском на неравномерность)
        assertTrue(actualTotalStoredKeys <= totalExpectedKeys * 3 + 10,
                "Should not have significantly more than " + (totalExpectedKeys * 3) +
                        " key copies, but got " + actualTotalStoredKeys);
    }

    @Test
    void testDataConsistency() throws InterruptedException {
        // Тест на консистентность данных
        Set<String> insertedKeys = new HashSet<>();

        // Вставляем ключи и запоминаем их
        for (int i = 0; i < 50; i++) {
            String key = "consistency_key_" + i;
            String value = "value_" + i;
            distributedHashTable.put(key, value);
            insertedKeys.add(key);
        }

        // Ждем небольшое время для завершения всех операций
        Thread.sleep(500);

        // Проверяем, что все ключи можно прочитать
        int readableKeys = 0;
        for (String key : insertedKeys) {
            String value = distributedHashTable.get(key);
            if (value != null) {
                readableKeys++;
            }
        }

        // Должны прочитать все вставленные ключи
        assertEquals(insertedKeys.size(), readableKeys,
                "Should be able to read all inserted keys");
    }

    @Test
    void testUniqueKeyCount() {
        // Вставляем известное количество уникальных ключей
        Set<String> uniqueKeys = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            String key = "unique_key_" + i;
            distributedHashTable.put(key, "value_" + i);
            uniqueKeys.add(key);
        }

        // Проверяем, что все ключи доступны для чтения
        int foundKeys = 0;
        for (String key : uniqueKeys) {
            if (distributedHashTable.get(key) != null) {
                foundKeys++;
            }
        }

        assertEquals(uniqueKeys.size(), foundKeys,
                "Should be able to retrieve all unique keys that were inserted");

        // Проверяем общую статистику (учитывая репликацию)
        Map<String, Integer> distribution = distributedHashTable.getDataDistribution();
        int totalStoredCopies = distribution.values().stream().mapToInt(Integer::intValue).sum();

        assertTrue(totalStoredCopies >= uniqueKeys.size(),
                "Should have at least as many stored copies as unique keys");
    }
}
