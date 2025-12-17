# DEBUG_TRACKER - CASSANDRA

This tracker lists failure groups ordered by likelihood of being actual bugs.
Groups are prioritized from most likely to be bugs to most likely to be false positives.

Total failure groups: 27

---

## Group 1 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 38

**Priority Reason:** Application exception - java.util.concurrent.RejectedExecutionException

### Generalized Stack Trace
```
java.util.concurrent.RejectedExecutionException
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:1365)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java:50)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:81)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:33)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java:150)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.execute(GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java:591)
	at org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.testCompositeClusteringKey(GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected.java:575)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testCompositeClusteringKey`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-586512fa`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testCompositeClusteringKey`
- Position: `after_first_insert`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `057-30c72039`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.guardrails.GuardrailItemsPerCollectionOnSSTableWriteTest_RestartInjected`
- Test Method: `testSetSizeFrozen`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `062-b50248ed`

---

## Group 2 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 18

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java)
	at com.datastax.driver.core.SessionManager.executeAsync(SessionManager.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:39)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected.testDisallowsNewRequests(DisableBinaryTest_RestartInjected.java:193)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java:63)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java:377)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java:141)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java:123)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java:707)
	at com.datastax.driver.core.SessionManager.executeAsync(SessionManager.java:144)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DisableBinaryTest_RestartInjected`
- Test Method: `testDisallowsNewRequests`
- Position: `after_transport_stop`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-a478f20a`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_initial_query`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `030-535dae58`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_enable_slow_select`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `031-14c36077`

---

## Group 3 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 12

**Priority Reason:** Test bug - java.lang.NullPointerException thrown from test code

### Generalized Stack Trace
```
java.lang.NullPointerException
	at java.base/java.util.UUID.fromString(UUID.java)
```

### Raw Stack Trace Sample
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null
	at java.base/java.util.UUID.fromString(UUID.java:237)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.doHintReappearData(HintDataReappearingTest_RestartInjected.java:279)
	at org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected.demonstrateHintCausesDataReappearanceWriteTimeout(HintDataReappearingTest_RestartInjected.java:90)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_pause_hints`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `003-6ea8e9de`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_insert_and_filter_reset`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-c3a515f0`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.HintDataReappearingTest_RestartInjected`
- Test Method: `demonstrateHintCausesDataReappearanceWriteTimeout`
- Position: `after_first_flush`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `005-d26ee023`

---

## Group 5 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 9

**Priority Reason:** Test bug - Caused by thrown from test code

### Generalized Stack Trace
```
Caused by: java.lang.NullPointerException
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java)
	at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_load_system_tables
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected.systemTables(TableMetricTest_RestartInjected.java:83)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.NullPointerException: Cannot invoke "java.util.Set.iterator()" because the return value of "org.apache.cassandra.utils.MBeanWrapper.queryNames(javax.management.ObjectName, javax.management.QueryExp)" is null
	at org.apache.cassandra.service.GCInspector.<init>(GCInspector.java:145)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.connect(InternalNodeProbe.java:79)
	at org.apache.cassandra.distributed.mock.nodetool.InternalNodeProbe.<init>(InternalNodeProbe.java:58)
	at org.apache.cassandra.distributed.impl.Instance$DTestNodeTool.<init>(Instance.java:1079)
	at org.apache.cassandra.distributed.impl.Instance.lambda$nodetoolResult$51(Instance.java:986)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `systemTables`
- Position: `after_load_system_tables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `067-b1422257`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `userTables`
- Position: `after_load_system_tables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `012-30bec6a1`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.metric.TableMetricTest_RestartInjected`
- Test Method: `userTables`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `013-334986c3`

---

## Group 9 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 3

**Priority Reason:** Test bug - Caused by thrown from test code

### Generalized Stack Trace
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_getters_test
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.jmx.JMXGetterCheckTest_RestartInjected.testGetters(JMXGetterCheckTest_RestartInjected.java:86)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java:209)
	at org.apache.cassandra.distributed.shared.ClusterUtils.parseGossipInfo(ClusterUtils.java:691)
	at org.apache.cassandra.distributed.shared.ClusterUtils.gossipInfo(ClusterUtils.java:655)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossip(ClusterUtils.java:554)
	at org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipSchemaMatch(ClusterUtils.java:600)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:85)
	at org.apache.cassandra.restart.CassandraClusterAdapter.waitActive(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:116)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXGetterCheckTest_RestartInjected`
