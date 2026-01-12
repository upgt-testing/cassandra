# BUG-GROUP-19: NullPointerException in CompactionManager.submitMaximal

## Summary

`CompactionManager.submitMaximal()` does not handle the case where `getMaximalTasks()` returns `null`, leading to a `NullPointerException` when calling `tasks.isEmpty()`.

## Classification

**Type**: BUG (Source Code Issue)

## Exception Details

```
java.lang.NullPointerException: Cannot invoke "org.apache.cassandra.db.compaction.CompactionTasks.isEmpty()" because "tasks" is null
    at org.apache.cassandra.db.compaction.CompactionManager.submitMaximal(CompactionManager.java:1001)
```

## Root Cause Analysis

### Buggy Code Location

**File**: `src/java/org/apache/cassandra/db/compaction/CompactionManager.java`
**Method**: `submitMaximal(ColumnFamilyStore cfStore, long gcBefore, boolean splitOutput, OperationType operationType)`
**Lines**: 994-1008

### Original Buggy Code

```java
public List<Future<?>> submitMaximal(final ColumnFamilyStore cfStore, final long gcBefore, boolean splitOutput, OperationType operationType)
{
    // here we compute the task off the compaction executor, so having that present doesn't
    // confuse runWithCompactionsDisabled -- i.e., we don't want to deadlock ourselves, waiting
    // for ourselves to finish/acknowledge cancellation before continuing.
    CompactionTasks tasks = cfStore.getCompactionStrategyManager().getMaximalTasks(gcBefore, splitOutput, operationType);

    if (tasks.isEmpty())  // NPE here when tasks is null
        return Collections.emptyList();
    // ...
}
```

### Why `getMaximalTasks()` Can Return Null

The `getMaximalTasks()` method in `CompactionStrategyManager` uses `runWithCompactionsDisabled()` internally:

```java
// CompactionStrategyManager.java:1076-1100
public CompactionTasks getMaximalTasks(final long gcBefore, final boolean splitOutput, OperationType operationType)
{
    maybeReloadDiskBoundaries();
    return cfs.runWithCompactionsDisabled(() -> {
        // ... create tasks
        return CompactionTasks.create(tasks);
    }, operationType, false, false);
}
```

The `runWithCompactionsDisabled()` method in `ColumnFamilyStore` can return `null` in two scenarios:

1. **When there are uninterruptible higher-priority compactions** (ColumnFamilyStore.java:2850-2858):
```java
if (!uninterruptibleTasks.isEmpty())
{
    logger.info("Unable to cancel in-progress compactions, since they're running with higher or same priority: {}...");
    return null;  // Returns null here!
}
```

2. **When compaction cannot be interrupted due to timeout** (ColumnFamilyStore.java:2868-2873):
```java
if (cfs.getTracker().getCompacting().stream().anyMatch(sstablesPredicate))
{
    logger.warn("Unable to cancel in-progress compactions for {}...");
    return null;  // Returns null here too!
}
```

### Evidence That Null Check Is Expected

Other callers in the codebase properly handle null returns. For example, in `PendingRepairHolder.getMaximalTasks()`:

```java
// PendingRepairHolder.java:130-132
Collection<AbstractCompactionTask> task = manager.getMaximalTasks(gcBefore, splitOutput);
if (task != null)  // Proper null check!
    tasks.addAll(task);
```

And `PendingRepairManager.getMaximalTasks()` explicitly returns null:

```java
// PendingRepairManager.java:394-397
synchronized Collection<AbstractCompactionTask> getMaximalTasks(long gcBefore, boolean splitOutput)
{
    if (strategies.isEmpty())
        return null;  // Explicitly returns null
    // ...
}
```

## How Restart Exposes This Bug

