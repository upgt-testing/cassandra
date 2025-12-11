package org.apache.cassandra.restart;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.shared.ClusterUtils;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Captures and verifies Cassandra cluster state across restarts.
 *
 * <p>This implementation captures:</p>
 * <ul>
 *   <li>Ring topology (tokens, nodes, datacenter/rack assignments)</li>
 *   <li>Schema versions from gossip and keyspace definitions</li>
 *   <li>Gossip state (node UP/DOWN status)</li>
 *   <li>Data samples (lightweight row count verification)</li>
 * </ul>
 *
 * <p>Invariants verified across restarts:</p>
 * <ul>
 *   <li>Node count unchanged</li>
 *   <li>Token assignments preserved</li>
 *   <li>Schema versions consistent across cluster</li>
 *   <li>Nodes that were NORMAL remain NORMAL</li>
 *   <li>Sample data counts match</li>
 * </ul>
 */
public class CassandraStateCapture extends AbstractStateCapture<Cluster> {

    @Override
    public ClusterState captureState(Cluster cluster) throws Exception {
        Map<String, Object> state = new HashMap<>();

        // Find a running node to query from
        IInvokableInstance referenceNode = findRunningNode(cluster);
        if (referenceNode != null) {
            captureRingTopology(state, referenceNode);
            captureSchemaState(state, cluster);
            captureGossipState(state, referenceNode);
            captureDataSamples(state, cluster);
        } else {
            // No running nodes - capture minimal state
            state.put("no_running_nodes", true);
            state.put("cluster_size", cluster.size());
        }

        return new DefaultClusterState(state);
    }

    @Override
    protected void verifyCustomInvariants(Cluster cluster, ClusterState before, ClusterState after) throws Exception {
        // 1. Verify ring topology is consistent
        verifyRingTopology(before, after);

        // 2. Verify schema versions match
        verifySchemaConsistency(before, after);

        // 3. Verify all nodes that were UP/NORMAL before are UP/NORMAL after
        verifyNodeStatus(before, after);

        // 4. Verify data integrity (sampled queries return same results)
        verifyDataIntegrity(before, after);
    }

    // --- Private helper methods for state capture ---

