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
package org.apache.cassandra.distributed.test.guardrails;

import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.apache.cassandra.db.guardrails.Guardrails;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

/**
 * Tests the guardrail for the number of tombstones in a partition, {@link Guardrails#partitionTombstones}.
 * <p>
 * This test only includes the activation of the guardrail during sstable writes, focusing on the emmitted log messages.
 * The tests for config, client warnings and diagnostic events are in
 * {@link org.apache.cassandra.db.guardrails.GuardrailPartitionTombstonesTest}.
 */
public class GuardrailPartitionTombstonesTest_RestartInjected_Randomized_123 extends GuardrailTester {

    // high enough to exceed system tables, which aren't excluded
    private static final int WARN_THRESHOLD = 500;

    private static final int FAIL_THRESHOLD = WARN_THRESHOLD * 2;

    private static Cluster cluster;

    @BeforeClass
    public static void setupCluster() throws IOException {
        cluster = init(Cluster.build(1).withConfig(c -> c.set("partition_tombstones_warn_threshold", WARN_THRESHOLD).set("partition_tombstones_fail_threshold", FAIL_THRESHOLD).set("memtable_heap_space", // avoids flushes
        "512MiB")).start());
        cluster.disableAutoCompaction(KEYSPACE);
    }

    @AfterClass
    public static void teardownCluster() {
        if (cluster != null)
            cluster.close();
    }

    @Override
    protected Cluster getCluster() {
        return cluster;
    }

    @Test
    public void testPartitionTombstones() {
        RestartFramework.at("after_dynamic_config_change").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // test yaml-loaded config
        testPartitionTombstones(WARN_THRESHOLD, FAIL_THRESHOLD);
        RestartFramework.at("after_dynamic_config_test").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        schemaChange("DROP TABLE %s");
        RestartFramework.at("after_table_drop").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // test dynamic config
        int warn = WARN_THRESHOLD + 10;
        int fail = FAIL_THRESHOLD + 10;
        cluster.get(1).runOnInstance(() -> Guardrails.instance.setPartitionTombstonesThreshold(warn, fail));
        RestartFramework.at("after_yaml_config_test").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        testPartitionTombstones(warn, fail);
    }

    private void testPartitionTombstones(int warn, int fail) {
        schemaChange("CREATE TABLE %s (k int, c int, v int, PRIMARY KEY (k, c))");
        RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // empty table
        assertNotWarnedOnFlush();
        RestartFramework.at("after_empty_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotWarnedOnCompact();
        RestartFramework.at("after_empty_compact").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // keep partition tombstones lower than thresholds
        populateTable(warn);
        RestartFramework.at("after_populate_below_threshold").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotWarnedOnFlush();
        RestartFramework.at("after_flush_below_threshold").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotWarnedOnCompact();
        RestartFramework.at("after_compact_below_threshold").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // exceed warn threshold
        populateTable(warn + 1);
        RestartFramework.at("after_populate_warn_threshold").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertWarnedOnFlush(expectedMessages(1));
        RestartFramework.at("after_warned_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertWarnedOnCompact(expectedMessages(1));
        RestartFramework.at("after_warned_compact").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // exceed fail threshold
        populateTable(fail + 1);
        RestartFramework.at("after_populate_fail_threshold").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertFailedOnFlush(expectedMessages(1));
        RestartFramework.at("after_failed_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertFailedOnCompact(expectedMessages(1));
        RestartFramework.at("after_failed_compact").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // remove most of the tombstones to be under the threshold again
        execute("DELETE FROM %s WHERE k = 1 AND c > 1");
        RestartFramework.at("after_delete_reduce_tombstones").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotWarnedOnFlush();
        RestartFramework.at("after_flush_post_delete").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNotWarnedOnCompact();
        RestartFramework.at("after_compact_post_delete").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // exceed warn threshold in multiple partitions,
        // this shouldn't trigger the fail threshold because the count is per-partition.
        for (int c = 0; c <= warn; c++) {
            execute("DELETE FROM %s WHERE k = 2 AND c = ?", c);
            execute("DELETE FROM %s WHERE k = 3 AND c = ?", c);
        }
        RestartFramework.at("after_multi_partition_tombstones").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertWarnedOnFlush(expectedMessages(2, 3));
        RestartFramework.at("after_multi_partition_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertWarnedOnCompact(expectedMessages(2, 3));
        RestartFramework.at("after_multi_partition_compact").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    private void populateTable(int numTombstones) {
        for (int c = 0; c < numTombstones; c++) {
            execute("DELETE FROM %s WHERE k = 1 AND c = ?", c);
        }
    }

    private void execute(String query, Object... args) {
        cluster.coordinator(1).execute(format(query), ConsistencyLevel.ALL, args);
    }

    private String[] expectedMessages(int... keys) {
        String[] messages = new String[keys.length];
        for (int i = 0; i < keys.length; i++) messages[i] = String.format("Guardrail partition_tombstones violated: Partition %s:%d", qualifiedTableName, keys[i]);
        return messages;
    }
}
