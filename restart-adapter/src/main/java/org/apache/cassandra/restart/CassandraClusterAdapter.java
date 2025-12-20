package org.apache.cassandra.restart;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.NodeToolResult;
import org.apache.cassandra.distributed.shared.ClusterUtils;
import org.apache.cassandra.utils.FBUtilities;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Adapter for Cassandra's distributed test Cluster to work with restart testing framework.
 *
 * <p><b>CRITICAL DESIGN NOTE</b>: Cassandra Cluster uses 1-based indexing (cluster.get(1) is first node).
 * The restart framework uses 0-based indexing, so this adapter performs index translation.</p>
 *
 * <p>Node Role Handling: Cassandra is a peer-to-peer system with no master/worker distinction.
 * This adapter accepts "node", "peer", or "all" as roles, treating all nodes identically.</p>
 */
public class CassandraClusterAdapter implements ClusterAdapter<Cluster> {

    private static final Logger logger = LoggerFactory.getLogger(CassandraClusterAdapter.class);

    public CassandraClusterAdapter() {
    }

    @Override
    public Class<Cluster> getClusterType() {
        return Cluster.class;
    }

    @Override
    public void restartNode(Cluster cluster, String nodeRole, int nodeIndex, RestartMode mode) throws Exception {
        // Index translation: framework uses 0-based, Cassandra uses 1-based
        int cassandraIndex = nodeIndex + 1;

        validateNodeIndex(cluster, cassandraIndex);
        IInvokableInstance instance = cluster.get(cassandraIndex);

        switch (mode) {
            case GRACEFUL:
                performGracefulRestart(cluster, instance);
                break;
            case CRASH:
                performCrashRestart(cluster, instance);
                break;
            case DELAYED_CRASH:
                performDelayedCrashRestart(cluster, instance);
                break;
            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }
    }

    @Override
    public void restartAllNodes(Cluster cluster, String nodeRole, RestartMode mode) throws Exception {
        // Cassandra doesn't have role distinction (all nodes are peers)
        // Restart all nodes sequentially
        for (int i = 0; i < cluster.size(); i++) {
            restartNode(cluster, nodeRole, i, mode);
        }
    }

    @Override
    public void waitActive(Cluster cluster) throws Exception {
        // Wait for all running nodes to see the ring as healthy
        for (int i = 1; i <= cluster.size(); i++) {
            IInvokableInstance node = cluster.get(i);
            if (!node.isShutdown()) {
                // This node should see all other nodes as Up and Normal
                ClusterUtils.awaitRingHealthy(node);
                break;  // Once one node sees ring as healthy, we're good
            }
        }

        // Wait for schema agreement across all running nodes
        for (int i = 1; i <= cluster.size(); i++) {
            if (!cluster.get(i).isShutdown()) {
                ClusterUtils.awaitGossipSchemaMatch(cluster.get(i));
                break;  // Schema check from one node is sufficient
            }
        }
    }

    @Override
    public int getNodeCount(Cluster cluster, String nodeRole) throws Exception {
        // Cassandra nodes don't have roles - all are peers
        return cluster.size();
    }

    @Override
    public StateCapture<Cluster> getStateCapture() {
        // No-op state capture - no verification needed for Cassandra restart adapter
        return new StateCapture<Cluster>() {
            @Override
            public ClusterState captureState(Cluster cluster) {
                return new DefaultClusterState(Collections.emptyMap());
            }

            @Override
            public void verifyState(Cluster cluster, ClusterState before, ClusterState after) {
                // No verification - intentionally empty
            }
        };
    }

    @Override
    public HealthCheck<Cluster> getHealthCheck() {
        // No-op health check - health verification is handled by waitActive()
        return new HealthCheck<Cluster>() {
            @Override
            public String getName() {
                return "cassandra-noop";
            }

            @Override
            public HealthCheckResult checkHealth(Cluster cluster) {
                // Always pass - actual health checking done in waitActive()
                return new HealthCheckResult(true, getName());
            }
        };
    }

    // --- Private helper methods ---

