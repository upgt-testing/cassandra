# Build and Run Tests Guide

## Overview

This guide explains how to build the Cassandra distributed tests with restart injection and run them.

**Build System**: Apache Ant (using `build.xml` files)
**Test Framework**: JUnit with Cassandra's distributed test infrastructure
**Total Restart-Injected Tests**: 203 test files in `test/distributed/` module

## Prerequisites

- Apache Ant (version 1.10 or higher)
- **Java 17** (required - Java 11 will not work due to classpath version conflicts)
- Restart Testing Framework built at `/home/shuai/xlab/restart_testing/RestartTestingFramework`

## Build Instructions

### Step 1: Build the restart-adapter dependency

The distributed tests depend on the `restart-adapter` module, which must be built first.

```bash
cd restart-adapter
ant jar
```

**Output**: Creates JAR at `dist/cassandra-restart-adapter-5.0.6-SNAPSHOT.jar`

### Step 2: Build the distributed test module

From the Cassandra root directory:

```bash
cd /home/shuai/xlab/restart_testing/cassandra
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant build-test -Duse.jdk17=true
```

**Important**: You must set `JAVA_HOME` to Java 17 and use the `-Duse.jdk17=true` flag.

This compiles all test modules including the distributed tests. The build process will:
1. Automatically build the restart-adapter (if not already built)
2. Include the restart-adapter and restart-core JARs in the classpath
3. Compile all distributed tests including `*_RestartInjected.java` files

## Running Tests

### Run a specific test class

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant test-jvm-dtest-some -Duse.jdk17=true -Dtest.name=org.apache.cassandra.distributed.test.SimpleReadWriteTest_RestartInjected
```

**Example**: Run the `MessageForwardingTest_RestartInjected` test:

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant test-jvm-dtest-some -Duse.jdk17=true -Dtest.name=org.apache.cassandra.distributed.test.MessageForwardingTest_RestartInjected
```

### Run a specific test method (for parameterized tests)

**Note**: Many `*_RestartInjected` tests use `@RunWith(Parameterized.class)`, which means they run multiple test variants. The `-Dtest.methods` parameter may not work as expected for parameterized tests. It's usually better to run the entire test class.

For non-parameterized tests, you can try:

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant test-jvm-dtest-some \
  -Duse.jdk17=true \
  -Dtest.name=org.apache.cassandra.distributed.test.SomeTest_RestartInjected \
  -Dtest.methods=testSomeMethod
```

### Run all distributed tests

```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64
ant test-jvm-dtest -Duse.jdk17=true
```

**Warning**: This runs all 190+ restart-injected tests plus regular distributed tests and will take many hours!

## Test Configuration

- **Test timeout**: 900000ms (15 minutes) per test (configured in `build.xml`)
- **Test results**: Located in `build/test/output/` and `build/test/reports/`
- **Test logs**: Tests output detailed logs to stdout/stderr during execution

## Useful Ant Targets

- `ant -p` - List all available build targets
- `ant clean` - Clean build artifacts
- `ant build-test -Duse.jdk17=true` - Build all test modules with Java 17
- `ant test-jvm-dtest -Duse.jdk17=true` - Run all distributed tests
- `ant test-jvm-dtest-some -Duse.jdk17=true` - Run specific distributed test(s)
- `ant build-restart-adapter` - Build just the restart-adapter module

## Example Test Files

Some example restart-injected test files:

- `test/distributed/org/apache/cassandra/distributed/test/SimpleReadWriteTest_RestartInjected.java` (Parameterized)
- `test/distributed/org/apache/cassandra/distributed/test/RepairTest_RestartInjected.java`
- `test/distributed/org/apache/cassandra/distributed/test/streaming/StreamingMetricsTest_RestartInjected.java`
- `test/distributed/org/apache/cassandra/distributed/test/hostreplacement/HostReplacementTest_RestartInjected.java`

## Test Structure

Each `*_RestartInjected.java` test uses the Restart Testing Framework to inject restarts at specific checkpoints:

```java
RestartFramework.at("after_write")
    .on(cluster)
    .restart("node")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();
```

This injects a restart at the "after_write" checkpoint, gracefully restarting node 0 of the cluster.

## Build System Modifications

The build system has been modified to support restart-injected tests:

1. **Classpath additions** (`build.xml:360-367`): Added restart-adapter and restart-core JARs to test classpath
2. **Build dependency** (`build.xml:1021-1023`): Added `build-restart-adapter` target
3. **Test naming convention** (`build.xml:1104`): Updated to accept `*_RestartInjected` naming pattern

## Troubleshooting

1. **"Unsupported JDK version used: 1.8"**: You need to set `JAVA_HOME` to Java 17 and use `-Duse.jdk17=true`
2. **"package org.restarttest.api does not exist"**: The restart-adapter or restart-core isn't in the classpath. Rebuild with `ant build-test -Duse.jdk17=true`
3. **"class file has wrong version 61.0, should be 55.0"**: You're mixing Java versions. Use Java 17 consistently.
4. **Test timeouts**: Increase timeout with `-Dtest.distributed.timeout=<milliseconds>`
5. **Constructor name mismatch errors**: The constructor name must match the class name exactly, including `_RestartInjected` suffix

## Quick Start Example

```bash
# Set Java 17
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64

# 1. Build restart-adapter (optional - build-test will do this automatically)
cd restart-adapter
ant jar

# 2. Build all tests
cd ..
ant build-test -Duse.jdk17=true

# 3. Run a simple test
ant test-jvm-dtest-some -Duse.jdk17=true -Dtest.name=org.apache.cassandra.distributed.test.SimpleReadWriteTest_RestartInjected

# 4. Run just one test from a specific subdirectory
ant test-jvm-dtest-some -Duse.jdk17=true -Dtest.name=org.apache.cassandra.distributed.test.streaming.StreamingMetricsTest_RestartInjected
```

## Summary of Required Changes

To get all 203 restart-injected tests working, the following changes were made:

1. **Fixed constructor names** (2 files): Test files had constructors that didn't match their class names
   - `RepairCoordinatorFailingMessageTest_RestartInjected.java:57`
   - `TopPartitionsTest_RestartInjected.java:65`

2. **Fixed method references** (3 files): Method references used old class names without `_RestartInjected` suffix
   - `PaxosRepair2Test_RestartInjected.java:388`
   - `PaxosRepairTest_RestartInjected.java:446, 752`
   - `ResourceLeakTest_RestartInjected.java:279`

3. **Fixed method visibility** (1 file): Made private method public for method reference usage
   - `PaxosRepairTest_RestartInjected.java:703`

4. **Modified `build.xml`**:
   - Added restart-adapter and restart-core JARs to test classpath
   - Added `build-restart-adapter` target
   - Updated test naming convention regex to accept `*_RestartInjected` pattern

**Result**: All 203 tests now compile and build successfully!

See `FIXES_SUMMARY.md` for detailed information about each fix.

All tested commands have been verified to work successfully!
