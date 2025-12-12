# Prompt: Transform Cassandra Distributed Test with Restart Position Injection

## Objective

Transform an existing Cassandra distributed cluster test to inject restart positions for distributed system restart testing. The transformation will generate:
1. A new test file with `_RestartInjected` suffix
2. A restart configuration file for the Maven plugin

## Input

- **Test File Path**: Path to the original test file (e.g., `/path/to/SimpleReadWriteTest.java`)
- **Test Class**: Fully-qualified class name (e.g., `org.apache.cassandra.distributed.test.SimpleReadWriteTest`)

## Output

1. **Generated Test File**: `{OriginalFileName}_RestartInjected.java` at the same directory as the input file
2. **Restart Configuration**: `restart-config.json` in the `restarts-config/` directory under the same module directory as the test file, if not exist create it.

## Transformation Instructions

### Step 1: Analyze the Original Test

Read the input test file and identify:

1. **Cluster Setup**: Find the Cassandra cluster instance variable (e.g., `Cluster cluster`)
   - **IMPORTANT**: The cluster object may be inherited from a parent base test class rather than being directly initialized in the test class itself
   - Check the class hierarchy (look for `extends` clauses) and examine parent classes for cluster initialization
   - Common patterns:
     - Direct initialization: `private Cluster cluster;` in the test class
     - Inherited from parent: `extends TestBaseImpl` where parent has the cluster field
     - Static shared cluster: `protected static Cluster cluster;` in base class
2. **Test Methods**: Identify all `@Test` annotated methods
3. **Critical Operations**: Look for operations that involve state transitions, such as:
   - Schema operations: `executeInternal()` for CREATE TABLE, DROP TABLE, ALTER TABLE
   - Data operations: INSERT, UPDATE, DELETE, SELECT via `executeInternal()` or `coordinator()`
   - CAS operations: Lightweight transactions (LWT), Compare-and-Set operations
   - Paxos operations: Paxos repairs, ballot operations
   - Flush operations: `flush()`, `forceBlockingFlush()`
   - Compaction operations: `compact()`, `majorCompaction()`
   - Repair operations: `repair()`, incremental repair
   - Streaming operations: Bootstrap, decommission, node replacement
   - Hinted handoff operations: Hints delivery, hint truncation
   - Gossip operations: Ring state changes, node join/leave
   - Snapshot operations: `snapshot()`, `clearSnapshot()`
   - Read/Write consistency: QUORUM, ALL, ONE operations
   - Batch operations: Batch writes, batch CAS
   - Secondary index operations: Index creation, index rebuild
   - Materialized view operations: MV creation, MV updates

### Step 2: Identify Restart Points

For each test method, identify potential restart points based on these criteria:

**Good Restart Points** (inject here):
- After table creation but before data insertion
- After write operations (especially with consistency levels)
- After CAS/Paxos operations (for linearizability testing)
- During repair operations (for consistency testing)
- After flush/compaction operations
- During streaming operations (bootstrap, decommission)
- After schema changes
- During gossip state changes
- After hinted handoff delivery
- During batch operations
- After snapshot operations
- Before/after node operations (join, leave, decommission)

**Poor Restart Points** (avoid):
- Before cluster setup (no cluster exists yet)
- After cluster teardown (cluster already destroyed)
- During trivial operations (simple reads with no state changes)
- Operations that are too fast to test meaningful state

**Naming Convention for Restart Positions**:
- Use descriptive, lowercase names with underscores
- Pattern: `{operation}_{context}`
- Examples:
  - `after_table_create`
  - `after_write`
  - `after_cas_operation`
  - `after_paxos_repair`
  - `after_flush`
  - `during_repair`
  - `after_compaction`
  - `during_bootstrap`
  - `after_hint_delivery`
  - `after_schema_change`
  - `during_gossip_settle`
  - `after_snapshot`
  - `before_decommission`
  - `after_batch_write`