- Test Method: `testGetters`
- Position: `after_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `059-acaf07f8`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected`
- Test Method: `testShutDownAndRestartInstances`
- Position: `after_all_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `010-901071ff`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.jmx.JMXFeatureTest_RestartInjected`
- Test Method: `testMultipleNetworkInterfacesProvisioning`
- Position: `after_all_getters_test`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-48962736`

---

## Group 14 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java)
	at com.datastax.driver.core.SessionManager.prepareAsync(SessionManager.java)
	at com.datastax.driver.core.AbstractSession.prepareAsync(AbstractSession.java)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:85)
	at com.datastax.driver.core.exceptions.NoHostAvailableException.copy(NoHostAvailableException.java:39)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java:88)
	at org.apache.cassandra.distributed.test.ReprepareNewBehaviourTest_RestartInjected.testUseWithMultipleKeyspaces(ReprepareNewBehaviourTest_RestartInjected.java:106)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed (no host was tried)
	at com.datastax.driver.core.RequestHandler.reportNoMoreHosts(RequestHandler.java:285)
	at com.datastax.driver.core.RequestHandler.access$1200(RequestHandler.java:63)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.findNextHostAndQuery(RequestHandler.java:377)
	at com.datastax.driver.core.RequestHandler.startNewExecution(RequestHandler.java:141)
	at com.datastax.driver.core.RequestHandler.sendRequest(RequestHandler.java:123)
	at com.datastax.driver.core.SessionManager.execute(SessionManager.java:707)
	at com.datastax.driver.core.SessionManager.prepareAsync(SessionManager.java:179)
	at com.datastax.driver.core.AbstractSession.prepareAsync(AbstractSession.java:106)
	at com.datastax.driver.core.AbstractSession.prepare(AbstractSession.java:86)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ReprepareNewBehaviourTest_RestartInjected`
- Test Method: `testUseWithMultipleKeyspaces`
- Position: `after_switch_ks2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `018-f85cd3b5`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.PrepareBatchStatementsTest_RestartInjected`
- Test Method: `testPreparedBatch`
- Position: `after_schema_creation`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `050-27008f8e`

---

## Group 16 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Application exception - java.lang.IllegalStateException

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java:58)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:65)
	at org.apache.cassandra.distributed.api.LogAction.watchFor(LogAction.java:137)
	at org.apache.cassandra.distributed.test.JVMDTestTest_RestartInjected.instanceLogs(JVMDTestTest_RestartInjected.java:111)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.JVMDTestTest_RestartInjected`
- Test Method: `instanceLogs`
- Position: `after_exception_trigger`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `062-7aca286b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected`
- Test Method: `compactionDoesNotCancelUpgradeSSTables`
- Position: `after_upgradesstables_test2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-7e61b015`

---

## Group 17 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Application exception - org.apache.cassandra.exceptions.UnavailableException

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.service.StorageProxy.findSuitableReplica(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateCounter(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ONE
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:31)
	at org.apache.cassandra.service.StorageProxy.findSuitableReplica(StorageProxy.java:1782)
	at org.apache.cassandra.service.StorageProxy.mutateCounter(StorageProxy.java:1725)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java:892)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java:1158)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java:529)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:502)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CountersTest_RestartInjected`
- Test Method: `testUpdateCounter`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `029-71a48fba`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CountersTest_RestartInjected`
- Test Method: `testUpdateCounterWithDroppedCompactStorage`
- Position: `after_counter_decrement`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-d7e603f0`

---

## Group 20 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.concurrent.RejectedExecutionException

