# Cassandra Restart Testing Adapter

This adapter integrates Apache Cassandra's distributed test cluster framework (jvm-dtest) with the Restart Testing Framework, enabling systematic restart testing for Cassandra distributed tests.

## Overview

The Cassandra Restart Testing Adapter allows you to inject node restarts at specific points during distributed tests to uncover bugs related to:

- Node failures and recovery
- State persistence across restarts
- Crash recovery scenarios
- Distributed coordination during node transitions
- Data consistency after restarts

## Quick Start

### 1. Build the Adapter

```bash
cd /home/shuai/xlab/restart_testing/cassandra/restart-adapter
mvn clean install
```

### 2. Add Dependency to Your Test Project

Add to your test `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.cassandra</groupId>
    <artifactId>cassandra-restart-adapter</artifactId>
    <version>5.0.6-SNAPSHOT</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.restarttest</groupId>
    <artifactId>restart-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 3. Use in Tests

```java
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;

public class MyDistributedTest {

    @Test
    public void testWithRestart() throws Exception {
        try (Cluster cluster = Cluster.build(3).start()) {

            // Create test data
            cluster.schemaChange("CREATE KEYSPACE test WITH replication = " +
                "{'class': 'SimpleStrategy', 'replication_factor': 2}");
            cluster.schemaChange("CREATE TABLE test.data (id int PRIMARY KEY, value text)");

            cluster.coordinator(1).execute(
                "INSERT INTO test.data (id, value) VALUES (1, 'test')",
                ConsistencyLevel.QUORUM);

            // Add restart point (NO-OP unless system properties match)
            RestartFramework.at("after_write")
                .on(cluster)                    // Automatically discovers CassandraClusterAdapter
                .restart("node")                // Use "node" or "peer"
                .withIndex(0)                   // First node (framework uses 0-based)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            // Verify data after restart
            Object[][] result = cluster.coordinator(1).execute(
                "SELECT * FROM test.data WHERE id = 1",
                ConsistencyLevel.QUORUM);

            assertEquals(1, result.length);
            assertEquals("test", result[0][1]);
        }
    }
}
```

## Usage

### Restart Modes

The adapter supports three restart modes:

#### GRACEFUL
Clean shutdown followed by startup. Allows proper cleanup and state flushing.

```java
RestartFramework.at("checkpoint")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

#### CRASH
Simulates kill -9 (abrupt shutdown with message blocking). Tests crash recovery.

```java
RestartFramework.at("checkpoint")
    .on(cluster)
    .restart("node")
    .withIndex(1)
    .withMode(RestartMode.CRASH)
    .execute();
```

#### DELAYED_CRASH
Starts graceful shutdown, then interrupts it mid-way. Tests partial state propagation.

```java
RestartFramework.at("checkpoint")
    .on(cluster)
    .restart("node")
    .withIndex(2)
    .withMode(RestartMode.DELAYED_CRASH)
    .execute();
```

### Node Selection

#### Specific Node (0-based indexing)
```java
.withIndex(0)  // First node (maps to cluster.get(1))
.withIndex(1)  // Second node (maps to cluster.get(2))
.withIndex(2)  // Third node (maps to cluster.get(3))
```

#### Random Node
```java
.withIndex("random")  // Framework picks a random node
```

#### All Nodes
```java
.withIndex("all")  // Restart all nodes sequentially
```

### Node Roles

Since Cassandra is a peer-to-peer system, all nodes are treated identically. You can use:
- `"node"` (recommended)
- `"peer"`
- `"all"`

Example:
```java
RestartFramework.at("checkpoint")
    .on(cluster)
    .restart("node")    // or "peer" or "all"
    .withIndex(0)
    .execute();
```

### Activating Restart Points

Restart points are NO-OP by default. Activate them using system properties:

```bash
mvn test -Dtest=MyTest#testWithRestart \
  -Drestart.position=after_write \
  -Drestart.target=node \
  -Drestart.mode=CRASH \
  -Drestart.index=0
```

