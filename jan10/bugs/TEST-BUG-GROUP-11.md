# TEST-BUG-GROUP-11: ClusterUtils.parseGossipInfo Incorrect Address Parsing

## Summary

`ClusterUtils.parseGossipInfo()` has a bug in parsing the output of `nodetool gossipinfo`. The function expects instance address lines to start with "/" but the actual output format uses `hostname/IP` or `IP/IP` without a leading slash.

## Affected Code

**File**: `test/distributed/org/apache/cassandra/distributed/shared/ClusterUtils.java`
**Method**: `parseGossipInfo(String str)` (line 682-703)

## Root Cause

The `parseGossipInfo` function uses this logic to detect instance address lines:

```java
private static Map<String, Map<String, String>> parseGossipInfo(String str)
{
    Map<String, Map<String, String>> map = new HashMap<>();
    String[] lines = str.split("\n");
    String currentInstance = null;
    for (String line : lines)
    {
        if (line.startsWith("/"))  // <-- BUG: expects lines to start with "/"
        {
            // start of new instance
            currentInstance = line;
            continue;
        }
        Objects.requireNonNull(currentInstance);  // <-- NPE thrown here
        String[] kv = line.trim().split(":", 2);
        ...
    }
    return map;
}
```

However, the actual output from `nodetool gossipinfo` looks like:

```
localhost/127.0.0.1
  generation:1768115584
  heartbeat:74
  STATUS:23:NORMAL,-1
  ...
127.0.0.2/127.0.0.2
  generation:1768115620
  heartbeat:33
  ...
```

Instance address lines like `localhost/127.0.0.1` or `127.0.0.2/127.0.0.2` do NOT start with "/". When the parser encounters the first line, it doesn't match `startsWith("/")`, so `currentInstance` remains null. When it encounters the second line (a key-value pair like `generation:...`), `Objects.requireNonNull(currentInstance)` throws NPE.

## Why This Bug Was Latent

This bug was not exposed in normal testing because:

1. `awaitGossipSchemaMatch()` returns early when GOSSIP feature is not enabled:
   ```java
   if (!instance.config().has(Feature.GOSSIP))
   {
       return;  // Early return - parseGossipInfo never called
   }
   ```

2. Most tests don't enable the GOSSIP feature explicitly.

3. The bug is only triggered when:
   - GOSSIP feature is enabled (e.g., `Feature.values()`)
   - `awaitGossipSchemaMatch` is called (e.g., by the restart adapter's `waitActive()`)

## Stack Trace

```
Caused by: java.lang.NullPointerException
    at java.base/java.util.Objects.requireNonNull(Objects.java:209)
    at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:695)
    at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:659)
    at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
    at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java:600)
    at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:90)
```

## Tests Affected

- `JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances`
- `JMXGetterCheckTest_RestartInjected.testGetters`
- Any test that:
  1. Uses `Feature.values()` or explicitly enables `Feature.GOSSIP`
  2. Calls `ClusterUtils.awaitGossipSchemaMatch` or uses the restart adapter

## Suggested Fix

Modify the instance address detection logic. Instance address lines:
- Start at column 0 (no leading whitespace)
- Contain "/" but NOT ":"
- Do not start with whitespace

Key-value lines:
- Start with whitespace (indented)
- Contain ":"

```java
private static Map<String, Map<String, String>> parseGossipInfo(String str)
{
    Map<String, Map<String, String>> map = new HashMap<>();
    String[] lines = str.split("\n");
    String currentInstance = null;
    for (String line : lines)
    {
        if (line.isEmpty())
            continue;

        // Instance address lines: no leading whitespace, contain "/" but no ":"
        // e.g., "localhost/127.0.0.1" or "127.0.0.2/127.0.0.2"
        if (!Character.isWhitespace(line.charAt(0)) && line.contains("/") && !line.contains(":"))
        {
            currentInstance = line;
            continue;
        }

        Objects.requireNonNull(currentInstance, "Unexpected line before instance address: " + line);
        String[] kv = line.trim().split(":", 2);
        assert kv.length == 2 : "When splitting line '" + line + "' expected 2 parts but not true";
        Map<String, String> state = map.computeIfAbsent(currentInstance, ignore -> new HashMap<>());
        state.put(kv[0], kv[1]);
    }
    return map;
}
```

## Classification

**Type**: TEST-BUG
**Severity**: Medium
**Component**: Test Framework (ClusterUtils)