During a node restart:
1. An async compaction task may be executing `submitMaximal()`
2. The restart causes conditions where there are uninterruptible compactions (e.g., compactions that couldn't be properly cancelled during shutdown)
3. `runWithCompactionsDisabled()` finds these uninterruptible tasks and returns `null`
4. `submitMaximal()` doesn't check for `null`, resulting in NPE

This is a latent bug in the production code that the restart testing scenario exposed.

## Proposed Fix

Add a null check before calling `isEmpty()`:

```java
public List<Future<?>> submitMaximal(final ColumnFamilyStore cfStore, final long gcBefore, boolean splitOutput, OperationType operationType)
{
    CompactionTasks tasks = cfStore.getCompactionStrategyManager().getMaximalTasks(gcBefore, splitOutput, operationType);

    if (tasks == null || tasks.isEmpty())  // Added null check
        return Collections.emptyList();

    // ... rest of the method
}
```

## Impact

- **Severity**: Medium
- **Affected Versions**: Cassandra 5.0.x (at least)
- **Production Impact**: Can cause NPE during high-load scenarios where compactions compete, or during node restart/shutdown operations

## Test Information

- **Test Class**: `org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected`
- **Test Method**: `upgradeSSTablesInterruptsOngoingCompaction`
- **Restart Position**: `after_upgradesstables`
- **Restart Mode**: `GRACEFUL`
- **Restart Target**: `node` (index 0)

## Race Condition Analysis

### Test Flow and Timing

The test `upgradeSSTablesInterruptsOngoingCompaction` follows this sequence:

```
1. Create table and disable auto-compaction
2. Insert data and flush (creates 5 SSTables)
3. Start async compaction task via submitMaximal() [Line 101-104]
   └── ByteBuddy intercepts beginCompaction() and PAUSES here
4. Wait for compaction to start (paused state) [Line 106]
5. Release the latch, allowing compaction to proceed [Line 107-108]
6. Run 'nodetool upgradesstables' [Line 109]
7. >>> RESTART INJECTION POINT: after_upgradesstables <<< [Line 111-116]
8. Call future.get() to retrieve async task result [Line 118]
```

### What Triggers the NPE (Race Condition Window)

The NPE occurs when the restart happens during a specific timing window:

```
Timeline A (NPE occurs):
─────────────────────────────────────────────────────────────────────────────
[Async Task]     submitMaximal() ──> getMaximalTasks() ──> runWithCompactionsDisabled()
                                                                    │
[Main Thread]    upgradesstables ──────────────────────────────────────────────────────
                                                                    │
[Restart]        ─────────────────────────────────────────── RESTART TRIGGERED
                                                                    │
                                                           Shutdown interrupts
                                                           compactions, but some
                                                           remain "uninterruptible"
                                                                    │
                                                           runWithCompactionsDisabled()
                                                           returns NULL
                                                                    │
                                                           tasks.isEmpty() → NPE!
```

**Conditions that trigger NPE:**
1. The async `submitMaximal()` call is executing during or immediately after restart
2. There are existing compactions (from upgradesstables or cleanup) with same/higher priority
3. `runWithCompactionsDisabled()` cannot interrupt these compactions
4. Returns `null` instead of `CompactionTasks`
5. `submitMaximal()` calls `tasks.isEmpty()` on null → NPE

### What Does NOT Trigger NPE (Alternative Failure Mode)

```
Timeline B (TimeoutException instead):
─────────────────────────────────────────────────────────────────────────────
[Async Task]     submitMaximal() ──> completes successfully before restart
                                                                    │
[Main Thread]    upgradesstables ────────────────────────────────────│────────
                                                                     │
[Restart]        ────────────────────────────────────────── RESTART TRIGGERED
                                                                     │
                                                           Node shuts down
                                                           Compaction stuck waiting
                                                                     │
[Cluster Close]  ─────────────────────────────────────────── TIMEOUT waiting
                                                             for compaction to finish
                                                                     │
                                                           TimeoutException thrown
                                                           at cluster.close()
```

**Conditions that result in TimeoutException (not NPE):**
1. The async `submitMaximal()` completes successfully before restart
2. The compaction task is running but gets stuck during shutdown
3. Cluster close waits for compaction to finish but times out
4. TimeoutException is thrown at line 120 (try-with-resources close)

### Why It's Non-Deterministic

The race depends on:
1. **CPU scheduling**: Which thread runs when during restart
2. **Compaction progress**: How far the compaction has progressed before restart
3. **Shutdown timing**: How quickly compactions are interrupted during graceful shutdown
4. **Async task state**: Whether the lambda has returned from `submitMaximal()` yet

## Reproduction Attempts

### Local Testing Results

Ran the test 5 times with the restart injection at `after_upgradesstables`:

| Run | Result | Exception Type | Location |
|-----|--------|----------------|----------|
| 1 | FAIL | TimeoutException | cluster.close() (line 120) |
| 2 | FAIL | TimeoutException | cluster.close() (line 120) |
| 3 | FAIL | TimeoutException | cluster.close() (line 120) |
| 4 | FAIL | TimeoutException | cluster.close() (line 120) |
| 5 | FAIL | TimeoutException | cluster.close() (line 120) |

**Observation**: All 5 local runs resulted in TimeoutException during cluster close, not the NPE. This indicates the race condition window for NPE is narrow.

### Original Failure (from test execution logs)

The original failure that was captured shows the NPE as the primary exception:
```
java.util.concurrent.ExecutionException: java.lang.NullPointerException: Cannot invoke "org.apache.cassandra.db.compaction.CompactionTasks.isEmpty()" because "tasks" is null
    at org.apache.cassandra.utils.concurrent.AbstractFuture.getWhenDone(AbstractFuture.java:239)
    at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:246)
    at ...UpgradeSSTablesTest_RestartInjected.upgradeSSTablesInterruptsOngoingCompaction(line:118)
    ...
Caused by: java.lang.NullPointerException: Cannot invoke "org.apache.cassandra.db.compaction.CompactionTasks.isEmpty()" because "tasks" is null
    at org.apache.cassandra.db.compaction.CompactionManager.submitMaximal(CompactionManager.java:1001)
    at ...UpgradeSSTablesTest_RestartInjected.lambda$...upgradeSSTablesInterruptsOngoingCompaction$...(line:103)
```

The TimeoutException appears as a **suppressed** exception (during cluster close cleanup).

### Reproduction Command

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64 && \
ant test-jvm-dtest-some \
  -Dtest.name=org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected \
  -Dtest.methods=upgradeSSTablesInterruptsOngoingCompaction \
  -Dno-build-test=true \
  -Dtest.jvm.args="-Drestart.position=after_upgradesstables \
                   -Drestart.target=node \
                   -Drestart.mode=GRACEFUL \
                   -Drestart.tracking.agent=/path/to/restart-tracking-agent.jar"
```

### Tips for Reproducing the NPE

To increase the likelihood of hitting the NPE race condition:
1. Run on a system with higher CPU contention
2. Add artificial delays before/during `submitMaximal()`
3. Reduce the number of compaction threads to 1 (already done in test)
4. Run multiple iterations (the original was caught in automated testing)

## Related Files

- `src/java/org/apache/cassandra/db/compaction/CompactionManager.java` - Contains the bug
- `src/java/org/apache/cassandra/db/compaction/CompactionStrategyManager.java` - Uses `runWithCompactionsDisabled`
- `src/java/org/apache/cassandra/db/ColumnFamilyStore.java` - `runWithCompactionsDisabled` can return null
- `src/java/org/apache/cassandra/db/compaction/PendingRepairHolder.java` - Shows proper null handling pattern