### Step 3: Generate the Restart-Injected Test File

Create a new test file with the following transformations:

#### 3.1 Package and Imports

```java
// Keep original package declaration
package org.apache.cassandra.distributed.test;

// Add these imports at the top (if not already present)
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Keep all original imports
```

#### 3.2 Class Declaration

```java
// Original class name: SimpleReadWriteTest
// New class name: SimpleReadWriteTest_RestartInjected

// Keep the same inheritance structure!
// If original: public class SimpleReadWriteTest extends TestBaseImpl
// Then use:    public class SimpleReadWriteTest_RestartInjected extends TestBaseImpl
public class SimpleReadWriteTest_RestartInjected {
    // Keep all original fields and variables
}
```

**IMPORTANT**: If the test class extends a base class, preserve this inheritance in the generated test. The cluster object may be defined in the parent class.

#### 3.3 Cluster Setup and Teardown

Keep the `@Before`/`@BeforeAll`/`@BeforeEach` and `@After`/`@AfterAll`/`@AfterEach` methods unchanged:

```java
@BeforeAll
public static void setUp() throws Exception {
    // Keep original setup code unchanged
}

@AfterAll
public static void tearDown() throws Exception {
    // Keep original teardown code unchanged
}
```

**Note**: Setup methods may be inherited from parent class. In that case, don't add setup methods to the generated test.

#### 3.4 Transform Test Methods

For each `@Test` method, apply the following transformations:

**Original Test Method**:
```java
@Test
public void testSimpleReadWrite() throws Exception {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE distributed_test_keyspace.tbl (pk int PRIMARY KEY, v int)");

        cluster.coordinator(1).execute("INSERT INTO distributed_test_keyspace.tbl (pk, v) VALUES (1, 1)",
                                       ConsistencyLevel.QUORUM);

        Object[][] result = cluster.coordinator(1).execute("SELECT * FROM distributed_test_keyspace.tbl WHERE pk = 1",
                                                           ConsistencyLevel.QUORUM);

        assertRows(result, row(1, 1));
    }
}
```

**Transformed Test Method**:
```java
@Test
public void testSimpleReadWrite() throws Exception {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE distributed_test_keyspace.tbl (pk int PRIMARY KEY, v int)");

        // RESTART POINT 1: after_table_create
        RestartFramework.at("after_table_create")
            .on(cluster)  // Use the cluster instance
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        cluster.coordinator(1).execute("INSERT INTO distributed_test_keyspace.tbl (pk, v) VALUES (1, 1)",
                                       ConsistencyLevel.QUORUM);

        // RESTART POINT 2: after_write
        RestartFramework.at("after_write")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        Object[][] result = cluster.coordinator(1).execute("SELECT * FROM distributed_test_keyspace.tbl WHERE pk = 1",
                                                           ConsistencyLevel.QUORUM);

        assertRows(result, row(1, 1));
    }
}
```

**IMPORTANT**: Identify the correct cluster variable name:
- If defined locally: `cluster`
- If inherited from parent: check parent class for field name (commonly `cluster`)
- Use the actual variable name in `.on(variableName)`

**CRITICAL INDEXING NOTE**:
- Cassandra Cluster uses **1-based indexing** (cluster.get(1) is the first node)
- The restart framework uses **0-based indexing** (withIndex(0) is the first node)
- The adapter automatically translates between these (framework index 0 → Cassandra index 1)
- **Always use 0-based indexing in restart points**: `.withIndex(0)`, `.withIndex(1)`, `.withIndex(2)`

**Injection Pattern**:

