/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.distributed.test;

import java.io.IOException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.SimpleQueryResult;
import org.apache.cassandra.exceptions.OverloadedException;
import org.apache.cassandra.service.StorageService;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.apache.cassandra.config.ReplicaFilteringProtectionOptions.DEFAULT_FAIL_THRESHOLD;
import static org.apache.cassandra.config.ReplicaFilteringProtectionOptions.DEFAULT_WARN_THRESHOLD;
import static org.apache.cassandra.distributed.api.ConsistencyLevel.ALL;
import static org.apache.cassandra.distributed.shared.AssertUtils.assertRows;
import static org.apache.cassandra.distributed.shared.AssertUtils.row;
import static org.junit.Assert.assertEquals;

/**
 * Exercises the functionality of {@link org.apache.cassandra.service.reads.ReplicaFilteringProtection}, the
 * mechanism that ensures distributed index and filtering queries at read consistency levels > ONE/LOCAL_ONE
 * avoid stale replica results.
 */
public class ReplicaFilteringProtectionTest_RestartInjected extends TestBaseImpl
{
    private static final int REPLICAS = 2;
    private static final int PARTITIONS = 3;
    private static final int ROWS_PER_PARTITION = 3;

    private static Cluster cluster;

    @BeforeClass
    public static void setup() throws IOException
    {
        cluster = init(Cluster.build()
                              .withNodes(REPLICAS)
                              .withConfig(config -> config.set("hinted_handoff_enabled", false)).start());

        // Make sure we start w/ the correct defaults:
        cluster.get(1).runOnInstance(() -> assertEquals(DEFAULT_WARN_THRESHOLD, StorageService.instance.getCachedReplicaRowsWarnThreshold()));
        cluster.get(1).runOnInstance(() -> assertEquals(DEFAULT_FAIL_THRESHOLD, StorageService.instance.getCachedReplicaRowsFailThreshold()));
    }

    @AfterClass
    public static void teardown()
    {
        if (cluster != null)
            cluster.close();
    }

    @Test
    public void testMissedUpdatesBelowCachingWarnThreshold()
    {
        String tableName = "missed_updates_no_warning";
        cluster.schemaChange(withKeyspace("CREATE TABLE %s." + tableName + " (k int, c int, v text, PRIMARY KEY (k, c))"));
        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        testMissedUpdates(tableName, REPLICAS * ROWS_PER_PARTITION, Integer.MAX_VALUE, false);
    }

    @Test
    public void testMissedUpdatesAboveCachingWarnThreshold()
    {
        String tableName = "missed_updates_cache_warn";
        cluster.schemaChange(withKeyspace("CREATE TABLE %s." + tableName + " (k int, c int, v text, PRIMARY KEY (k, c))"));
        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        testMissedUpdates(tableName, REPLICAS * ROWS_PER_PARTITION - 1, Integer.MAX_VALUE, true);
    }

    @Test
    public void testMissedUpdatesAroundCachingFailThreshold()
    {
        String tableName = "missed_updates_cache_fail";
        cluster.schemaChange(withKeyspace("CREATE TABLE %s." + tableName + " (k int, c int, v text, PRIMARY KEY (k, c))"));
        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        testMissedUpdates(tableName, 1, REPLICAS * ROWS_PER_PARTITION, true);
        try
        {
            testMissedUpdates(tableName, 1, REPLICAS * ROWS_PER_PARTITION - 1, true);
        }
        catch (RuntimeException e)
        {
            assertEquals(e.getClass().getName(), OverloadedException.class.getName());
        }
    }

