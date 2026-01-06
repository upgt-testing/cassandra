# FP-GROUP-16: TimeoutException - Streaming Timeout

## Classification: FALSE POSITIVE

## Failure Summary
**Error:** `org.awaitility.core.ConditionTimeoutException: Condition with alias 'Did not see stream running or timed out' didn't complete within 3 minutes`

**Test:** `StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.failureDueToSessionTimeout`

**Restart Parameters:**
- Position: `after_trigger_streaming`
- Target: `node`
- Mode: `GRACEFUL`
- Index: `0`

## Root Cause Analysis

### Test Flow
1. The test creates a 2-node cluster with `stream_transfer_task_timeout` set to `1ms`
2. Creates a table
3. **Line 74:** Triggers streaming asynchronously via `ForkJoinPool.commonPool().execute(() -> triggerStreaming(cluster))`
4. **Line 76-81:** Restart point `after_trigger_streaming` immediately restarts node 0
5. **Line 85-87:** Test waits for either `STREAM_IS_RUNNING` signal or "Session timed out" log

### Why This is a False Positive

The restart at `after_trigger_streaming` happens at an inappropriate timing that disrupts the test's expected flow:

1. **Asynchronous Scheduling vs Immediate Restart**: The `triggerStreaming()` call merely schedules a repair operation via ForkJoinPool. The restart executes immediately after this scheduling - before the repair can actually start.

2. **Node1 Unavailability During Critical Phase**: The `triggerStreaming()` method runs a repair on node2 (`node2.nodetoolResult("repair", ...)`) which requires node1 to participate. When node1 is restarted immediately after scheduling, node1 is unavailable when node2 tries to initiate the repair.

3. **ByteBuddy Hooks Never Triggered**: The ByteBuddy hooks that signal `STREAM_IS_RUNNING` are installed on node2 (FAILING_NODE=2). These hooks intercept streaming operations, but streaming never starts because node1 is being restarted.

4. **Neither Condition Can Be Met**:
   - `STREAM_IS_RUNNING` is never signaled because streaming doesn't start
   - "Session timed out" is never logged because no streaming session was established to timeout

### Verification

The original test (without restart injection) passes successfully in ~35 seconds:
```
BUILD SUCCESSFUL
Total time: 35 seconds
```

This confirms the failure is caused by the restart timing, not a Cassandra bug.

## Conclusion

This is a **False Positive** because:
- The restart position is placed immediately after an async task is scheduled, but before the task can execute
- The restart disrupts the test's expected control flow (streaming initiation between nodes)
- The test expects streaming to either run (and be blocked) or timeout, but neither can happen if the participant node is being restarted
- This does not expose any bug in Cassandra source code - it's simply an inappropriate restart position that breaks the test's setup assumptions

## Recommendation

The restart position `after_trigger_streaming` should not be used for this test because:
1. It's placed after an async scheduling call, not after streaming actually starts
2. A more appropriate position would be after streaming has actually begun (e.g., after `STREAM_IS_RUNNING` is signaled)
3. Alternatively, restarting node 1 (index 1, which is node2) instead of node 0 (node1) might be more appropriate since node2 initiates the repair