1. **After Schema Operations**:
   ```java
   cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

   // Inject restart point
   RestartFramework.at("after_table_create")
       .on(cluster)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

2. **After Write Operations**:
   ```java
   cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.QUORUM);

   // Inject restart point
   RestartFramework.at("after_write")
       .on(cluster)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. **After CAS Operations**:
   ```java
   cluster.coordinator(1).execute("UPDATE ks.tbl SET v = 2 WHERE pk = 1 IF v = 1", ConsistencyLevel.SERIAL);

   // Inject restart point
   RestartFramework.at("after_cas_operation")
       .on(cluster)
       .restart("node")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

4. **During Long Operations**:
   ```java
   // Start repair
   cluster.get(1).nodetoolResult("repair", "ks", "tbl").asserts().success();

   // Inject restart point after repair completes
   RestartFramework.at("after_repair")
       .on(cluster)
       .restart("node")
       .withIndex(1)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

#### 3.5 Cassandra-Specific Node Roles

Cassandra is a **peer-to-peer distributed system** with no master/worker distinction. All nodes are identical in function.

**Node Roles**:
- **`node`** (or `"peer"`): Any node in the cluster (all nodes are functionally identical)
- **`all`**: All nodes in the cluster

**Default Restart Configuration**:
Use these defaults for all injected restart points:
- **Node Role**: `"node"` (all nodes are peers in Cassandra)
- **Node Index**: `0` (first node, framework uses 0-based indexing)
- **Restart Mode**: `RestartMode.GRACEFUL` (default, safest)

#### 3.6 Node Role Selection Guidelines

Since Cassandra is peer-to-peer, all operations can be tested on any node. However, certain operations may be coordinated by specific nodes:

| Operation Type | Node Role | Index Selection | Reason |
|----------------|-----------|-----------------|--------|
| Schema changes | `node` | Any (0, 1, 2, or random) | Schema propagates via gossip |
| Write operations | `node` | Coordinator node | Test coordinator failure |
| Read operations | `node` | Coordinator node | Test read path resilience |
| CAS operations | `node` | Coordinator node | Test Paxos resilience |
| Repair operations | `node` | Repair coordinator | Test repair interruption |
| Streaming | `node` | Streaming node | Test bootstrap/decommission |
| Gossip | `node` | Any | Test gossip propagation |
| Hints | `node` | Hint recipient | Test hint delivery |
| Compaction | `node` | Node with data | Test compaction resilience |
| Flush | `node` | Node with memtable | Test flush durability |

### Step 4: Generate Restart Configuration File

Create `restarts-config/restart-config.json` with the following structure:

```json
{
  "tests": [
    {
      "testClass": "org.apache.cassandra.distributed.test.SimpleReadWriteTest_RestartInjected",
      "testMethod": "testSimpleReadWrite",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["node"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_write",
          "targets": ["node"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        }
      ]
    }
  ]
}
```

#### Configuration Generation Rules

For each test method in the transformed test:

1. **Create a test specification** with:
   - `testClass`: The fully-qualified name of the generated test class
   - `testMethod`: The test method name (same as original)
   - `restartPoints`: Array of restart point configurations

2. **For each restart point** injected in the test method:
   - `position`: The position identifier used in `.at("...")`
   - `targets`: Array of node roles to test (typically `["node"]`)
   - `modes`: Array of restart modes to test

#### Target Selection for Cassandra Operations

**Single Node** (`["node"]`):
- Most operations (Cassandra is peer-to-peer)
- Write operations
- Read operations
- CAS operations
- Schema changes
- Repair operations
- Compaction/flush
- Snapshot operations

**All Nodes** (`["all"]`):
- Cluster-wide consistency tests
- Full cluster restart scenarios
- Schema propagation tests
- Gossip convergence tests

#### Mode Selection Guidelines

- **`["GRACEFUL"]`**: Basic test, verify restart works
  - Use for: Initial testing, simple state transitions

- **`["GRACEFUL", "CRASH"]`**: Standard test, verify crash recovery
  - Use for: Schema changes, data operations, flush operations

- **`["GRACEFUL", "CRASH", "DELAYED_CRASH"]`**: Advanced test, verify timing-sensitive operations
  - Use for: CAS/Paxos operations, repair, streaming, hinted handoff, distributed coordination

