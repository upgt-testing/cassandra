package org.apache.cassandra.restart;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restarttest.core.RestartMode;

import static org.junit.Assert.*;

/**
 * Integration tests for CassandraClusterAdapter.
 *
 * <p>These tests verify that the restart adapter correctly:</p>
 * <ul>
 *   <li>Translates 0-based to 1-based indices</li>
 *   <li>Restarts nodes in GRACEFUL, CRASH, and DELAYED_CRASH modes</li>
 *   <li>Handles rolling restarts</li>
 * </ul>
 */
public class CassandraClusterAdapterTest {

    private static Cluster cluster;
    private static CassandraClusterAdapter adapter;

    @BeforeClass
    public static void setUp() throws Exception {
        // Create a 3-node Cassandra cluster
        cluster = Cluster.build(3)
            .withDataDirCount(1)
            .start();

        adapter = new CassandraClusterAdapter();

        // Create test keyspace and table
        cluster.schemaChange("CREATE KEYSPACE IF NOT EXISTS test WITH replication = " +
            "{'class': 'SimpleStrategy', 'replication_factor': 2}");
        cluster.schemaChange("CREATE TABLE IF NOT EXISTS test.data (id int PRIMARY KEY, value text)");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (cluster != null) {
            cluster.close();
        }
    }

    /**
     * Test that index translation works correctly (0-based to 1-based).
     */
    @Test
    public void testIndexMapping() throws Exception {
        // Verify adapter type matches
        assertEquals("Adapter should handle Cluster type",
            Cluster.class, adapter.getClusterType());

        // Test index translation: framework index 0 should map to Cassandra node 1
        // Node 1 should be running after test setup
        assertFalse("Node 1 should be running", cluster.get(1).isShutdown());

        // Restart with framework index 0 (should restart node 1)
        adapter.restartNode(cluster, "node", 0, RestartMode.GRACEFUL);

        // Verify node 1 is running again
        assertFalse("Node 1 should be running after restart", cluster.get(1).isShutdown());
    }

    /**
     * Test graceful restart of a single node with data preservation.
     */
    @Test
    public void testGracefulRestart() throws Exception {
        // Insert test data
        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (1, 'test1')",
            ConsistencyLevel.ALL);

        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (2, 'test2')",
            ConsistencyLevel.ALL);

        // Restart node at framework index 1 (Cassandra node 2) gracefully
        adapter.restartNode(cluster, "node", 1, RestartMode.GRACEFUL);

        // Wait for cluster to be active
        adapter.waitActive(cluster);

        // Verify data is still accessible
        Object[][] result = cluster.coordinator(1).execute(
            "SELECT * FROM test.data WHERE id = 1",
            ConsistencyLevel.QUORUM);

        assertEquals("Data should be preserved after restart", 1, result.length);
        assertEquals("Value should be correct", "test1", result[0][1]);
    }

    /**
     * Test crash restart (simulates kill -9).
     */
    @Test
    public void testCrashRestart() throws Exception {
        // Insert test data
        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (10, 'crash_test')",
            ConsistencyLevel.ALL);

        // Restart node at framework index 2 (Cassandra node 3) with crash mode
        adapter.restartNode(cluster, "node", 2, RestartMode.CRASH);

        // Wait for cluster to be active
        adapter.waitActive(cluster);

        // Verify data is still accessible
        Object[][] result = cluster.coordinator(1).execute(
            "SELECT * FROM test.data WHERE id = 10",
            ConsistencyLevel.QUORUM);

        assertEquals("Data should be preserved after crash restart", 1, result.length);
    }

    /**
     * Test delayed crash restart.
     */
    @Test
    public void testDelayedCrashRestart() throws Exception {
        // Insert test data
        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (20, 'delayed_crash_test')",
            ConsistencyLevel.ALL);

        // Restart node with delayed crash mode
        adapter.restartNode(cluster, "node", 0, RestartMode.DELAYED_CRASH);

        // Wait for cluster to be active
        adapter.waitActive(cluster);

        // Verify data is still accessible
        Object[][] result = cluster.coordinator(1).execute(
            "SELECT * FROM test.data WHERE id = 20",
            ConsistencyLevel.QUORUM);

        assertEquals("Data should be preserved after delayed crash restart", 1, result.length);
    }

    /**
     * Test rolling restart of all nodes.
     */
    @Test
    public void testRollingRestart() throws Exception {
        // Insert initial data
        for (int i = 100; i < 110; i++) {
            cluster.coordinator(1).execute(
                "INSERT INTO test.data (id, value) VALUES (?, ?)",
                ConsistencyLevel.QUORUM, i, "rolling_" + i);
        }

        // Perform rolling restart (one node at a time)
        for (int nodeIdx = 0; nodeIdx < 3; nodeIdx++) {
            // Restart node
            adapter.restartNode(cluster, "node", nodeIdx, RestartMode.GRACEFUL);
            adapter.waitActive(cluster);

            // Insert more data between restarts
            int writeStart = 200 + (nodeIdx * 10);
            for (int i = writeStart; i < writeStart + 5; i++) {
                cluster.coordinator(1).execute(
                    "INSERT INTO test.data (id, value) VALUES (?, ?)",
                    ConsistencyLevel.QUORUM, i, "write_" + i);
            }
        }

        // Verify all data is still accessible
        Object[][] count = cluster.coordinator(1).execute(
            "SELECT COUNT(*) FROM test.data",
            ConsistencyLevel.QUORUM);

        assertTrue("Should have at least 25 rows (10 initial + 15 during rolling restart)",
            ((Long) count[0][0]) >= 25);
    }

    /**
     * Test restart all nodes.
     */
    @Test
    public void testRestartAllNodes() throws Exception {
        // Insert test data
        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (300, 'restart_all_test')",
            ConsistencyLevel.ALL);

        // Restart all nodes
        adapter.restartAllNodes(cluster, "node", RestartMode.GRACEFUL);

        // Wait for cluster to be active
        adapter.waitActive(cluster);

        // Verify data is still accessible
        Object[][] result = cluster.coordinator(1).execute(
            "SELECT * FROM test.data WHERE id = 300",
            ConsistencyLevel.QUORUM);

        assertEquals("Data should be preserved after restarting all nodes", 1, result.length);
    }

    /**
     * Test node count retrieval.
     */
    @Test
    public void testGetNodeCount() throws Exception {
        int nodeCount = adapter.getNodeCount(cluster, "node");
        assertEquals("Should have 3 nodes", 3, nodeCount);

        // Test with different role names (all should return same count)
        assertEquals("Should have 3 nodes for 'peer' role",
            3, adapter.getNodeCount(cluster, "peer"));
        assertEquals("Should have 3 nodes for 'all' role",
            3, adapter.getNodeCount(cluster, "all"));
    }

    /**
     * Test invalid node index throws exception.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNodeIndex() throws Exception {
        // Try to restart node at invalid framework index 10 (would map to Cassandra index 11)
        adapter.restartNode(cluster, "node", 10, RestartMode.GRACEFUL);
    }

    /**
     * Test waitActive functionality.
     */
    @Test
    public void testWaitActive() throws Exception {
        // Restart a node
        adapter.restartNode(cluster, "node", 0, RestartMode.GRACEFUL);

        // Wait for active should succeed
        adapter.waitActive(cluster);

        // All nodes should be running and in the ring
        for (int i = 1; i <= 3; i++) {
            assertFalse("Node " + i + " should be running", cluster.get(i).isShutdown());
        }
    }
}
