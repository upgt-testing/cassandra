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
import java.util.LongSummaryStatistics;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.utils.FBUtilities;
import org.hamcrest.Matchers;
import static org.apache.cassandra.cql3.TombstonesWithIndexedSSTableTest.makeRandomString;
import static org.junit.Assert.assertThat;

public class UnifiedCompactionDensitiesTest_RestartInjected_Randomized_456 extends TestBaseImpl {

    @Test
    public void testTargetSSTableSize1Node1Dir() throws IOException {
        testTargetSSTableSize(1, 1);
    }

    @Test
    public void testTargetSSTableSize1Node2Dirs() throws IOException {
        testTargetSSTableSize(1, 2);
    }

    @Test
    public void testTargetSSTableSize2Nodes1Dir() throws IOException {
        testTargetSSTableSize(2, 1);
    }

    @Test
    public void testTargetSSTableSize2Nodes3Dirs() throws IOException {
        testTargetSSTableSize(2, 3);
    }

    private void testTargetSSTableSize(int nodeCount, int dataDirs) throws IOException {
        try (Cluster cluster = init(builder().withNodes(nodeCount).withDataDirCount(dataDirs).withConfig(cfg -> cfg.set("memtable_heap_space", "100MiB")).start())) {
            cluster.schemaChange(withKeyspace("alter keyspace %s with replication = {'class': 'SimpleStrategy', 'replication_factor':1}"));
            RestartFramework.at("after_keyspace_alter").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChange(withKeyspace("create table %s.tbl (id bigint primary key, value text) with compaction = {'class':'UnifiedCompactionStrategy', " + "'target_sstable_size' : '1MiB', " + "'min_sstable_size' : '0B', " + "'sstable_growth': '0'}"));
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            long targetSize = 1L << 20;
            // Size must be within sqrt(0.5), sqrt(2) of target, use 1.6 to account for estimations
            long targetMin = targetSize * 10 / 16;
            long targetMax = targetSize * 16 / 10;
            // 8 MiB per data directory, to be guaranteed to be over the 1MiB target size, and also different from the base shard count
            long toWrite = targetSize * nodeCount * dataDirs * 8;
            int payloadSize = 1024;
            cluster.forEach(x -> x.nodetool("disableautocompaction"));
            RestartFramework.at("after_disable_autocompaction").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // The first flush will not have the flush size metric initialized, so first check distribution after compaction.
            int i = 0;
            for (; i < 2; ++i) {
                writeData(cluster, i * toWrite, toWrite, payloadSize);
                cluster.forEach(x -> x.flush(KEYSPACE));
            }
            RestartFramework.at("after_first_write_flush_cycle").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(x -> x.forceCompact(KEYSPACE, "tbl"));
            RestartFramework.at("after_first_compaction").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            checkSSTableSizes(nodeCount, cluster, targetMin, targetMax);
            RestartFramework.at("after_first_size_check").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Now check that the sstables created by flushes are of the right size.
            for (; i < 2; ++i) {
                writeData(cluster, i * toWrite, toWrite, payloadSize);
                cluster.forEach(x -> x.flush(KEYSPACE));
            }
            RestartFramework.at("after_second_write_flush_cycle").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            checkSSTableSizes(nodeCount, cluster, targetMin, targetMax);
            RestartFramework.at("after_second_size_check").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // Compact again, as this time there will be independent buckets whose splitting must also work correctly.
            cluster.forEach(x -> x.forceCompact(KEYSPACE, "tbl"));
            RestartFramework.at("after_second_compaction").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            checkSSTableSizes(nodeCount, cluster, targetMin, targetMax);
            RestartFramework.at("after_final_size_check").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    private static void writeData(Cluster cluster, long offset, long toWrite, int payloadSize) {
        for (int i = 0; i < toWrite; i += payloadSize) cluster.coordinator(1).execute(withKeyspace("insert into %s.tbl (id, value) values (?, ?)"), ConsistencyLevel.ONE, i + offset, makeRandomString(payloadSize));
    }

    private void checkSSTableSizes(int nodeCount, Cluster cluster, long targetMin, long targetMax) {
        for (int i = 1; i <= nodeCount; ++i) {
            LongSummaryStatistics stats = cluster.get(i).callOnInstance(() -> {
                ColumnFamilyStore cfs = Keyspace.open(KEYSPACE).getColumnFamilyStore("tbl");
                return cfs.getLiveSSTables().stream().mapToLong(SSTableReader::onDiskLength).summaryStatistics();
            });
            long sstableCount = stats.getCount();
            long minSize = stats.getMin();
            long maxSize = stats.getMax();
            LoggerFactory.getLogger(getClass()).info("Node {} sstables {} min/max size: {}/{} avg {} total {}", i, sstableCount, FBUtilities.prettyPrintMemory(minSize), FBUtilities.prettyPrintMemory(maxSize), FBUtilities.prettyPrintBinary(stats.getAverage(), "", "B"), FBUtilities.prettyPrintMemory(stats.getSum()));
            assertThat(sstableCount, Matchers.greaterThan(0L));
            assertThat(minSize, Matchers.greaterThan(targetMin));
            assertThat(maxSize, Matchers.lessThan(targetMax));
        }
    }
}