### Step 5: File Placement

1. **Generated Test File**:
   - Location: Same directory as original test file
   - Name: `{OriginalClassName}_RestartInjected.java`
   - Example: `SimpleReadWriteTest.java` → `SimpleReadWriteTest_RestartInjected.java`

2. **Restart Configuration**:
   - Location: `restarts-config/` directory under the same module directory as the test file
   - Name: `restart-config.json`
   - If file exists, append to the `tests` array (avoid duplicates)
   - If file doesn't exist, create new file

## Example Transformation

### Input: `SimpleReadWriteTest.java`

```java
package org.apache.cassandra.distributed.test;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.junit.Test;

import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class SimpleReadWriteTest extends TestBaseImpl {

    @Test
    public void testWriteRead() throws Throwable {
        try (Cluster cluster = init(Cluster.build(3)
                                          .withConfig(config -> config.with(GOSSIP, NETWORK))
                                          .start())) {
            cluster.schemaChange("CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}");
            cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

            cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.QUORUM);

            Object[][] results = cluster.coordinator(1).execute("SELECT * FROM ks.tbl WHERE pk = 1", ConsistencyLevel.QUORUM);
            assertRows(results, row(1, 1));
        }
    }
}
```

### Output 1: `SimpleReadWriteTest_RestartInjected.java`

```java
package org.apache.cassandra.distributed.test;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class SimpleReadWriteTest_RestartInjected extends TestBaseImpl {

    @Test
    public void testWriteRead() throws Throwable {
        try (Cluster cluster = init(Cluster.build(3)
                                          .withConfig(config -> config.with(GOSSIP, NETWORK))
                                          .start())) {
            cluster.schemaChange("CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}");
            cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

            // RESTART POINT 1: after_table_create
            RestartFramework.at("after_table_create")
                .on(cluster)
                .restart("node")
                .withIndex(0)  // First node (framework uses 0-based indexing)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.QUORUM);

            // RESTART POINT 2: after_write
            RestartFramework.at("after_write")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            Object[][] results = cluster.coordinator(1).execute("SELECT * FROM ks.tbl WHERE pk = 1", ConsistencyLevel.QUORUM);
            assertRows(results, row(1, 1));
        }
    }
}
```

### Output 2: `restarts-config/restart-config.json`

```json
{
  "tests": [
    {
      "testClass": "org.apache.cassandra.distributed.test.SimpleReadWriteTest_RestartInjected",
      "testMethod": "testWriteRead",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["node"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_write",
          "targets": ["node"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        }
      ]
    }
  ]
}
```

## Cassandra-Specific Patterns

### Pattern 1: Schema Change Testing

```java
@Test
public void testSchemaChange() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}");

        RestartFramework.at("after_keyspace_create")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .execute();

        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .execute();

        // Verify schema persists
        assertTrue(cluster.get(1).callOnInstance(() -> {
            return org.apache.cassandra.db.Keyspace.open("ks") != null;
        }));
    }
}
```

### Pattern 2: CAS/Paxos Testing

```java
@Test
public void testCAS() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");
        cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.QUORUM);

        // Perform CAS operation
        cluster.coordinator(1).execute("UPDATE ks.tbl SET v = 2 WHERE pk = 1 IF v = 1", ConsistencyLevel.SERIAL);

        RestartFramework.at("after_cas_operation")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.CRASH)
            .execute();

        // Verify CAS result persisted
        Object[][] result = cluster.coordinator(1).execute("SELECT v FROM ks.tbl WHERE pk = 1", ConsistencyLevel.QUORUM);
        assertRows(result, row(2));
    }
}
```

### Pattern 3: Repair Testing

