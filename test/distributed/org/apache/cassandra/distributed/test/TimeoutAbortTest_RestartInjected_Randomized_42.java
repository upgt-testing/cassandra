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
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.TEST_READ_ITERATION_DELAY_MS;
import static org.junit.Assert.assertFalse;
import static org.psjava.util.AssertStatus.assertTrue;

public class TimeoutAbortTest_RestartInjected_Randomized_42 extends TestBaseImpl {

    @Test
    public void timeoutTest() throws IOException, InterruptedException {
        TEST_READ_ITERATION_DELAY_MS.setInt(5000);
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange(withKeyspace("create table %s.tbl (id int, ck1 int, ck2 int, d int, primary key (id, ck1, ck2))"));
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.coordinator(1).execute(withKeyspace("delete from %s.tbl using timestamp 5 where id = 1 and ck1 = 77 "), ConsistencyLevel.ALL);
            RestartFramework.at("after_tombstone_delete").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).flush(KEYSPACE);
            RestartFramework.at("after_first_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            Thread.sleep(1000);
            for (int i = 0; i < 100; i++) cluster.coordinator(1).execute(withKeyspace("insert into %s.tbl (id, ck1, ck2, d) values (1,77,?,1) using timestamp 10"), ConsistencyLevel.ALL, i);
            RestartFramework.at("after_inserts").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).flush(KEYSPACE);
            RestartFramework.at("after_second_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            boolean caughtException = false;
            try {
                cluster.coordinator(1).execute(withKeyspace("select * from %s.tbl where id=1 and ck1 = 77"), ConsistencyLevel.ALL);
            } catch (Exception e) {
                assertTrue(e.getClass().getName().contains("ReadTimeoutException"));
                caughtException = true;
            }
            assertTrue(caughtException);
            List<String> errors = cluster.get(1).logs().grepForErrors().getResult();
            assertFalse(errors.toString(), errors.stream().anyMatch(s -> s.contains("open RT bound")));
        }
    }
}
