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

import com.datastax.driver.core.Session;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.api.Feature;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.NodeToolResult;
import org.apache.cassandra.distributed.api.QueryResults;
import org.apache.cassandra.distributed.api.SimpleQueryResult;
import org.apache.cassandra.distributed.shared.AssertUtils;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class ClientNetworkStopStartTest_RestartInjected extends TestBaseImpl
{
    /**
     * @see <a href="https://issues.apache.org/jira/browse/CASSANDRA-16127">CASSANDRA-16127</a>
     */
    @Test
    public void stopStartNative() throws IOException
    {
        //TODO why does trunk need GOSSIP for native to work but no other branch does?
        try (Cluster cluster = init(Cluster.build(1).withConfig(c -> c.with(Feature.GOSSIP, Feature.NATIVE_PROTOCOL)).start()))
        {
            IInvokableInstance node = cluster.get(1);
            assertTransportStatus(node, "binary", true);
            node.nodetoolResult("disablebinary").asserts().success();
            assertTransportStatus(node, "binary", false);
            RestartFramework.at("after_disable_binary")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
            node.nodetoolResult("enablebinary").asserts().success();
            assertTransportStatus(node, "binary", true);
            RestartFramework.at("after_enable_binary")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            // now use it to make sure it still works!
            cluster.schemaChange("CREATE TABLE " + KEYSPACE + ".tbl (pk int, value int, PRIMARY KEY (pk))");
            RestartFramework.at("after_table_create")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            try (com.datastax.driver.core.Cluster client = com.datastax.driver.core.Cluster.builder().addContactPoints(node.broadcastAddress().getAddress()).build();
                 Session session = client.connect())
            {
                session.execute("INSERT INTO " + KEYSPACE + ".tbl (pk, value) VALUES (?, ?)", 0, 0);
            }
            RestartFramework.at("after_native_insert")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            SimpleQueryResult qr = cluster.coordinator(1).executeWithResult("SELECT * FROM " + KEYSPACE + ".tbl", ConsistencyLevel.ALL);
            AssertUtils.assertRows(qr, QueryResults.builder().row(0, 0).build());
            RestartFramework.at("after_query")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }

    private static void assertTransportStatus(IInvokableInstance node, String transport, boolean running)
    {
        assertNodetoolStdout(node, running ? "running" : "not running", running ? "not running" : null, "status" + transport);
    }

    private static void assertNodetoolStdout(IInvokableInstance node, String expectedStatus, String notExpected, String... nodetool)
    {
        NodeToolResult result = node.nodetoolResult(nodetool);
        result.asserts().success().stdoutContains(expectedStatus);
        if (notExpected != null)
            result.asserts().stdoutNotContains(notExpected);
    }
}