```java
@Test
public void testRepair() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        // Write data with inconsistency
        cluster.filters().inbound().from(1).to(3).drop();
        cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.ONE);
        cluster.filters().reset();

        RestartFramework.at("before_repair")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .execute();

        // Run repair
        cluster.get(1).nodetoolResult("repair", "ks").asserts().success();

        RestartFramework.at("after_repair")
            .on(cluster)
            .restart("node")
            .withIndex(2)
            .execute();

        // Verify data is consistent
        for (int i = 1; i <= 3; i++) {
            Object[][] result = cluster.get(i).executeInternal("SELECT * FROM ks.tbl WHERE pk = 1");
            assertRows(result, row(1, 1));
        }
    }
}
```

### Pattern 4: Gossip and Ring State Testing

```java
@Test
public void testGossipSettles() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).withConfig(config -> config.with(GOSSIP)).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        // Wait for gossip to settle
        cluster.forEach(instance -> instance.runOnInstance(() -> {
            org.apache.cassandra.gms.Gossiper.instance.waitToSettle();
        }));

        RestartFramework.at("after_gossip_settle")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .execute();

        // Verify ring state
        cluster.forEach(instance -> {
            assertTrue(!instance.isShutdown());
        });
    }
}
```

### Pattern 5: Hinted Handoff Testing

```java
@Test
public void testHintedHandoff() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        // Write with one node down to create hints
        cluster.get(3).shutdown().get();
        cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (1, 1)", ConsistencyLevel.ONE);

        RestartFramework.at("after_write_with_hint")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .execute();

        // Bring node 3 back
        cluster.get(3).startup();

        // Wait for hints delivery
        Thread.sleep(5000);

        RestartFramework.at("after_hint_delivery")
            .on(cluster)
            .restart("node")
            .withIndex(2)
            .execute();

        // Verify hint was delivered
        Object[][] result = cluster.get(3).executeInternal("SELECT * FROM ks.tbl WHERE pk = 1");
        assertRows(result, row(1, 1));
    }
}
```

### Pattern 6: Streaming/Bootstrap Testing

```java
@Test
public void testBootstrap() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        // Write data
        for (int i = 0; i < 100; i++) {
            cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (?, ?)",
                                          ConsistencyLevel.QUORUM, i, i);
        }

        RestartFramework.at("before_bootstrap")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .execute();

        // Add a new node (would require cluster.bootstrap() if available)
        // For now, just test restart during data load

        RestartFramework.at("during_data_load")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.CRASH)
            .execute();
    }
}
```

### Pattern 7: Flush and Compaction Testing

```java
@Test
public void testFlushCompaction() throws Throwable {
    try (Cluster cluster = init(Cluster.build(3).start())) {
        cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY, v int)");

        // Write data
        for (int i = 0; i < 1000; i++) {
            cluster.coordinator(1).execute("INSERT INTO ks.tbl (pk, v) VALUES (?, ?)",
                                          ConsistencyLevel.QUORUM, i, i);
        }

        // Flush
        cluster.forEach(instance -> instance.flush("ks"));

        RestartFramework.at("after_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .execute();

        // Compact
        cluster.get(1).nodetoolResult("compact", "ks", "tbl").asserts().success();

        RestartFramework.at("after_compact")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .execute();

        // Verify data
        Object[][] result = cluster.coordinator(1).execute("SELECT COUNT(*) FROM ks.tbl", ConsistencyLevel.QUORUM);
        assertRows(result, row(1000L));
    }
}
```

## Validation Checklist

After transformation, verify:

- [ ] Generated test file compiles without errors
- [ ] All original test logic is preserved
- [ ] Class inheritance is maintained (if test extends a base class)
- [ ] Correct cluster variable name is used (check parent classes if inherited)
- [ ] Restart points are placed at meaningful Cassandra operations
- [ ] Restart position names are descriptive and Cassandra-specific
- [ ] Node role is "node" (Cassandra is peer-to-peer)
- [ ] **Node indices use 0-based indexing** (framework convention)
- [ ] Configuration file has correct fully-qualified class names
- [ ] Configuration file includes all restart points from the test
- [ ] Target arrays use `["node"]` for most operations
- [ ] Mode arrays are appropriate for timing sensitivity
- [ ] Files are placed in correct locations
- [ ] Original test file is not modified (only new files created)