    private void testMissedUpdates(String tableName, int warnThreshold, int failThreshold, boolean shouldWarn)
    {
        cluster.get(1).runOnInstance(() -> StorageService.instance.setCachedReplicaRowsWarnThreshold(warnThreshold));
        cluster.get(1).runOnInstance(() -> StorageService.instance.setCachedReplicaRowsFailThreshold(failThreshold));
        RestartFramework.at("after_threshold_config")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        String fullTableName = KEYSPACE + '.' + tableName;
        for (int i = 0; i < PARTITIONS; i++)
            for (int j = 0; j < ROWS_PER_PARTITION; j++)
                cluster.coordinator(1).execute("INSERT INTO " + fullTableName + "(k, c, v) VALUES (?, ?, 'old')", ALL, i, j);
        RestartFramework.at("after_initial_inserts")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        long histogramSampleCount = rowsCachedPerQueryCount(cluster.get(1), tableName);
        String query = "SELECT * FROM " + fullTableName + " WHERE v = ? LIMIT ? ALLOW FILTERING";
        Object[][] initialRows = cluster.coordinator(1).execute(query, ALL, "old", PARTITIONS * ROWS_PER_PARTITION);
        assertRows(initialRows,
                   row(1, 0, "old"), row(1, 1, "old"), row(1, 2, "old"),
                   row(0, 0, "old"), row(0, 1, "old"), row(0, 2, "old"),
                   row(2, 0, "old"), row(2, 1, "old"), row(2, 2, "old"));
        assertEquals(histogramSampleCount + 1, rowsCachedPerQueryCount(cluster.get(1), tableName));
        RestartFramework.at("after_initial_query")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        updateAllRowsOn(1, fullTableName, "new");
        RestartFramework.at("after_first_update")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        SimpleQueryResult oldResult = cluster.coordinator(1).executeWithResult(query, ALL, "old", PARTITIONS * ROWS_PER_PARTITION);
        assertRows(oldResult.toObjectArrays());
        verifyWarningState(shouldWarn, oldResult);
        assertEquals(PARTITIONS, protectionQueryCount(cluster.get(1), tableName));
        assertEquals(PARTITIONS * REPLICAS, maxRowsCachedPerQuery(cluster.get(1), tableName));
        assertEquals(histogramSampleCount + 2, rowsCachedPerQueryCount(cluster.get(1), tableName));
        RestartFramework.at("after_divergent_query")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        SimpleQueryResult newResult = cluster.coordinator(1).executeWithResult(query, ALL, "new", PARTITIONS * ROWS_PER_PARTITION);
        Object[][] newRows = newResult.toObjectArrays();
        assertRows(newRows,
                   row(1, 0, "new"), row(1, 1, "new"), row(1, 2, "new"),
                   row(0, 0, "new"), row(0, 1, "new"), row(0, 2, "new"),
                   row(2, 0, "new"), row(2, 1, "new"), row(2, 2, "new"));
        verifyWarningState(warnThreshold < REPLICAS * ROWS_PER_PARTITION, newResult);
        assertEquals(PARTITIONS, protectionQueryCount(cluster.get(1), tableName));
        assertEquals(REPLICAS * ROWS_PER_PARTITION, minRowsCachedPerQuery(cluster.get(1), tableName));
        assertEquals(histogramSampleCount + 3, rowsCachedPerQueryCount(cluster.get(1), tableName));
        RestartFramework.at("after_read_repair")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        updateAllRowsOn(1, fullTableName, "future");
        RestartFramework.at("after_second_update")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        SimpleQueryResult futureResult = cluster.coordinator(1).executeWithResult(query, ALL, "future", PARTITIONS * ROWS_PER_PARTITION);
        Object[][] futureRows = futureResult.toObjectArrays();
        assertRows(futureRows,
                   row(1, 0, "future"), row(1, 1, "future"), row(1, 2, "future"),
                   row(0, 0, "future"), row(0, 1, "future"), row(0, 2, "future"),
                   row(2, 0, "future"), row(2, 1, "future"), row(2, 2, "future"));
        verifyWarningState(shouldWarn, futureResult);
        assertEquals(PARTITIONS * 2, protectionQueryCount(cluster.get(1), tableName));
        assertEquals(PARTITIONS * REPLICAS, maxRowsCachedPerQuery(cluster.get(1), tableName));
        assertEquals(histogramSampleCount + 4, rowsCachedPerQueryCount(cluster.get(1), tableName));
        RestartFramework.at("after_final_query")
            .on(cluster)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    private void updateAllRowsOn(int node, String table, String value)
    {
        for (int i = 0; i < PARTITIONS; i++)
            for (int j = 0; j < ROWS_PER_PARTITION; j++)
                cluster.get(node).executeInternal("UPDATE " + table + " SET v = ? WHERE k = ? and c = ?", value, i, j);
    }

    private void verifyWarningState(boolean shouldWarn, SimpleQueryResult futureResult)
    {
        List<String> futureWarnings = futureResult.warnings();
        assertEquals(shouldWarn, futureWarnings.stream().anyMatch(w -> w.contains("cached_replica_rows_warn_threshold")));
        assertEquals(shouldWarn ? 1 : 0, futureWarnings.size());
    }

    private long protectionQueryCount(IInvokableInstance instance, String tableName)
    {
        return instance.callOnInstance(() -> Keyspace.open(KEYSPACE)
                                                     .getColumnFamilyStore(tableName)
                                                     .metric.replicaFilteringProtectionRequests.getCount());
    }

    private long maxRowsCachedPerQuery(IInvokableInstance instance, String tableName)
    {
        return instance.callOnInstance(() -> Keyspace.open(KEYSPACE)
                                                     .getColumnFamilyStore(tableName)
                                                     .metric.rfpRowsCachedPerQuery.getSnapshot().getMax());
    }

    private long minRowsCachedPerQuery(IInvokableInstance instance, String tableName)
    {
        return instance.callOnInstance(() -> Keyspace.open(KEYSPACE)
                                                     .getColumnFamilyStore(tableName)
                                                     .metric.rfpRowsCachedPerQuery.getSnapshot().getMin());
    }

    private long rowsCachedPerQueryCount(IInvokableInstance instance, String tableName)
    {
        return instance.callOnInstance(() -> Keyspace.open(KEYSPACE)
                                                     .getColumnFamilyStore(tableName)
                                                     .metric.rfpRowsCachedPerQuery.getCount());
    }
}