**System Properties:**
- `restart.position`: Must match the position name in `RestartFramework.at(...)`
- `restart.target`: Must match the node role in `.restart(...)`
- `restart.mode`: `GRACEFUL`, `CRASH`, or `DELAYED_CRASH`
- `restart.index`: Node index or `"all"` or `"random"` (optional, default: 0)

## Important Design Notes

### Index Translation

**CRITICAL**: The restart framework uses 0-based indexing, but Cassandra Cluster uses 1-based indexing.

The adapter automatically translates:
- Framework index `0` → Cassandra `cluster.get(1)` (first node)
- Framework index `1` → Cassandra `cluster.get(2)` (second node)
- Framework index `2` → Cassandra `cluster.get(3)` (third node)

### State Capture

The adapter automatically captures and verifies cluster state across restarts:

**Captured State:**
- Ring topology (tokens, nodes, datacenter/rack assignments)
- Schema versions from gossip
- Gossip state (node UP/DOWN status)
- Sample data counts (if test keyspace exists)

**Verified Invariants:**
- Node count unchanged
- Token assignments preserved
- Schema versions consistent across cluster
- Nodes that were NORMAL remain NORMAL
- Sample data counts match

### Health Verification

The adapter verifies cluster health after restart by:
1. Calling `ClusterUtils.awaitRingHealthy()` to ensure all nodes are Up and Normal
2. Calling `ClusterUtils.awaitGossipSchemaMatch()` to ensure schema consistency

The `getHealthCheck()` method returns a no-op implementation since health verification is handled by `waitActive()`.

## Advanced Examples

### Rolling Restart with Writes

```java
@Test
public void testRollingRestartWithWrites() throws Exception {
    try (Cluster cluster = Cluster.build(3).start()) {
        // Setup
        cluster.schemaChange("CREATE KEYSPACE test WITH replication = " +
            "{'class': 'SimpleStrategy', 'replication_factor': 3}");
        cluster.schemaChange("CREATE TABLE test.data (id int PRIMARY KEY, value int)");

        // Insert initial data
        for (int i = 0; i < 100; i++) {
            cluster.coordinator(1).execute(
                "INSERT INTO test.data (id, value) VALUES (?, ?)",
                ConsistencyLevel.QUORUM, i, i);
        }

        CassandraClusterAdapter adapter = new CassandraClusterAdapter();

        // Perform rolling restart with concurrent writes
        for (int nodeIdx = 0; nodeIdx < 3; nodeIdx++) {
            // Restart node
            RestartFramework.at("rolling_restart_" + nodeIdx)
                .on(cluster)
                .restart("node")
                .withIndex(nodeIdx)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            adapter.waitActive(cluster);

            // Continue writing
            int writeStart = 100 + (nodeIdx * 100);
            for (int i = writeStart; i < writeStart + 100; i++) {
                cluster.coordinator(1).execute(
                    "INSERT INTO test.data (id, value) VALUES (?, ?)",
                    ConsistencyLevel.QUORUM, i, i);
            }
        }

        // Verify all data is present
        Object[][] count = cluster.coordinator(1).execute(
            "SELECT COUNT(*) FROM test.data",
            ConsistencyLevel.ALL);
        assertEquals(400L, count[0][0]);
    }
}
```

### Multiple Restart Points

```java
@Test
public void testMultipleRestartPoints() throws Exception {
    try (Cluster cluster = Cluster.build(3).start()) {
        // Setup
        cluster.schemaChange("CREATE KEYSPACE test WITH replication = " +
            "{'class': 'SimpleStrategy', 'replication_factor': 2}");
        cluster.schemaChange("CREATE TABLE test.data (id int PRIMARY KEY, value text)");

        // Restart point 1: After schema creation
        RestartFramework.at("after_schema")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // Insert data
        cluster.coordinator(1).execute(
            "INSERT INTO test.data (id, value) VALUES (1, 'test')",
            ConsistencyLevel.QUORUM);

        // Restart point 2: After write
        RestartFramework.at("after_write")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.CRASH)
            .execute();

        // Verify data
        Object[][] result = cluster.coordinator(1).execute(
            "SELECT * FROM test.data WHERE id = 1",
            ConsistencyLevel.QUORUM);
        assertEquals(1, result.length);
    }
}
```