    /**
     * Perform graceful restart: drain node, then clean shutdown followed by startup.
     *
     * <p>The drain operation ensures:
     * <ul>
     *   <li>All memtables are flushed to SSTables on disk</li>
     *   <li>The cluster is properly notified via gossip (DRAINING/DRAINED state)</li>
     *   <li>Commit log segments are recycled to minimize replay time on restart</li>
     *   <li>No new writes are accepted during shutdown</li>
     * </ul>
     *
     * <p>This mimics production-like graceful shutdown behavior (e.g., systemctl stop cassandra).
     */
    private void performGracefulRestart(Cluster cluster, IInvokableInstance instance) throws Exception {
        int nodeNum = instance.config().num();

        try {
            // Step 1: Drain the node - this is the key to a truly graceful shutdown
            // Drain flushes all memtables to disk, announces leaving to cluster via gossip,
            // and recycles commit log segments to minimize recovery time on restart
            logger.info("Draining node {} before graceful shutdown", nodeNum);
            NodeToolResult drainResult = instance.nodetoolResult("drain");
            if (drainResult.getRc() != 0) {
                logger.warn("Drain command returned non-zero exit code {} for node {}: {}",
                        drainResult.getRc(), nodeNum, drainResult.getStdout());
                // Continue with shutdown anyway - drain may fail if node is already draining
            }

            // Step 2: Shutdown the node (now with all data safely on disk)
            logger.info("Shutting down node {} after drain", nodeNum);
            Future<Void> shutdownFuture = instance.shutdown(true);
            FBUtilities.waitOnFuture(shutdownFuture);

            // Brief pause to allow cluster to detect node is down
            Thread.sleep(1000);

            // Step 3: Restart the node
            logger.info("Starting up node {}", nodeNum);
            instance.startup();

            // Step 4: Wait for node to rejoin ring
            IInvokableInstance referenceNode = findRunningNode(cluster, nodeNum);
            if (referenceNode != null) {
                ClusterUtils.awaitRingJoin(referenceNode, instance);
            }
            logger.info("Node {} graceful restart completed", nodeNum);

        } catch (Exception e) {
            throw new RuntimeException("Failed to gracefully restart node " + nodeNum, e);
        }
    }

    /**
     * Perform crash restart: abrupt stop (simulates kill -9) followed by startup
     */
    private void performCrashRestart(Cluster cluster, IInvokableInstance instance) throws Exception {
        int nodeNum = instance.config().num();

        try {
            // Simulate crash using abrupt stop (blocks all messages, then shuts down)
            ClusterUtils.stopAbrupt(cluster, instance);

            // Pause to simulate crash detection time
            Thread.sleep(2000);

            // Restart the node
            instance.startup();

            // Wait for node to rejoin ring
            IInvokableInstance referenceNode = findRunningNode(cluster, nodeNum);
            if (referenceNode != null) {
                ClusterUtils.awaitRingJoin(referenceNode, instance);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to crash restart node " + nodeNum, e);
        }
    }

    /**
     * Perform delayed crash restart: start graceful shutdown, interrupt it, then restart
     */
    private void performDelayedCrashRestart(Cluster cluster, IInvokableInstance instance) throws Exception {
        int nodeNum = instance.config().num();

        try {
            // Start shutdown process gracefully
            Future<Void> shutdownFuture = instance.shutdown(true);

            // Wait a bit to let some cleanup happen
            Thread.sleep(500);

            // Force immediate shutdown (simulates crash during shutdown)
            ClusterUtils.stopAbrupt(cluster, instance);

            // Wait for future to complete (it will likely fail or already be done)
            try {
                shutdownFuture.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Expected - we interrupted the shutdown
            }

            // Pause before restart
            Thread.sleep(2000);

            // Restart the node
            instance.startup();

            // Wait for node to rejoin ring
            IInvokableInstance referenceNode = findRunningNode(cluster, nodeNum);
            if (referenceNode != null) {
                ClusterUtils.awaitRingJoin(referenceNode, instance);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to delayed-crash restart node " + nodeNum, e);
        }
    }

    /**
     * Validate that the Cassandra node index is within valid range.
     *
     * @param cluster the cluster
     * @param cassandraIndex the 1-based Cassandra index
     * @throws IllegalArgumentException if index is out of range
     */
    private void validateNodeIndex(Cluster cluster, int cassandraIndex) {
        if (cassandraIndex < 1 || cassandraIndex > cluster.size()) {
            throw new IllegalArgumentException(
                String.format("Invalid node index: %d (cluster has %d nodes, indexed 1-%d)",
                    cassandraIndex, cluster.size(), cluster.size())
            );
        }
    }

    /**
     * Find a running node (not shutdown) in the cluster, excluding the specified node number.
     *
     * @param cluster the cluster
     * @param excludeNodeNum node number to exclude (or -1 to not exclude any)
     * @return a running instance, or null if none found
     */
    private IInvokableInstance findRunningNode(Cluster cluster, int excludeNodeNum) {
        for (int i = 1; i <= cluster.size(); i++) {
            if (i != excludeNodeNum) {
                IInvokableInstance node = cluster.get(i);
                if (!node.isShutdown()) {
                    return node;
                }
            }
        }
        return null;
    }
}