### Generalized Stack Trace
```
java.util.concurrent.RejectedExecutionException
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithPagingWithResult(Coordinator.java)
	at org.apache.cassandra.distributed.api.ICoordinator.executeWithPaging(ICoordinator.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.RejectedExecutionException: isolatedExecutor has shut down
	at org.apache.cassandra.concurrent.ThreadPoolExecutorBase.lambda$static$0(ThreadPoolExecutorBase.java:49)
	at java.base/java.util.concurrent.ThreadPoolExecutor.reject(ThreadPoolExecutor.java:833)
	at java.base/java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:1365)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.addTask(ThreadPoolExecutorPlus.java:50)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:81)
	at org.apache.cassandra.concurrent.ThreadPoolExecutorPlus.submit(ThreadPoolExecutorPlus.java:33)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$6(IsolatedExecutor.java:150)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithPagingWithResult(Coordinator.java:180)
	at org.apache.cassandra.distributed.api.ICoordinator.executeWithPaging(ICoordinator.java:49)
	at org.apache.cassandra.distributed.test.GroupByTest_RestartInjected.testGroupWithDeletesAndPaging(GroupByTest_RestartInjected.java:204)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.GroupByTest_RestartInjected`
- Test Method: `testGroupWithDeletesAndPaging`
- Position: `after_inserts`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-899d8831`

---

## Group 21 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.cassandra.exceptions.UnavailableException

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.UnavailableException
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicasForWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java)
	at org.apache.cassandra.service.StorageProxy.performWrite(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.UnavailableException: Cannot achieve consistency level ALL
	at org.apache.cassandra.exceptions.UnavailableException.create(UnavailableException.java:37)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicas(ReplicaPlans.java:193)
	at org.apache.cassandra.locator.ReplicaPlans.assureSufficientLiveReplicasForWrite(ReplicaPlans.java:136)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:475)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:466)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:460)
	at org.apache.cassandra.locator.ReplicaPlans.forWrite(ReplicaPlans.java:449)
	at org.apache.cassandra.service.StorageProxy.performWrite(StorageProxy.java:1383)
	at org.apache.cassandra.service.StorageProxy.mutate(StorageProxy.java:894)
	at org.apache.cassandra.service.StorageProxy.mutateWithTriggers(StorageProxy.java:1158)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithoutCondition(ModificationStatement.java:529)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:502)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ring.NodeNotInRingTest_RestartInjected`
- Test Method: `nodeNotInRingTest`
- Position: `after_first_populate`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-9a74b21a`

---

## Group 23 (Priority 2)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - Caused by

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.await(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.AbstractFuture.getWhenDone(AbstractFuture.java:239)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:246)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.testDynamicContentionTracing(CASContentionTest_RestartInjected.java:139)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.Throwables.unchecked(Throwables.java:307)
	at org.apache.cassandra.utils.Throwables.throwAsUncheckedException(Throwables.java:318)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:281)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.lambda$testDynamicContentionTracing$2(CASContentionTest_RestartInjected.java:109)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java:361)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java:320)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.await(WaitQueue.java:299)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.await(Awaitable.java:306)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java:154)
	at org.apache.cassandra.utils.concurrent.AsyncFuture.await(AsyncFuture.java:46)
	at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:245)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:276)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_filter_reset`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `007-e3ac67f6`

---

## Group 4 (Priority 3)

**Status:** [ ] Not started

**Execution Count:** 10

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java)
	at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_verification
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.GossipSettlesTest_RestartInjected.testGossipSettles(GossipSettlesTest_RestartInjected.java:135)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 2
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: Unable to gossip with any peers
	at org.apache.cassandra.gms.Gossiper.doShadowRound(Gossiper.java:2075)
	at org.apache.cassandra.service.StorageService.checkForEndpointCollision(StorageService.java:866)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1181)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.GossipSettlesTest_RestartInjected`
- Test Method: `testGossipSettles`
- Position: `after_verification`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `052-132c22ff`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.gossip.GossipShutdownTest_RestartInjected`
- Test Method: `emptyStateAndShutdownEvent`
- Position: `after_filter_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `046-b98f1031`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.HintedHandoffAddRemoveNodesTest_RestartInjected`
- Test Method: `shouldStreamHintsDuringDecommission`
- Position: `after_final_decommissions`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `019-868ef345`

---

## Group 6 (Priority 3)

**Status:** [ ] Not started

