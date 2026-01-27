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
package org.apache.cassandra.distributed.test.streaming;

import java.io.IOException;
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.Feature;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class StreamFailureLogsFailureDueToSessionFailedTest_RestartInjected_Randomized_42 extends AbstractStreamFailureLogs {

    @Test
    public void failureDueToSessionFailed() throws IOException {
        streamTestWithRestart(true, "Remote peer /127.0.0.2:7012 failed stream session", 1);
    }

    protected void streamTestWithRestart(boolean zeroCopyStreaming, String reason, Integer failedNode) throws IOException {
        try (Cluster cluster = Cluster.build(2).withInstanceInitializer(BBStreamHelper::install).withConfig(c -> c.with(Feature.values()).set("stream_entire_sstables", zeroCopyStreaming).set("disk_failure_policy", "die")).start()) {
            init(cluster);
            cluster.schemaChange(withKeyspace("CREATE TABLE %s.tbl (pk int PRIMARY KEY)"));
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInvokableInstance node1 = cluster.get(1);
            IInvokableInstance node2 = cluster.get(2);
            for (int i = 0; i < 10; i++) node1.executeInternal(withKeyspace("INSERT INTO %s.tbl (pk) VALUES (?)"), i);
            RestartFramework.at("after_data_insert").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            node2.nodetoolResult("repair", "-full", KEYSPACE, "tbl").asserts().failure();
            RestartFramework.at("after_failed_repair").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            IInvokableInstance failingNode = cluster.get(failedNode);
            searchForLog(failingNode, reason);
            RestartFramework.at("after_log_search").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }
}
