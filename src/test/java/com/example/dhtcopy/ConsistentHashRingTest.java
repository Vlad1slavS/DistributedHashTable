package com.example.dhtcopy;


import com.example.dhtcopy.core.ConsistentHashRing;
import com.example.dhtcopy.core.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(locations = "classpath:application-test.yml")
public class ConsistentHashRingTest {

    private ConsistentHashRing hashRing;

    @BeforeEach
    void setUp() {
        hashRing = new ConsistentHashRing(100); // Using fewer virtual nodes for testing
    }

    @Test
    void debugKeyPlacement() {
        // Настройка нод
        Node node1 = new Node("node1", "localhost", 8001);
        Node node2 = new Node("node2", "localhost", 8002);
        Node node3 = new Node("node3", "localhost", 8003);
        Node node4 = new Node("1", "localhost", 8004);

        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        hashRing.addNode(node4);

        // Отладка размещения ключа "12"
        hashRing.debugKeyPlacement("Hello!", 4);

        // Проверим распределение нескольких ключей
        for (int i = 0; i < 20; i++) {
            String key = String.valueOf(i);
            List<Node> nodes = hashRing.getNodes(key, 4);
            System.out.println("Key '" + key + "' -> " +
                    nodes.stream().map(Node::getId).toList());
        }
    }


    @Test
    void testAddNode() {
        Node node = new Node("node1", "localhost", 8001);
        hashRing.addNode(node);

        assertEquals(1, hashRing.getUniqueNodesCount());
        assertFalse(hashRing.isEmpty());
    }

    @Test
    void testRemoveNode() {
        Node node = new Node("node1", "localhost", 8001);
        hashRing.addNode(node);

        assertTrue(hashRing.removeNode("node1"));
        assertEquals(0, hashRing.getUniqueNodesCount());
        assertFalse(hashRing.removeNode("nonexistent"));
    }

    @Test
    void testGetNode() {
        Node node1 = new Node("node1", "localhost", 8001);
        Node node2 = new Node("node2", "localhost", 8002);

        hashRing.addNode(node1);
        hashRing.addNode(node2);

        Node retrievedNode = hashRing.getNode("test-key");
        assertNotNull(retrievedNode);
        assertTrue(retrievedNode.equals(node1) || retrievedNode.equals(node2));
    }

    @Test
    void testGetNodesForReplication() {
        Node node1 = new Node("node1", "localhost", 8001);
        Node node2 = new Node("node2", "localhost", 8002);
        Node node3 = new Node("node3", "localhost", 8003);

        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);

        List<Node> nodes = hashRing.getNodes("test-key", 2);
        assertEquals(2, nodes.size());

        // Ensure we don't get duplicate nodes
        assertEquals(2, nodes.stream().map(Node::getId).distinct().count());
    }

    @Test
    void testDataDistribution() {
        Node node1 = new Node("node1", "localhost", 8001);
        Node node2 = new Node("node2", "localhost", 8002);

        // Add some test data
        node1.put("key1", "value1");
        node1.put("key2", "value2");
        node2.put("key3", "value3");

        hashRing.addNode(node1);
        hashRing.addNode(node2);

        Map<String, Integer> distribution = hashRing.getDataDistribution();
        assertEquals(2, distribution.get("node1"));
        assertEquals(1, distribution.get("node2"));
    }

    @Test
    void testConsistentHashing() {
        Node node1 = new Node("node1", "localhost", 8001);
        Node node2 = new Node("node2", "localhost", 8002);

        hashRing.addNode(node1);
        hashRing.addNode(node2);

        // Test that the same key always maps to the same node
        Node firstResult = hashRing.getNode("consistent-key");
        Node secondResult = hashRing.getNode("consistent-key");

        assertEquals(firstResult, secondResult);
    }
}