Activate specific restart points:
```bash
# Test restart point 1
mvn test -Dtest=MyTest -Drestart.position=after_schema -Drestart.target=node -Drestart.mode=GRACEFUL

# Test restart point 2
mvn test -Dtest=MyTest -Drestart.position=after_write -Drestart.target=node -Drestart.mode=CRASH
```

## Testing the Adapter

Run the adapter's own tests:

```bash
cd /home/shuai/xlab/restart_testing/cassandra/restart-adapter
mvn test
```

This will execute all integration tests including:
- Index mapping verification
- Graceful restart
- Crash restart
- Delayed crash restart
- State capture and verification
- Rolling restart
- Restart all nodes

## Troubleshooting

### Restart Point Never Executes

**Symptom**: Test runs normally even with system properties set.

**Solution**: Verify:
- `restart.position` matches the position name in `RestartFramework.at(...)`
- `restart.target` matches the node role in `.restart(...)`
- `restart.mode` is one of: `GRACEFUL`, `CRASH`, `DELAYED_CRASH`

### Index Out of Bounds

**Symptom**: `IllegalArgumentException: Invalid node index`

**Solution**: Remember that the framework uses 0-based indexing. For a 3-node cluster:
- Valid framework indices: 0, 1, 2
- These map to Cassandra nodes: 1, 2, 3

### State Verification Fails

**Symptom**: `StateVerificationException` after restart

**Solution**: Check if the state change is legitimate. You can disable state capture:
```java
RestartFramework.at("checkpoint")
    .on(cluster)
    .restart("node")
    .captureState(false)  // Disable state capture
    .execute();
```

### Cluster Not Healthy After Restart

**Symptom**: Test times out in `waitActive()`

**Solution**:
- Check Cassandra logs for startup errors
- Increase timeout if cluster is slow to start
- Verify cluster configuration is correct

## Architecture

### Components

1. **CassandraClusterAdapter** - Main adapter implementing `ClusterAdapter<Cluster>`
   - Handles index translation (0-based → 1-based)
   - Implements restart modes (GRACEFUL, CRASH, DELAYED_CRASH)
   - Provides `waitActive()` for health verification

2. **CassandraStateCapture** - State capture and verification
   - Captures ring topology, schema, gossip state, data samples
   - Verifies invariants across restarts
   - Extends `AbstractStateCapture<Cluster>`

3. **ServiceLoader Registration** - Auto-discovery
   - Registered in `META-INF/services/org.restarttest.core.ClusterAdapter`
   - Automatically discovered by RestartTestingFramework

### Dependencies

- **restart-core**: Core restart testing framework
- **Cassandra distributed test API**: Cluster, IInvokableInstance, ClusterUtils
- **JUnit 4**: For testing

## License

Apache License 2.0 (same as Apache Cassandra)

## Contributing

To contribute improvements to the adapter:
1. Add tests for new functionality
2. Ensure all existing tests pass
3. Update documentation
4. Submit changes with clear commit messages

## References

- [Restart Testing Framework Documentation](/home/shuai/xlab/restart_testing/RestartTestingFramework/docs/)
- [Cassandra Distributed Test Framework](../test/distributed/)
- [Adapter Guide](/home/shuai/xlab/restart_testing/RestartTestingFramework/docs/adapter-guide.md)
- [Usage Guide](/home/shuai/xlab/restart_testing/RestartTestingFramework/docs/usage-guide.md)