**Execution Count:** 4

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: org.apache.cassandra.exceptions.ConfigurationException
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_successful_decommission
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected.testDecommissionAfterNodeRestart(DecommissionTest_RestartInjected.java:208)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: org.apache.cassandra.exceptions.ConfigurationException: This node was decommissioned and will not rejoin the ring unless -Dcassandra.override_decommission=true has been set, or all existing data is removed and the node is bootstrapped again
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1144)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_successful_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `045-76bbfef7`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintedHandoffAddRemoveNodesTest_RestartInjected`
- Test Method: `shouldAvoidHintTransferOnDecommission`
- Position: `after_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-da6c8e70`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.streaming.LCSStreamingKeepLevelTest_RestartInjected`
- Test Method: `testDecom`
- Position: `after_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `065-99f590ae`

---

## Group 10 (Priority 3)

**Status:** [ ] Not started

**Execution Count:** 3

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.IllegalStateException
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java)
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_stop_all_2
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected.hostReplacementOfDeadNodeAndOtherNodeStartsAfter(HostReplacementOfDownedClusterTest_RestartInjected.java:229)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.IllegalStateException: Instance is not running, so can not be shutdown
	at org.apache.cassandra.distributed.impl.AbstractCluster$Wrapper.shutdown(AbstractCluster.java:449)
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:140)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected`
- Test Method: `hostReplacementOfDeadNodeAndOtherNodeStartsAfter`
- Position: `after_stop_all_2`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-211b7df2`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.RestartTest_RestartInjected`
- Test Method: `test`
- Position: `after_both_nodes_shutdown`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `031-6043a934`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.hostreplacement.HostReplacementOfDownedClusterTest_RestartInjected`
- Test Method: `hostReplacementOfDeadNode`
- Position: `after_stop_all`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `004-b9fd3ca8`

---

## Group 24 (Priority 3)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Exception with restart framework involvement

### Generalized Stack Trace
```
Caused by: java.lang.RuntimeException
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_bootstrap
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.MigrationCoordinatorTest_RestartInjected.replaceNode(MigrationCoordinatorTest_RestartInjected.java:114)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: Cannot replace address with a node that is already bootstrapped
	at org.apache.cassandra.service.StorageService.prepareForReplacement(StorageService.java:734)
	at org.apache.cassandra.service.StorageService.prepareToJoin(StorageService.java:1159)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:1017)
	at org.apache.cassandra.service.StorageService.initServer(StorageService.java:947)
	at org.apache.cassandra.distributed.impl.Instance.lambda$startup$12(Instance.java:708)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.MigrationCoordinatorTest_RestartInjected`
- Test Method: `replaceNode`
- Position: `after_bootstrap`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-3d9de488`

---

## Group 7 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.apache.cassandra.exceptions.CasWriteTimeoutException
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.begin(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java)
	at org.apache.cassandra.service.StorageProxy.cas(StorageProxy.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithCondition(ModificationStatement.java)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java)
	at java.base/java.lang.Thread.run(Thread.java)
```

### Raw Stack Trace Sample
```
org.apache.cassandra.exceptions.CasWriteTimeoutException: CAS operation timed out: received 1 of 2 required responses after 0 contention retries
	at org.apache.cassandra.service.paxos.Paxos$MaybeFailure.markAndThrowAsTimeoutOrFailure(Paxos.java:565)
	at org.apache.cassandra.service.paxos.Paxos.begin(Paxos.java:1072)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java:677)
	at org.apache.cassandra.service.paxos.Paxos.cas(Paxos.java:636)
	at org.apache.cassandra.service.StorageProxy.cas(StorageProxy.java:327)
	at org.apache.cassandra.cql3.statements.ModificationStatement.executeWithCondition(ModificationStatement.java:542)
	at org.apache.cassandra.cql3.statements.ModificationStatement.execute(ModificationStatement.java:501)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testSucccessfulWriteDuringRangeMovementFollowedByRead`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `037-18a19bb5`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testSuccessfulWriteDuringRangeMovementFollowedByConflicting`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `037-f70c4759`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.CASTest_RestartInjected`
- Test Method: `testIncompleteWriteFollowedBySuccessfulWriteWithStaleRingDuringRangeMovementFollowedByRead`
- Position: `after_bootstrap_setup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `029-ff964b52`

---

