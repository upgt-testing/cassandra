# TEST-BUG Report: ClusterUtils.parseGossipInfo Fails to Parse Gossip Output Format

## TL;DR

`parseGossipInfo()` assumes gossip endpoint lines start with `/` (e.g., `/127.0.0.1`), but Java's `InetAddress.toString()` returns `hostname/ipaddress` format (e.g., `localhost/127.0.0.1`) once hostname has been resolved. JMX getter tests trigger hostname resolution, and subsequent calls to `awaitGossipSchemaMatch()` fail with NPE.

## Type

**TEST-BUG** - Bug in test helper code (`ClusterUtils.java`), not in Cassandra source code

## Affected Component

- **File**: `test/distributed/org/apache/cassandra/distributed/shared/ClusterUtils.java`
- **Method**: `parseGossipInfo(String str)` at line 678-699

## Root Cause

### The Bug

```java
if (line.startsWith("/"))  // BUG: assumes "/127.0.0.1" format
```

This incorrectly assumes gossip endpoint lines always start with `/`. However, `InetAddress.toString()` returns:
- `/127.0.0.1` - when hostname has **never** been resolved
- `localhost/127.0.0.1` - when hostname **has** been resolved (cached)

### Why Hostname Gets Resolved

The hostname resolution is triggered by **JMX getter tests** (`JMXGetterCheckTest.testAllValidGetters()`), which call ALL JMX attributes including:

```java
// FailureDetectorMBean methods that trigger hostname resolution:
getAllEndpointStatesWithResolveIp()      // resolveIp=true
getAllEndpointStatesWithPortAndResolveIp() // resolveIp=true
```

These methods call `entry.getKey().getHostName()` on the gossip endpoint addresses (`FailureDetector.java:149`), which caches the hostname in the underlying `InetAddress` objects.

### The Failure Sequence

```
1. Test starts, cluster initializes
   └── Gossip endpoints created with InetAddress (hostname NOT resolved)
   └── InetAddress.toString() → "/127.0.0.1"

2. JMXGetterCheckTest calls all JMX getters
   └── Calls getAllEndpointStatesWithResolveIp()
   └── This calls getHostName() on gossip endpoint InetAddress objects
   └── Hostname "localhost" gets CACHED in the InetAddress objects

3. Node restart occurs
   └── Restart adapter calls awaitGossipSchemaMatch()
   └── This runs "nodetool gossipinfo" (resolveIp=false)
   └── But InetAddress.toString() now returns "localhost/127.0.0.1" (cached!)

4. parseGossipInfo() fails
   └── Checks: line.startsWith("/") → FALSE for "localhost/127.0.0.1"
   └── currentInstance remains null
   └── Objects.requireNonNull(currentInstance) → NPE!
```

### Key Insight: InetAddress Hostname Caching

Java's `InetAddress` caches hostname at the **object level**. The gossip `endpointStateMap` contains shared `InetAddressAndPort` objects. Once ANY code path calls `getHostName()` on these objects, all subsequent `toString()` calls return the hostname.

```java
// InetAddressAndPort.toString(boolean withPort) - line 167-176
public static String toString(InetSocketAddress address, boolean withPort) {
    if (withPort) {
        return toString(address.getAddress(), address.getPort());
    } else {
        return address.getAddress().toString();  // Uses cached hostname!
    }
}
```

## Stack Trace

```
java.lang.NullPointerException
    at java.base/java.util.Objects.requireNonNull(Objects.java:209)
    at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:691)
    at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:655)
    at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
    at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java:600)
```

## Is This a Restart Bug?

**No.** This is a **latent bug** in `parseGossipInfo()` that was exposed by restart testing. The bug would also trigger in any scenario where:

1. Something calls `getHostName()` on gossip endpoints (JMX getters, `system_views.gossip_info` virtual table query, etc.)
2. Then `parseGossipInfo()` is called

The restart itself is correct - it just creates conditions where `awaitGossipSchemaMatch()` is called after JMX getters have already resolved hostnames.

## The Fix

Use a regex pattern to explicitly match the `InetAddress.toString()` format: `[hostname]/ipaddress[:port]`

```java
// Pattern to match gossip endpoint lines
private static final Pattern GOSSIP_ENDPOINT_PATTERN =
    Pattern.compile("^[^\\s/]*/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$");

// Before (buggy):
if (line.startsWith("/"))

// After (fixed):
if (GOSSIP_ENDPOINT_PATTERN.matcher(line).matches())
```

**Pattern explanation:**
- `^` - start of line
- `[^\\s/]*` - optional hostname (any chars except whitespace or `/`)
- `/` - literal slash separator
- `\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}` - IPv4 address
- `(:\\d+)?` - optional port number
- `$` - end of line

**Matches:**
- `/127.0.0.1` ✓
- `localhost/127.0.0.1` ✓
- `localhost/127.0.0.1:7000` ✓
- `  generation:123` ✗ (key-value line, correctly rejected)

## Suggested Fix (Minimal Change)

```java
// Add pattern constant to class
private static final Pattern GOSSIP_ENDPOINT_PATTERN =
    Pattern.compile("^[^\\s/]*/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$");

// Updated method - only the endpoint detection line changes
private static Map<String, Map<String, String>> parseGossipInfo(String str)
{
    Map<String, Map<String, String>> map = new HashMap<>();
    String[] lines = str.split("\n");
    String currentInstance = null;
    for (String line : lines)
    {
        // Endpoint lines match pattern: [hostname]/ipaddress[:port]
        // Examples: "/127.0.0.1", "localhost/127.0.0.1", "localhost/127.0.0.1:7000"
        // Key-value lines are indented with spaces (e.g., "  generation:123")
        if (GOSSIP_ENDPOINT_PATTERN.matcher(line).matches())  // <-- FIXED
        {
            // start of new instance
            currentInstance = line;
            continue;
        }
        Objects.requireNonNull(currentInstance);  // unchanged
        String[] kv = line.trim().split(":", 2);
        assert kv.length == 2 : "When splitting line '" + line + "' expected 2 parts but not true";  // unchanged
        Map<String, String> state = map.computeIfAbsent(currentInstance, ignore -> new HashMap<>());
        state.put(kv[0], kv[1]);
    }
    return map;
}
```

## Reproduction

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant test-jvm-dtest-some \
    -Dtest.name=org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected \
    -Dtest.methods=testShutDownAndRestartInstances \
    -Dno-build-test=true \
    -Dtest.jvm.args="-Drestart.position=after_all_getters_test -Drestart.target=node -Drestart.mode=GRACEFUL"
```

## Affected Tests

| Test | Restart Position | Mode |
|------|-----------------|------|
| `JMXFeatureTest_RestartInjected.testShutDownAndRestartInstances` | `after_all_getters_test` | GRACEFUL |
| `JMXGetterCheckTest_RestartInjected.testGetters` | `after_getters_test` | GRACEFUL |

## Patch

**Location**: `dec20/patches/TEST-BUG-GROUP-11.patch`

```bash
git apply dec20/patches/TEST-BUG-GROUP-11.patch
```

## Git Blame

The code was added by David Capwell on 2020-12-15 (commit 5879813db7e).