## Advanced Scenarios

### Multiple Test Methods

If the original test has multiple `@Test` methods:

1. Transform each method independently
2. Inject restart points in each method
3. Create a separate test specification for each method in the configuration

Example configuration:
```json
{
  "tests": [
    {
      "testClass": "org.apache.cassandra.distributed.test.CASTest_RestartInjected",
      "testMethod": "testBasicCAS",
      "restartPoints": [...]
    },
    {
      "testClass": "org.apache.cassandra.distributed.test.CASTest_RestartInjected",
      "testMethod": "testBatchCAS",
      "restartPoints": [...]
    }
  ]
}
```

### Inherited Cluster from Base Class

**CRITICAL**: Many Cassandra tests extend `TestBaseImpl` which contains cluster management:

**Example Base Class** (`TestBaseImpl.java`):
```java
public class TestBaseImpl {
    protected Cluster cluster;

    protected Cluster init(Cluster.Builder builder) throws IOException {
        cluster = builder.start();
        return cluster;
    }
}
```

**Example Test Using Base Class**:
```java
public class SimpleReadWriteTest extends TestBaseImpl {
    @Test
    public void testWrite() throws Throwable {
        try (Cluster cluster = init(Cluster.build(3).start())) {
            // No cluster setup here - it's in init()!
            cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY)");
        }
    }
}
```

**Transformation Strategy**:
1. **Preserve the inheritance**: `SimpleReadWriteTest_RestartInjected extends TestBaseImpl`
2. **Do NOT duplicate setup/teardown** methods in the generated test
3. **Use the cluster variable**: `.on(cluster)` from the try-with-resources or field
4. **Check parent class** to identify cluster initialization pattern

**Generated Test**:
```java
public class SimpleReadWriteTest_RestartInjected extends TestBaseImpl {
    // NO setup/teardown methods - inherited from parent or used via init()!

    @Test
    public void testWrite() throws Throwable {
        try (Cluster cluster = init(Cluster.build(3).start())) {
            cluster.schemaChange("CREATE TABLE ks.tbl (pk int PRIMARY KEY)");

            // Use 'cluster' variable from try-with-resources
            RestartFramework.at("after_create")
                .on(cluster)
                .restart("node")
                .withIndex(0)  // 0-based indexing!
                .execute();
        }
    }
}
```

### Helper Methods

If the test has helper methods:

1. **Do not inject restart points in helper methods**
2. Only inject in `@Test` annotated methods
3. Keep helper methods unchanged

### Tests Without Obvious Restart Points

If a test has no clear state transitions:

1. Inject restart points within the range of (a) after cluster setup and (b) before cluster teardown
2. Evenly distribute restart points to cover the test execution
3. You MUST Use percentage-based positions (e.g., `at_25_percent`, `at_50_percent`) to at least cover 4 points during the test execution
4. Find cluster operations to place restart points around

**IMPORTANT**: You are NOT allowed to skip any test transformation due to lack of restart points. Always inject at least one restart point per test method.

## Common Cassandra Operations and Suggested Restart Points