## Group 8 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java)
	at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java)
	at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_first_insert_started
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected.testDynamicContentionTracing(CASContentionTest_RestartInjected.java:118)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: Failed to gracefully restart node 1
	at org.apache.cassandra.restart.CassandraClusterAdapter.performGracefulRestart(CassandraClusterAdapter.java:156)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:48)
	at org.apache.cassandra.restart.CassandraClusterAdapter.restartNode(CassandraClusterAdapter.java:28)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
Caused by: java.lang.RuntimeException: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@6ca42584 did not terminate on time
	at org.apache.cassandra.utils.Throwables.maybeFail(Throwables.java:79)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$48(Instance.java:945)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$async$10(IsolatedExecutor.java:156)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.util.concurrent.TimeoutException: org.apache.cassandra.concurrent.SEPExecutor@6ca42584 did not terminate on time
	at org.apache.cassandra.utils.ExecutorUtils.awaitTerminationUntil(ExecutorUtils.java:111)
	at org.apache.cassandra.utils.ExecutorUtils.awaitTermination(ExecutorUtils.java:100)
	at org.apache.cassandra.concurrent.Stage.shutdownAndWait(Stage.java:193)
	at org.apache.cassandra.distributed.impl.Instance.lambda$shutdown$42(Instance.java:915)
	at org.apache.cassandra.distributed.impl.Instance.lambda$parallelRun$52(Instance.java:1162)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_first_insert_started`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `005-62d6417a`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.CASContentionTest_RestartInjected`
- Test Method: `testDynamicContentionTracing`
- Position: `after_second_insert`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `006-e95f24a7`

**Test 3:**
- Test Class: `org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected`
- Test Method: `shouldExposeReadsAndWrites`
- Position: `after_async_queries`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `051-9491c198`

---

## Group 13 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.apache.cassandra.distributed.test.AbstractHintWindowTest.insertData(AbstractHintWindowTest.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with org.apache.cassandra.distributed.test.AbstractHintWindowTest was not fulfilled within 10 seconds.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.AbstractHintWindowTest.insertData(AbstractHintWindowTest.java:122)
	at org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected.testPersistentHintWindow(HintsPersistentWindowTest_RestartInjected.java:165)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected`
- Test Method: `testPersistentHintWindow`
- Position: `after_node_startup`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `009-b5edb33b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.HintsPersistentWindowTest_RestartInjected`
- Test Method: `testPersistentHintWindow`
- Position: `after_hints_size_assertion`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `010-11b54bd5`

---

## Group 15 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected was not fulfilled within 20 seconds.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.waitForSnapshot(SnapshotsTest_RestartInjected.java:358)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.waitForSnapshotPresent(SnapshotsTest_RestartInjected.java:345)
	at org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected.testSecondaryIndexCleanup(SnapshotsTest_RestartInjected.java:206)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected`
- Test Method: `testSecondaryIndexCleanup`
- Position: `after_snapshot`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `048-0453f216`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.SnapshotsTest_RestartInjected`
- Test Method: `testSnapshotsCleanupByTTL`
- Position: `after_snapshot`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `061-cb80856f`

---

## Group 19 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java)
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: Condition with alias 'Did not see stream running or timed out' didn't complete within 3 minutes because condition with org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected was not fulfilled.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:78)
	at org.awaitility.core.CallableCondition.await(CallableCondition.java:26)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:864)
	at org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.failureDueToSessionTimeout(StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected.java:87)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.util.concurrent.TimeoutException
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:101)
	at org.awaitility.core.Uninterruptibles.getUninterruptibly(Uninterruptibles.java:81)
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:101)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.streaming.StreamFailureLogsFailureDueToSessionTimeoutTest_RestartInjected`
- Test Method: `failureDueToSessionTimeout`
- Position: `after_trigger_streaming`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `036-63709395`

---

