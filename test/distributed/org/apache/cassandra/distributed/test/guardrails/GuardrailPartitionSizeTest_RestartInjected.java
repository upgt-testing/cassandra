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

import static java.nio.ByteBuffer.allocate;

/**
 * Tests the guardrail for the size of partition size, {@link Guardrails#partitionSize}.
 * <p>
 * This test only includes the activation of the guardrail during sstable writes, focusing on the emmitted log messages.
 * The tests for config, client warnings and diagnostic events are in
 * {@link org.apache.cassandra.db.guardrails.GuardrailPartitionSizeTest}.
 */
public class GuardrailPartitionSizeTest_RestartInjected extends GuardrailTester
{
    private static final int WARN_THRESHOLD = 1024 * 1024; // bytes (1 MiB)
    private static final int FAIL_THRESHOLD = WARN_THRESHOLD * 2; // bytes (2 MiB)

    private static final int NUM_NODES = 1;
    private static final int NUM_CLUSTERINGS = 5;

    private static Cluster cluster;

    @BeforeClass
    public static void setupCluster() throws IOException
    {
        cluster = init(Cluster.build(NUM_NODES)
                              .withConfig(c -> c.set("partition_size_warn_threshold", WARN_THRESHOLD + "B")
                                                .set("partition_size_fail_threshold", FAIL_THRESHOLD + "B")
                                                .set("memtable_heap_space", "512MiB")) // avoids flushes
                              .start());
        cluster.disableAutoCompaction(KEYSPACE);
    }

    @AfterClass
    public static void teardownCluster()
    {
        if (cluster != null)
            cluster.close();
    }

    @Override
    protected Cluster getCluster()
    {
        return cluster;
    }

    @Test
    public void testPartitionSize()
    {
        // test yaml-loaded config
        testPartitionSize(WARN_THRESHOLD, FAIL_THRESHOLD);

        RestartFramework.at("after_yaml_config_test")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        schemaChange("DROP TABLE %s");

        RestartFramework.at("after_table_drop")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // test dynamic config
        int warn = WARN_THRESHOLD * 2;
        int fail = FAIL_THRESHOLD * 2;
        cluster.get(1).runOnInstance(() -> Guardrails.instance.setPartitionSizeThreshold(warn + "B", fail + "B"));

        RestartFramework.at("after_dynamic_config_change")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        testPartitionSize(warn, fail);

        RestartFramework.at("after_dynamic_config_test")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    private void testPartitionSize(int warn, int fail)
    {
        schemaChange("CREATE TABLE %s (k int, c int, v blob, PRIMARY KEY (k, c))");

        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // empty table
        assertNotWarnedOnFlush();

        RestartFramework.at("after_empty_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertNotWarnedOnCompact();

        RestartFramework.at("after_empty_compact")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // keep partition size lower than thresholds
        execute("INSERT INTO %s (k, c, v) VALUES (1, 1, ?)", allocate(1));

        RestartFramework.at("after_insert_below_threshold")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertNotWarnedOnFlush();

        RestartFramework.at("after_flush_below_threshold")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertNotWarnedOnCompact();

        RestartFramework.at("after_compact_below_threshold")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // exceed warn threshold
        for (int c = 0; c < NUM_CLUSTERINGS; c++)
        {
            execute("INSERT INTO %s (k, c, v) VALUES (1, ?, ?)", c, allocate(warn / NUM_CLUSTERINGS));
        }

        RestartFramework.at("after_warn_threshold_inserts")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnFlush(expectedMessages(1));

        RestartFramework.at("after_warned_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnCompact(expectedMessages(1));

        RestartFramework.at("after_warned_compact")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // exceed fail threshold
        for (int c = 0; c < NUM_CLUSTERINGS * 10; c++)
        {
            execute("INSERT INTO %s (k, c, v) VALUES (1, ?, ?)", c, allocate(fail / NUM_CLUSTERINGS));
        }

        RestartFramework.at("after_fail_threshold_inserts")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertFailedOnFlush(expectedMessages(1));

        RestartFramework.at("after_failed_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertFailedOnCompact(expectedMessages(1));

        RestartFramework.at("after_failed_compact")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // remove most of the data to be under the threshold again
        execute("DELETE FROM %s WHERE k = 1 AND c > 1");

        RestartFramework.at("after_delete_reduce_size")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertNotWarnedOnFlush();

        RestartFramework.at("after_flush_post_delete")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertNotWarnedOnCompact();

        RestartFramework.at("after_compact_post_delete")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // exceed warn threshold in multiple partitions
        for (int c = 0; c < NUM_CLUSTERINGS; c++)
        {
            execute("INSERT INTO %s (k, c, v) VALUES (1, ?, ?)", c, allocate(warn / NUM_CLUSTERINGS));
            execute("INSERT INTO %s (k, c, v) VALUES (2, ?, ?)", c, allocate(warn / NUM_CLUSTERINGS));
        }

        RestartFramework.at("after_multi_partition_inserts")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnFlush(expectedMessages(1, 2));

        RestartFramework.at("after_multi_partition_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnCompact(expectedMessages(1, 2));

        RestartFramework.at("after_multi_partition_compact")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // exceed warn threshold in a new partition
        for (int c = 0; c < NUM_CLUSTERINGS; c++)
        {
            execute("INSERT INTO %s (k, c, v) VALUES (3, ?, ?)", c, allocate(warn / NUM_CLUSTERINGS));
        }

        RestartFramework.at("after_new_partition_insert")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnFlush(expectedMessages(3));

        RestartFramework.at("after_final_flush")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        assertWarnedOnCompact(expectedMessages(1, 2, 3));

        RestartFramework.at("after_final_compact")
            .on(cluster)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    private void execute(String query, Object... args)
    {
        cluster.coordinator(1).execute(format(query), ConsistencyLevel.ALL, args);
    }

    private String[] expectedMessages(int... keys)
    {
        String[] messages = new String[keys.length];
        for (int i = 0; i < keys.length; i++)
            messages[i] = String.format("Guardrail partition_size violated: Partition %s:%d", qualifiedTableName, keys[i]);
        return messages;
    }
}