| Cassandra Operation | Suggested Restart Point Name | Node Role | Timing |
|---------------------|------------------------------|-----------|--------|
| `schemaChange()` CREATE TABLE | `after_table_create` | `node` | After |
| `schemaChange()` DROP TABLE | `before_table_drop` | `node` | Before |
| `schemaChange()` ALTER TABLE | `after_table_alter` | `node` | After |
| `schemaChange()` CREATE KEYSPACE | `after_keyspace_create` | `node` | After |
| `execute()` INSERT | `after_insert` | `node` | After |
| `execute()` UPDATE | `after_update` | `node` | After |
| `execute()` DELETE | `after_delete` | `node` | After |
| `execute()` SELECT | `during_select` | `node` | During |
| `execute()` with IF (CAS) | `after_cas_operation` | `node` | After |
| `execute()` BATCH | `after_batch` | `node` | After |
| `flush()` | `after_flush` | `node` | After |
| `forceBlockingFlush()` | `after_blocking_flush` | `node` | After |
| `compact()` | `before_compact`, `after_compact` | `node` | Before/After |
| `nodetool repair` | `before_repair`, `after_repair` | `node` | Before/After |
| `nodetool snapshot` | `after_snapshot` | `node` | After |
| `nodetool clearsnapshot` | `after_clear_snapshot` | `node` | After |
| Gossip settle | `after_gossip_settle` | `node` | After |
| Node join/leave | `after_node_join`, `before_node_leave` | `node` | After/Before |
| Streaming (bootstrap) | `during_bootstrap` | `node` | During |
| Decommission | `before_decommission`, `after_decommission` | `node` | Before/After |
| Hint delivery | `after_hint_delivery` | `node` | After |
| Paxos repair | `after_paxos_repair` | `node` | After |

## Notes

- **Non-invasive**: Original test file is never modified
- **Incremental**: Can transform tests one at a time
- **Compatible**: Generated tests can run both with and without restart injection
- **Configurable**: Configuration file allows easy adjustment of test matrix
- **Cassandra-aware**: Peer-to-peer architecture (no master/worker distinction)
- **Index-aware**: Framework uses 0-based indexing, adapter translates to Cassandra's 1-based indexing
- **Inheritance-aware**: Handles tests that inherit cluster setup from parent classes

## Dependencies

Ensure the following dependencies are included in the test's module to use the Restart Testing Framework.

**IMPORTANT**: Cassandra uses **Ant** as its build system, not Maven. Dependencies must be added to the Ant build file.

### Adding Dependencies to Cassandra (Ant-based)

#### Step 1: Add JAR Dependencies to `lib/` Directory

The restart adapter is built separately and its JAR files should be copied to Cassandra's `lib/` directory:

```bash
# Build the restart-adapter first
cd /home/shuai/xlab/restart_testing/cassandra/restart-adapter
ant clean jar

# Copy built JARs to Cassandra lib directory
cp dist/*.jar /home/shuai/xlab/restart_testing/cassandra/lib/
```

#### Step 2: Update `build.xml` Classpath

Cassandra's `build.xml` should include the restart testing framework JARs in the test classpath:

```xml
<!-- In build.xml, add to test.classpath -->
<path id="test.classpath">
    <!-- Existing test classpath entries... -->

    <!-- Restart Testing Framework -->
    <fileset dir="${basedir}/lib">
        <include name="restart-*.jar"/>
    </fileset>
</path>
```

#### Step 3: Verify Dependencies

Check that the restart adapter JARs are available:

```bash
# List restart-related JARs
ls -la /home/shuai/xlab/restart_testing/cassandra/lib/restart-*.jar
```

Expected files:
- `restart-adapter.jar` - The Cassandra cluster adapter
- `restart-core.jar` - The core restart framework (dependency of adapter)

## Build Requirements

**IMPORTANT**: Cassandra uses **Ant** as its build system, not Maven.

### Prerequisites

1. **Java 17+** - Required for building Cassandra
2. **Ant 1.10+** - Build tool
3. **Restart Testing Framework** - Core framework and Cassandra adapter

### Build Steps

#### Step 1: Build Cassandra Restart Adapter (Standalone)

The restart-adapter must be built separately first:

```bash
cd /home/shuai/xlab/restart_testing/cassandra/restart-adapter

# Build and create JAR
ant clean jar

# Verify JAR was created
ls -la dist/restart-adapter.jar
```

#### Step 2: Copy Dependencies to Cassandra