## Group 22 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: Can't load org.apache.cassandra.distributed.impl.FileLogAction$FileLineIterator. Instance class loader is already closed.
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClassInternal(InstanceClassLoader.java:118)
	at org.apache.cassandra.distributed.shared.InstanceClassLoader.loadClass(InstanceClassLoader.java:112)
	at org.apache.cassandra.distributed.impl.FileLogAction.match(FileLogAction.java:58)
	at org.apache.cassandra.distributed.api.LogAction.grep(LogAction.java:170)
	at org.apache.cassandra.distributed.api.LogAction.grep(LogAction.java:208)
	at org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected.upgradeSSTablesInterruptsOngoingCompaction(UpgradeSSTablesTest_RestartInjected.java:119)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	Suppressed: java.lang.RuntimeException: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.Throwables.maybeFail(Throwables.java:79)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java:543)
		at org.apache.cassandra.distributed.impl.AbstractCluster.close(AbstractCluster.java:1089)
		at org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected.upgradeSSTablesInterruptsOngoingCompaction(UpgradeSSTablesTest_RestartInjected.java:59)
	Caused by: java.util.concurrent.TimeoutException
		at org.apache.cassandra.utils.concurrent.AbstractFuture.get(AbstractFuture.java:253)
		at org.apache.cassandra.utils.FBUtilities.waitOnFutures(FBUtilities.java:535)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.UpgradeSSTablesTest_RestartInjected`
- Test Method: `upgradeSSTablesInterruptsOngoingCompaction`
- Position: `after_upgradesstables`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `056-e90052ff`

---

## Group 25 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java)
```

### Raw Stack Trace Sample
```
com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:87)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:25)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected.shouldExposeReadsAndWrites(QueriesTableTest_RestartInjected.java:125)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.exceptions.WriteTimeoutException.copy(WriteTimeoutException.java:115)
	at com.datastax.driver.core.Responses$Error.asException(Responses.java:177)
	at com.datastax.driver.core.RequestHandler$SpeculativeExecution.onSet(RequestHandler.java:653)
	at com.datastax.driver.core.Connection$Dispatcher.channelRead0(Connection.java:1292)
	at com.datastax.driver.core.Connection$Dispatcher.channelRead0(Connection.java:1210)
	at com.datastax.shaded.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.timeout.IdleStateHandler.channelRead(IdleStateHandler.java:286)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.handler.codec.ByteToMessageDecoder.fireChannelRead(ByteToMessageDecoder.java:346)
	at com.datastax.shaded.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:318)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	at com.datastax.driver.core.InboundTrafficMeter.channelRead(InboundTrafficMeter.java:40)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:166)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562)
	at com.datastax.shaded.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at com.datastax.shaded.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at com.datastax.shaded.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency LOCAL_ONE (1 replica were required but only 0 acknowledged the write)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java:91)
	at com.datastax.driver.core.Responses$Error$1.decode(Responses.java:69)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java:299)
	at com.datastax.driver.core.Message$ProtocolDecoder.decode(Message.java:270)
	at com.datastax.shaded.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:88)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.QueriesTableTest_RestartInjected`
- Test Method: `shouldExposeReadsAndWrites`
- Position: `after_table_create`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `050-86bc4cdf`

---

## Group 26 (Priority 4)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Timeout - inspect later

### Generalized Stack Trace
```
org.awaitility.core.ConditionTimeoutException
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java)
	at org.awaitility.core.AbstractHamcrestCondition.await(AbstractHamcrestCondition.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java)
```

### Raw Stack Trace Sample
```
org.awaitility.core.ConditionTimeoutException: org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected expected not an empty string but was "" within 1 minutes.
	at org.awaitility.core.ConditionAwaiter.await(ConditionAwaiter.java:165)
	at org.awaitility.core.AbstractHamcrestCondition.await(AbstractHamcrestCondition.java:86)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:895)
	at org.awaitility.core.ConditionFactory.until(ConditionFactory.java:601)
	at org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected.lambda$checkSnapshot$d36cbfdb$1(PreviewRepairSnapshotTest_RestartInjected.java:189)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.PreviewRepairSnapshotTest_RestartInjected`
- Test Method: `testSnapshotOfSStablesContainingMismatchingTokens`
- Position: `after_final_repair`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `009-9c8284de`

---

## Group 11 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
java.lang.RuntimeException
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java)
	at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
	at org.apache.cassandra.service.StorageService.unbootstrap(StorageService.java)
	at org.apache.cassandra.service.StorageService.decommission(StorageService.java)
