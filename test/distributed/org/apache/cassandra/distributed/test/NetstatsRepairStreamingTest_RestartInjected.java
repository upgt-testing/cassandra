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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.shared.NetworkTopology;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NATIVE_PROTOCOL;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class NetstatsRepairStreamingTest_RestartInjected extends AbstractNetstatsStreaming
{
    @Test
    public void testWithCompressionEnabled() throws Exception
    {
        executeTest(true);
    }

    @Test
    public void testWithCompressionDisabled() throws Exception
    {
        executeTest(false);
    }

    private void executeTest(boolean compressionEnabled) throws Exception
    {
        final ExecutorService executorService = Executors.newFixedThreadPool(1);

        try (final Cluster cluster = Cluster.build()
                                            .withNodeIdTopology(NetworkTopology.singleDcNetworkTopology(2, "dc0", "rack0"))
                                            .withConfig(config -> config.with(NETWORK, GOSSIP, NATIVE_PROTOCOL)
                                                                        .set("stream_throughput_outbound", "122KiB/s")
                                                                        .set("compaction_throughput", "1MiB/s")
                                                                        .set("stream_entire_sstables", false)).start())
        {
            final IInvokableInstance node1 = cluster.get(1);
            final IInvokableInstance node2 = cluster.get(2);

            createTable(cluster, 1, compressionEnabled);

            RestartFramework.at("after_table_create")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            node1.nodetoolResult("disableautocompaction", "netstats_test").asserts().success();
            node2.nodetoolResult("disableautocompaction", "netstats_test").asserts().success();

            RestartFramework.at("after_disable_autocompaction")
                .on(cluster)
                .restart("node")
                .withIndex(1)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            populateData(compressionEnabled);

            RestartFramework.at("after_populate_data")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            node1.flush("netstats_test");
            node2.flush("netstats_test");

            RestartFramework.at("after_flush")
                .on(cluster)
                .restart("node")
                .withIndex(1)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            changeReplicationFactor();

            RestartFramework.at("after_replication_factor_change")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            final Future<NetstatResults> resultsFuture1 = executorService.submit(new NetstatsCallable(node1));

            node1.nodetoolResult("repair", "netstats_test").asserts().success();

            RestartFramework.at("after_repair")
                .on(cluster)
                .restart("node")
                .withIndex(1)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            final NetstatResults results = resultsFuture1.get(1, MINUTES);

            results.assertSuccessful();

            NetstatsOutputParser.validate(NetstatsOutputParser.parse(results));
        }
    }
}