    /**
     * Find any running node in the cluster.
     *
     * @param cluster the cluster
     * @return a running instance, or null if none found
     */
    private IInvokableInstance findRunningNode(Cluster cluster) {
        for (int i = 1; i <= cluster.size(); i++) {
            IInvokableInstance node = cluster.get(i);
            if (!node.isShutdown()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Capture ring topology information from the cluster.
     *
     * @param state the state map to populate
     * @param node the reference node to query
     */
    private void captureRingTopology(Map<String, Object> state, IInvokableInstance node) {
        try {
            // Get ring information
            List<ClusterUtils.RingInstanceDetails> ring = ClusterUtils.ring(node);

            Map<String, Object> topology = new HashMap<>();
            topology.put("nodeCount", ring.size());
            topology.put("nodes", ring.stream()
                .map(r -> {
                    Map<String, String> nodeInfo = new HashMap<>();
                    nodeInfo.put("address", r.getAddress());
                    nodeInfo.put("rack", r.getRack());
                    nodeInfo.put("status", r.getStatus());
                    nodeInfo.put("state", r.getState());
                    nodeInfo.put("token", r.getToken());
                    return nodeInfo;
                })
                .collect(Collectors.toList())
            );

            state.put("ringTopology", topology);
        } catch (Exception e) {
            // If we can't capture ring, record the error
            state.put("ringTopology_error", e.getMessage());
        }
    }

    /**
     * Capture schema state from the cluster.
     *
     * @param state the state map to populate
     * @param cluster the cluster
     */
    private void captureSchemaState(Map<String, Object> state, Cluster cluster) {
        try {
            // Capture schema version from all running nodes
            Map<String, String> schemaVersions = new HashMap<>();

            for (int i = 1; i <= cluster.size(); i++) {
                IInvokableInstance node = cluster.get(i);
                if (!node.isShutdown()) {
                    Map<String, Map<String, String>> gossip = ClusterUtils.gossipInfo(node);
                    // Extract schema version from gossip
                    gossip.forEach((addr, gossipState) -> {
                        String schema = gossipState.get("SCHEMA");
                        if (schema != null) {
                            schemaVersions.put(addr, schema);
                        }
                    });
                    break;  // One node's view is sufficient
                }
            }

            state.put("schemaVersions", schemaVersions);

            // Capture keyspace and table definitions
            IInvokableInstance node = findRunningNode(cluster);
            if (node != null) {
                try {
                    Object[][] keyspaces = node.executeInternal(
                        "SELECT keyspace_name, replication FROM system_schema.keyspaces"
                    );
                    state.put("keyspaces", Arrays.asList(keyspaces));
                } catch (Exception e) {
                    // Keyspace query might fail, that's okay
                    state.put("keyspaces_error", e.getMessage());
                }
            }
        } catch (Exception e) {
            state.put("schemaState_error", e.getMessage());
        }
    }

    /**
     * Capture gossip state from the cluster.
     *
     * @param state the state map to populate
     * @param node the reference node to query
     */
    private void captureGossipState(Map<String, Object> state, IInvokableInstance node) {
        try {
            Map<String, Map<String, String>> gossipInfo = ClusterUtils.gossipInfo(node);

            Map<String, String> nodeStates = new HashMap<>();
            gossipInfo.forEach((addr, gossipState) -> {
                String status = gossipState.get("STATUS_WITH_PORT");
                if (status == null) {
                    status = gossipState.get("STATUS");
                }
                if (status != null) {
                    nodeStates.put(addr, status);
                }
            });

            state.put("gossipStates", nodeStates);
        } catch (Exception e) {
            state.put("gossipStates_error", e.getMessage());
        }
    }

    /**
     * Capture sample data from test tables for lightweight verification.
     *
     * @param state the state map to populate
     * @param cluster the cluster
     */
    private void captureDataSamples(Map<String, Object> state, Cluster cluster) {
        IInvokableInstance node = findRunningNode(cluster);
        if (node == null) {
            return;
        }

        // Try to capture some sample data
        // Note: This is best-effort - test keyspaces might not exist
        try {
            // Try common test keyspace patterns
            String[] testKeyspaces = {"test", "distributed_test_keyspace", "ks"};

            for (String ks : testKeyspaces) {
                try {
                    Object[][] tables = node.executeInternal(
                        "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?", ks
                    );

                    if (tables != null && tables.length > 0) {
                        // Found a test keyspace with tables
                        state.put("sample_keyspace", ks);
                        state.put("sample_table_count", tables.length);

                        // Try to get row count from first table
                        String tableName = (String) tables[0][0];
                        try {
                            Object[][] count = node.executeInternal(
                                String.format("SELECT COUNT(*) FROM %s.%s", ks, tableName)
                            );
                            state.put("sample_row_count", count[0][0]);
                        } catch (Exception e) {
                            // Count query might fail, that's okay
                        }
                        break;
                    }
                } catch (Exception e) {
                    // Keyspace doesn't exist or query failed, try next
                }
            }

            // If no test keyspace found, just mark as N/A
            if (!state.containsKey("sample_keyspace")) {
                state.put("sample_data", "N/A");
            }
        } catch (Exception e) {
            state.put("sample_data_error", e.getMessage());
        }
    }

    // --- Private helper methods for verification ---

    /**
     * Verify ring topology remained consistent across restart.
     */
    private void verifyRingTopology(ClusterState before, ClusterState after) throws StateVerificationException {
        Map<String, Object> topoBefore = (Map<String, Object>) before.getStateMap().get("ringTopology");
        Map<String, Object> topoAfter = (Map<String, Object>) after.getStateMap().get("ringTopology");

        if (topoBefore == null || topoAfter == null) {
            // If we couldn't capture topology before or after, skip verification
            return;
        }

        // Verify node count didn't change
        if (!Objects.equals(topoBefore.get("nodeCount"), topoAfter.get("nodeCount"))) {
            throw new StateVerificationException("Node count changed: " +
                topoBefore.get("nodeCount") + " -> " + topoAfter.get("nodeCount"));
        }

        // Verify tokens didn't change
        List<Map<String, String>> nodesBefore = (List<Map<String, String>>) topoBefore.get("nodes");
        List<Map<String, String>> nodesAfter = (List<Map<String, String>>) topoAfter.get("nodes");

        if (nodesBefore != null && nodesAfter != null) {
            Set<String> tokensBefore = nodesBefore.stream()
                .map(n -> n.get("token"))
                .collect(Collectors.toSet());
            Set<String> tokensAfter = nodesAfter.stream()
                .map(n -> n.get("token"))
                .collect(Collectors.toSet());

            if (!tokensBefore.equals(tokensAfter)) {
                throw new StateVerificationException("Token assignment changed after restart");
            }
        }
    }

    /**
     * Verify schema remained consistent across restart.
     */
    private void verifySchemaConsistency(ClusterState before, ClusterState after) throws StateVerificationException {
        Map<String, String> versionsBefore = (Map<String, String>) before.getStateMap().get("schemaVersions");
        Map<String, String> versionsAfter = (Map<String, String>) after.getStateMap().get("schemaVersions");

        if (versionsBefore == null || versionsAfter == null) {
            // If we couldn't capture schema before or after, skip verification
            return;
        }

        // All nodes should have same schema version after restart
        Set<String> uniqueVersionsAfter = new HashSet<>(versionsAfter.values());
        if (uniqueVersionsAfter.size() > 1) {
            throw new StateVerificationException(
                "Schema versions not consistent after restart: " + uniqueVersionsAfter);
        }
    }

    /**
     * Verify node status remained consistent across restart.
     */
    private void verifyNodeStatus(ClusterState before, ClusterState after) throws StateVerificationException {
        Map<String, String> statesBefore = (Map<String, String>) before.getStateMap().get("gossipStates");
        Map<String, String> statesAfter = (Map<String, String>) after.getStateMap().get("gossipStates");

        if (statesBefore == null || statesAfter == null) {
            // If we couldn't capture gossip before or after, skip verification
            return;
        }

        // All nodes that were NORMAL before should be NORMAL after
        for (Map.Entry<String, String> entry : statesBefore.entrySet()) {
            String addr = entry.getKey();
            String status = entry.getValue();
            if (status != null && status.contains("NORMAL")) {
                String afterStatus = statesAfter.get(addr);
                if (afterStatus == null || !afterStatus.contains("NORMAL")) {
                    throw new StateVerificationException("Node " + addr + " was NORMAL before but " +
                        afterStatus + " after restart");
                }
            }
        }
    }

    /**
     * Verify data integrity across restart (sample data).
     */
    private void verifyDataIntegrity(ClusterState before, ClusterState after) throws StateVerificationException {
        // Verify sample data counts match (if available)
        Object beforeCount = before.getStateMap().get("sample_row_count");
        Object afterCount = after.getStateMap().get("sample_row_count");

        if (beforeCount != null && afterCount != null) {
            if (!Objects.equals(beforeCount, afterCount)) {
                throw new StateVerificationException("Sample data count mismatch: " +
                    beforeCount + " -> " + afterCount);
            }
        }
    }
}
