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

import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.api.ICoordinator;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NATIVE_PROTOCOL;
import static org.apache.cassandra.distributed.shared.AssertUtils.assertRows;
import static org.apache.cassandra.distributed.shared.AssertUtils.row;

public class CountersTest_RestartInjected_Randomized_456 extends TestBaseImpl {

    @Test
    public void testUpdateCounter() throws Throwable {
        testUpdateCounter(false);
    }

    @Test
    public void testUpdateCounterWithDroppedCompactStorage() throws Throwable {
        testUpdateCounter(true);
    }

    private static void testUpdateCounter(boolean droppedCompactStorage) throws Throwable {
        try (Cluster cluster = Cluster.build(2).withConfig(c -> c.with(GOSSIP, NATIVE_PROTOCOL).set("drop_compact_storage_enabled", true)).start()) {
            cluster.schemaChange("CREATE KEYSPACE k WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
            RestartFramework.at("after_keyspace_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            String createTable = "CREATE TABLE k.t ( k int, c int, total counter, PRIMARY KEY (k, c))";
            if (droppedCompactStorage) {
                cluster.schemaChange(createTable + " WITH COMPACT STORAGE");
                cluster.schemaChange("ALTER TABLE k.t DROP COMPACT STORAGE");
                RestartFramework.at("after_drop_compact_storage").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            } else {
                cluster.schemaChange(createTable);
                RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            }
            ConsistencyLevel cl = ConsistencyLevel.ONE;
            String select = "SELECT total FROM k.t WHERE k = 1 AND c = ?";
            for (int i = 1; i <= cluster.size(); i++) {
                ICoordinator coordinator = cluster.coordinator(i);
                coordinator.execute("UPDATE k.t SET total = total + 1 WHERE k = 1 AND c = ?", cl, i);
                assertRows(coordinator.execute(select, cl, i), row(1L));
                RestartFramework.at("after_counter_increment").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                coordinator.execute("UPDATE k.t SET total = total - 4 WHERE k = 1 AND c = ?", cl, i);
                assertRows(coordinator.execute(select, cl, i), row(-3L));
                RestartFramework.at("after_counter_decrement").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            }
        }
    }
}
