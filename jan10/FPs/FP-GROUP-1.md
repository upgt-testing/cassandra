# FP-GROUP-1: NoHostAvailableException with DataStax Java Driver

## Classification
**FALSE POSITIVE** - External driver session becomes stale after node restart

## Summary
All 20 test failures in Group 1 share a common pattern: tests use the DataStax Java driver (`com.datastax.driver.core.Session`) which maintains its own host state machine. When the Cassandra node is restarted, the driver marks the host as DOWN and schedules asynchronous reconnection. Since the test continues to use the stale session before reconnection completes, `NoHostAvailableException` is thrown.

## Test Executions Affected
- `DisableBinaryTest_RestartInjected.testDisallowsNewRequests` (after_transport_stop)
- `OverloadTest_RestartInjected.applyClientBackpressure` (after_initial_query, after_enable_slow_select)
- `OverloadTest_RestartInjected.testFinishInProgressQueries` (after_table_create, after_initial_query, after_enable_slow_select)
- `OverloadTest_RestartInjected.clientBackpressureDisabled` (after_initial_query, after_enable_slow_select)
- `PrepareBatchStatementsTest_RestartInjected.testPreparedBatch` (after_schema_creation)
- `ReprepareOldBehaviourTest_RestartInjected.testReprepareMixedVersionWithoutReset` (after_prepare_mixed, after_first_execute_mixed)
- `ReprepareOldBehaviourTest_RestartInjected.testReprepareUsingOldBehavior` (after_prepare, after_first_execute)
- `ReprepareNewBehaviourTest_RestartInjected.testUseWithMultipleKeyspaces` (after_schema_setup, after_prepare_ks1, after_insert_ks2, after_query_ks1, after_switch_ks2, after_prepare_ks2, after_query_ks2)

## Stack Trace Pattern
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
    at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
    at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
    at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
    at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
...
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
    at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
```

## Root Cause Analysis

### Problem Flow
1. Test creates a DataStax Java driver `Cluster` and `Session`
2. Driver establishes connection pool to Cassandra node
3. Test runs initial queries successfully
4. Restart framework triggers node restart at specified position
5. During restart shutdown phase:
   - Driver detects connection loss
   - Driver marks host as DOWN
   - Driver schedules reconnection with exponential backoff (typically 16 seconds)
6. Node restarts and native transport becomes available
7. Test continues and tries to execute query using the same session
8. Driver throws `NoHostAvailableException` because:
   - Host is still marked as DOWN
   - Reconnection hasn't completed yet (16-second delay)
   - Message: "no host was tried" confirms this

### Evidence from Logs
```
ERROR [cluster1-reconnection-0] ... Cannot connect to any host, scheduling retry in 16000 milliseconds
DEBUG [cluster1-reconnection-1] ... Failed reconnection to /127.0.0.1:9042 ([/127.0.0.1:9042] Cannot connect), scheduling retry in 16000 milliseconds
...
INFO  [node1_isolatedExecutor:1] ... Starting listening for CQL clients on /127.0.0.1:9042 (unencrypted)...
```

The driver scheduled reconnection with a 16-second delay, but the test tried to execute a query immediately after restart completed.

## Why This Is NOT a Cassandra Bug

1. **Cassandra Node Restarts Correctly**: The node shuts down gracefully and restarts with native transport enabled. The logs show successful startup.

2. **DataStax Driver Behavior is Expected**:
   - The driver maintains its own host state machine independent of Cassandra
   - When a connection is lost, the host is marked DOWN
   - Reconnection is asynchronous with exponential backoff
   - This is standard driver behavior for resilience

3. **External Library Limitation**: The restart framework cannot refresh the DataStax driver session because:
   - It's a third-party library with internal state
   - The session object is created and managed by the test
   - There's no API to "reconnect" without destroying the session

4. **Production Scenario Differences**: In production, applications would:
   - Configure retry policies that wait for reconnection
   - Use connection pools that handle reconnection transparently
   - Create new sessions if needed after restart
   - Wait for the driver's host to become available again

## Why This Is a False Positive

The restart positions in these tests are placed at points where:
1. The test still needs to use the driver session for subsequent queries
2. The test does not expect any delay for driver reconnection
3. The test logic depends on immediate query execution after restart

The failure occurs because the restart framework:
1. Restarts the Cassandra node (correctly)
2. Cannot refresh the DataStax driver session (external library)
3. The test tries to use the stale session before driver reconnection completes

## Reproduction
```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64 && \
ant test-jvm-dtest-some \
  -Dtest.name=org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected \
  -Dtest.methods=testDisallowsNewRequests \
  -Dno-build-test=true \
  -Dtest.jvm.args="-Drestart.position=after_transport_stop -Drestart.target=node -Drestart.mode=GRACEFUL -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar"
```

## Recommendation

The restart framework should either:
1. Skip restart injection for tests that use external driver sessions that cannot be refreshed
2. Add explicit wait time after restart for driver reconnection to complete
3. Mark these positions as invalid restart positions in the framework configuration

Alternatively, the tests could be enhanced to:
1. Create a new driver session after restart
2. Configure retry policies that handle temporary unavailability
3. Add explicit waits for host to become available
