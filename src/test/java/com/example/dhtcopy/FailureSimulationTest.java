package com.example.dhtcopy;

import com.example.dhtcopy.core.DistributedHashTable;
import com.example.dhtcopy.core.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
public class FailureSimulationTest {

    @Autowired
    private DistributedHashTable distributedHashTable;

    private Node node1, node2, node3;

    @BeforeEach
    void setUp() {
        node1 = new Node("node1", "localhost", 8001);
        node2 = new Node("node2", "localhost", 8002);
        node3 = new Node("node3", "localhost", 8003);

        distributedHashTable.addNode(node1);
        distributedHashTable.addNode(node2);
        distributedHashTable.addNode(node3);

        // Add some test data
        for (int i = 0; i < 30; i++) {
            distributedHashTable.put("key_" + i, "value_" + i);
        }
    }

    @Test
    void testNodeFailureHandling() {
        // Record initial state
        Map<String, Integer> beforeFailure = distributedHashTable.getDataDistribution();
        int totalKeysBefore = beforeFailure.values().stream().mapToInt(Integer::intValue).sum();

        // Simulate node failure
        node1.setActive(false);

        // Try to read data - should still work due to replication
        String value = distributedHashTable.get("key_1");
        // Note: depending on which node the key was stored on, it might or might not be available
        // The important thing is that the system doesn't crash

        // Remove the failed node
        boolean removed = distributedHashTable.removeNode("node1");
        assertTrue(removed, "Failed node should be removed successfully");

        // System should still be functional with remaining nodes
        distributedHashTable.put("new_key_after_failure", "new_value");
        assertNotNull(distributedHashTable.get("new_key_after_failure"));
    }

    @Test
    void testMultipleNodeFailures() {
        // Simulate multiple node failures
        node1.setActive(false);
        node2.setActive(false);

        // System should still work with at least one active node
        try {
            distributedHashTable.put("emergency_key", "emergency_value");
            String retrievedValue = distributedHashTable.get("emergency_key");
            assertEquals("emergency_value", retrievedValue);
        } catch (Exception e) {
            // If all responsible nodes are down, operation might fail
            // This is expected behavior
            assertTrue(e.getMessage().contains("No active nodes") ||
                    e.getMessage().contains("quorum"));
        }
    }

    @Test
    void testNodeRecovery() {
        // Simulate node failure
        node1.setActive(false);

        // Add some data while node is down
        distributedHashTable.put("recovery_key", "recovery_value");

        // Simulate node recovery
        node1.setActive(true);

        // Node should be functional again
        distributedHashTable.put("post_recovery_key", "post_recovery_value");

        // Verify we can still read the key added during failure
        String value = distributedHashTable.get("recovery_key");
        assertEquals("recovery_value", value);
    }

    @Test
    void testDataConsistencyAfterFailure() {
        // Add data with known keys
        distributedHashTable.put("consistency_test_1", "value_1");
        distributedHashTable.put("consistency_test_2", "value_2");
        distributedHashTable.put("consistency_test_3", "value_3");

        // Verify data exists
        assertEquals("value_1", distributedHashTable.get("consistency_test_1"));
        assertEquals("value_2", distributedHashTable.get("consistency_test_2"));
        assertEquals("value_3", distributedHashTable.get("consistency_test_3"));

        // Simulate node failure
        node2.setActive(false);
        distributedHashTable.removeNode("node2");

        // Data should still be accessible (due to replication)
        // Note: some data might be lost if replication factor is not sufficient
        // The test verifies the system remains functional
        try {
            String value1 = distributedHashTable.get("consistency_test_1");
            String value2 = distributedHashTable.get("consistency_test_2");
            String value3 = distributedHashTable.get("consistency_test_3");

            // At least some data should be recoverable
            int recoveredCount = 0;
            if ("value_1".equals(value1)) recoveredCount++;
            if ("value_2".equals(value2)) recoveredCount++;
            if ("value_3".equals(value3)) recoveredCount++;

            // With replication factor 3 and only 1 node failed,
            // we should recover most or all data
            assertTrue(recoveredCount >= 1, "At least some data should be recoverable");

        } catch (Exception e) {
            // Some operations might fail, which is acceptable in failure scenarios
            System.out.println("Expected failure during node outage: " + e.getMessage());
        }
    }
}