```

### Raw Stack Trace Sample
```
java.lang.RuntimeException: simulated error in prepareUnbootstrapStreaming
	at org.apache.cassandra.distributed.test.DecommissionTest$BB.prepareUnbootstrapStreaming(DecommissionTest.java:208)
	at org.apache.cassandra.service.StorageService.prepareUnbootstrapStreaming(StorageService.java)
	at org.apache.cassandra.service.StorageService.unbootstrap(StorageService.java:5427)
	at org.apache.cassandra.service.StorageService.decommission(StorageService.java:5351)
	at org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected.lambda$testDecommissionAfterNodeRestart$81c80a4a$2(DecommissionTest_RestartInjected.java:192)
	at org.apache.cassandra.concurrent.FutureTask$2.call(FutureTask.java:124)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_failed_decommission`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `043-a7a8df70`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.DecommissionTest_RestartInjected`
- Test Method: `testDecommissionAfterNodeRestart`
- Position: `after_manual_restart`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `044-5be8ad0d`

---

## Group 12 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: com.datastax.driver.core.exceptions.TransportException
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java)
	at com.datastax.driver.core.Connection.defunct(Connection.java)
	at com.datastax.driver.core.Connection$Dispatcher.exceptionCaught(Connection.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireExceptionCaught(AbstractChannelHandlerContext.java)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.exceptionCaught(DefaultChannelPipeline.java)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at java.base/java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:396)
	at java.base/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:2073)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.testApplyClientBackpressure(OverloadTest_RestartInjected.java:154)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.applyClientBackpressure(OverloadTest_RestartInjected.java:72)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:40)
	at com.datastax.driver.core.exceptions.TransportException.copy(TransportException.java:26)
	at com.datastax.driver.core.DriverThrowables.propagateCause(DriverThrowables.java:37)
	at com.datastax.driver.core.DefaultResultSetFuture.getUninterruptibly(DefaultResultSetFuture.java:295)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:60)
	at com.datastax.driver.core.AbstractSession.execute(AbstractSession.java:41)
	at org.apache.cassandra.distributed.test.OverloadTest_RestartInjected.lambda$testApplyClientBackpressure$1(OverloadTest_RestartInjected.java:133)
	at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1768)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: com.datastax.driver.core.exceptions.TransportException: [/127.0.0.1:9042] Connection has been closed
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1460)
	at com.datastax.driver.core.Connection$ConnectionCloseFuture.force(Connection.java:1441)
	at com.datastax.driver.core.Connection.defunct(Connection.java:624)
	at com.datastax.driver.core.Connection$Dispatcher.exceptionCaught(Connection.java:1363)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:346)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:325)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.fireExceptionCaught(AbstractChannelHandlerContext.java:317)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline$HeadContext.exceptionCaught(DefaultChannelPipeline.java:1377)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:346)
	at com.datastax.shaded.netty.channel.AbstractChannelHandlerContext.invokeExceptionCaught(AbstractChannelHandlerContext.java:325)
	at com.datastax.shaded.netty.channel.DefaultChannelPipeline.fireExceptionCaught(DefaultChannelPipeline.java:907)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.handleReadException(AbstractNioByteChannel.java:125)
	at com.datastax.shaded.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:177)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650)
	at com.datastax.shaded.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562)
	at com.datastax.shaded.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at com.datastax.shaded.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at com.datastax.shaded.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `applyClientBackpressure`
- Position: `after_queries_submitted`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-4a5e2787`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.OverloadTest_RestartInjected`
- Test Method: `clientBackpressureDisabled`
- Position: `after_queries_submitted`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `032-263631b1`

---

