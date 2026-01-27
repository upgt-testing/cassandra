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
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.distributed.shared.AssertUtils.assertRows;
import static org.apache.cassandra.distributed.shared.AssertUtils.row;

public class DistributedRowUtilTest_RestartInjected_Randomized_42 extends TestBaseImpl {

    @Test
    public void correctColumnsReturnedForOrderedResults() throws IOException {
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange(withKeyspace("CREATE TABLE %s.t (k int, c int, v int, primary key(k, c))"));
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.coordinator(1).execute(withKeyspace("INSERT INTO %s.t (k, c, v) VALUES (0, 1, 2)"), ConsistencyLevel.QUORUM);
            RestartFramework.at("after_insert").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            String query = withKeyspace("SELECT v FROM %s.t WHERE k IN (0, 1) ORDER BY c LIMIT 10");
            assertRows(cluster.coordinator(1).execute(query, ConsistencyLevel.QUORUM), row(2));
            RestartFramework.at("after_query").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }
}