```bash
# Copy restart adapter JARs to Cassandra's lib directory
cp /home/shuai/xlab/restart_testing/cassandra/restart-adapter/dist/*.jar \
   /home/shuai/xlab/restart_testing/cassandra/lib/

# Verify
ls -la /home/shuai/xlab/restart_testing/cassandra/lib/restart-*.jar
```

#### Step 3: Build Cassandra with Tests

```bash
cd /home/shuai/xlab/restart_testing/cassandra

# Build Cassandra
ant clean build

# Build test classes
ant build-test
```

#### Step 4: Run Restart-Injected Tests

```bash
# Run a specific restart-injected test
ant testsome -Dtest.name=SimpleReadWriteTest_RestartInjected

# Run all distributed tests (including restart-injected ones)
ant test-distributed
```

### Ant Build Commands

Cassandra uses Ant instead of Maven. Here are the key commands:

```bash
# Build entire project
ant clean build

# Build without tests
ant clean jar

# Build test classes
ant build-test

# Run all tests
ant test

# Run a specific test class
ant testsome -Dtest.name=SimpleReadWriteTest_RestartInjected

# Run distributed tests
ant test-distributed

# Run test with restart injection (using JVM properties)
ant testsome -Dtest.name=MyTest_RestartInjected \
  -Drestart.position=after_write \
  -Drestart.target=node \
  -Drestart.mode=CRASH \
  -Drestart.index=0
```

### Module Structure

Cassandra distributed tests are located in:

```
cassandra/
├── test/
│   └── distributed/
│       └── org/apache/cassandra/distributed/
│           ├── test/              # All distributed tests (267 tests to transform)
│           ├── Cluster.java       # Cluster class used by tests
│           └── api/               # Cluster API interfaces
├── restart-adapter/               # Restart adapter (standalone Ant project)
│   ├── build.xml                  # Ant build file for adapter
│   ├── src/main/java/            # Adapter source code
│   └── dist/                      # Built JARs
└── lib/                           # Cassandra dependencies (add restart JARs here)
```

### Troubleshooting

**Ant Build Errors**:
If you encounter classpath errors:
```bash
# Verify restart JARs are in lib/
ls -la lib/restart-*.jar

# Clean and rebuild
ant clean build-test
```

**Java Version Issues**:
Cassandra requires Java 17+:
```bash
# Check Java version
java -version  # Should show Java 17 or higher

# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

**Restart Adapter Not Found**:
If tests can't find the adapter:
```bash
# Rebuild and reinstall the adapter
cd restart-adapter
ant clean jar
cp dist/*.jar ../lib/
```

### Complete Build Example

Complete workflow from scratch:

```bash
# 1. Build restart adapter
cd /home/shuai/xlab/restart_testing/cassandra/restart-adapter
ant clean jar

# 2. Copy adapter JARs to Cassandra lib
cp dist/*.jar ../lib/

# 3. Build Cassandra with tests
cd /home/shuai/xlab/restart_testing/cassandra
ant clean build-test

# 4. Run a restart-injected test
ant testsome -Dtest.name=SimpleReadWriteTest_RestartInjected
```

## Key Differences from Maven-based Projects

1. **Build System**: Ant (`build.xml`) instead of Maven (`pom.xml`)
2. **Dependencies**: JAR files in `lib/` directory instead of Maven coordinates
3. **Test Execution**: `ant testsome -Dtest.name=...` instead of `mvn test -Dtest=...`
4. **Build Commands**: `ant build` instead of `mvn compile`
5. **Clean**: `ant clean` instead of `mvn clean`
6. **Test Output**: Different directory structure for test results

## Target Tests Location

All target tests are listed in:
```
/home/shuai/xlab/restart_testing/cassandra/TRANSFORMATION_TRACKER.md
```

Total: **267 tests** to transform across multiple categories:
- Guardrails tests
- Distributed core tests
- Distributed specialized tests (auth, cdc, fql, gossip, guardrails, etc.)
- Repair tests
- Simulator tests