## Group 18 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java)
	at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:122)
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:191)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected.readsDuringBootstrapTest(ReadsDuringBootstrapTest_RestartInjected.java:141)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: org.apache.cassandra.utils.concurrent.UncheckedInterruptedException: java.lang.InterruptedException
	at org.apache.cassandra.utils.Throwables.unchecked(Throwables.java:307)
	at org.apache.cassandra.utils.Throwables.throwAsUncheckedException(Throwables.java:318)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.waitOn(IsolatedExecutor.java:285)
	at org.apache.cassandra.distributed.impl.IsolatedExecutor.lambda$sync$7(IsolatedExecutor.java:151)
	at org.apache.cassandra.distributed.impl.Coordinator.executeWithResult(Coordinator.java:61)
	at org.apache.cassandra.distributed.api.ICoordinator.execute(ICoordinator.java:32)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected.lambda$readsDuringBootstrapTest$1(ReadsDuringBootstrapTest_RestartInjected.java:92)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireSharedInterruptibly(AbstractQueuedSynchronizer.java:1048)
	at java.base/java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
	at org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected$BB.getCachedReplicas(ReadsDuringBootstrapTest_RestartInjected.java:170)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getCachedReplicas(AbstractReplicationStrategy.java)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getNaturalReplicas(AbstractReplicationStrategy.java:98)
	at org.apache.cassandra.locator.AbstractReplicationStrategy.getNaturalReplicasForToken(AbstractReplicationStrategy.java:90)
	at org.apache.cassandra.locator.ReplicaLayout.forTokenReadLiveSorted(ReplicaLayout.java:331)
	at org.apache.cassandra.locator.ReplicaPlans.forRead(ReplicaPlans.java:723)
	at org.apache.cassandra.service.reads.AbstractReadExecutor.getReadExecutor(AbstractReadExecutor.java:196)
	at org.apache.cassandra.service.StorageProxy.fetchRows(StorageProxy.java:2119)
	at org.apache.cassandra.service.StorageProxy.readRegular(StorageProxy.java:2011)
	at org.apache.cassandra.service.StorageProxy.read(StorageProxy.java:1885)
	at org.apache.cassandra.db.SinglePartitionReadCommand$Group.execute(SinglePartitionReadCommand.java:1285)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:427)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:340)
	at org.apache.cassandra.cql3.statements.SelectStatement.execute(SelectStatement.java:108)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:67)
	at org.apache.cassandra.distributed.impl.CoordinatorHelper.unsafeExecuteInternal(CoordinatorHelper.java:47)
	at org.apache.cassandra.distributed.impl.Coordinator.unsafeExecuteInternal(Coordinator.java:94)
	at org.apache.cassandra.distributed.impl.Coordinator.lambda$executeWithResult$0(Coordinator.java:61)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected`
- Test Method: `readsDuringBootstrapTest`
- Position: `after_bootstrap_and_join`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `023-25a41f1b`

**Test 2:**
- Test Class: `org.apache.cassandra.distributed.test.ring.ReadsDuringBootstrapTest_RestartInjected`
- Test Method: `readsDuringBootstrapTest`
- Position: `after_cache_population`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `026-af60fba8`

---

## Group 27 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.awaitUntil(WaitQueue.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$Defaults.await(Awaitable.java)
	at org.apache.cassandra.utils.concurrent.Awaitable$AbstractAwaitable.await(Awaitable.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.ExecutionException: java.lang.RuntimeException: java.lang.InterruptedException
	at java.base/java.util.concurrent.FutureTask.report(FutureTask.java:122)
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:191)
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.testFinishingNonIntersectingIncRepairDuringPreview(PreviewRepairTest_RestartInjected.java:486)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
Caused by: java.lang.RuntimeException: java.lang.InterruptedException
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.lambda$repair$6323037e$1(PreviewRepairTest_RestartInjected.java:819)
	at org.apache.cassandra.concurrent.FutureTask.call(FutureTask.java:61)
	at org.apache.cassandra.concurrent.FutureTask.run(FutureTask.java:71)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.InterruptedException
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.checkInterrupted(WaitQueue.java:361)
	at org.apache.cassandra.utils.concurrent.WaitQueue$Standard$AbstractSignal.awaitUntil(WaitQueue.java:349)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java:314)
	at org.apache.cassandra.utils.concurrent.Awaitable$AsyncAwaitable.awaitUntil(Awaitable.java:346)
	at org.apache.cassandra.utils.concurrent.Awaitable$Defaults.await(Awaitable.java:114)
	at org.apache.cassandra.utils.concurrent.Awaitable$AbstractAwaitable.await(Awaitable.java:210)
	at org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected.lambda$repair$6323037e$1(PreviewRepairTest_RestartInjected.java:815)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.cassandra.distributed.test.PreviewRepairTest_RestartInjected`
- Test Method: `testFinishingNonIntersectingIncRepairDuringPreview`
- Position: `testNonIntersect_after_range_repair`
- Target: `node`
- Mode: `GRACEFUL`
- Execution Dir: `042-54060778`

---
