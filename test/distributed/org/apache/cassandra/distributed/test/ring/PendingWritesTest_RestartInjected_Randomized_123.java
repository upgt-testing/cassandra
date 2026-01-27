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
package org.apache.cassandra.distributed.test.ring;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInstanceConfig;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.TokenSupplier;
import org.apache.cassandra.distributed.impl.DistributedTestSnitch;
import org.apache.cassandra.distributed.shared.NetworkTopology;
import org.apache.cassandra.distributed.test.TestBaseImpl;
import org.apache.cassandra.locator.EndpointsForRange;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.service.PendingRangeCalculatorService;
import org.apache.cassandra.service.StorageService;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.JOIN_RING;
import static org.apache.cassandra.distributed.action.GossipHelper.bootstrap;
import static org.apache.cassandra.distributed.action.GossipHelper.disseminateGossipState;
import static org.apache.cassandra.distributed.action.GossipHelper.statusToBootstrap;
import static org.apache.cassandra.distributed.action.GossipHelper.withProperty;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class PendingWritesTest_RestartInjected_Randomized_123 extends TestBaseImpl {

    @Test
    public void testPendingWrites() throws Throwable {
        int originalNodeCount = 2;
        int expandedNodeCount = originalNodeCount + 1;
        try (Cluster cluster = builder().withNodes(originalNodeCount).withTokenSupplier(TokenSupplier.evenlyDistributedTokens(expandedNodeCount)).withNodeIdTopology(NetworkTopology.singleDcNetworkTopology(expandedNodeCount, "dc0", "rack0")).withConfig(config -> config.with(NETWORK, GOSSIP)).start()) {
            BootstrapTest.populate(cluster, 0, 100);
            RestartFramework.at("after_initial_populate").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInstanceConfig config = cluster.newInstanceConfig();
            IInvokableInstance newInstance = cluster.bootstrap(config);
            withProperty(JOIN_RING, false, () -> newInstance.startup(cluster));
            RestartFramework.at("after_bootstrap_startup").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(statusToBootstrap(newInstance));
            RestartFramework.at("after_status_to_bootstrap").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.run(bootstrap(false, Duration.ofSeconds(60), Duration.ofSeconds(60)), newInstance.config().num());
            RestartFramework.at("after_bootstrap_run").on(cluster).restart("node").withIndex(2).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).acceptsOnInstance((InetSocketAddress ip) -> {
                Set<InetAddressAndPort> set = new HashSet<>();
                for (Map.Entry<Range<Token>, EndpointsForRange.Builder> e : StorageService.instance.getTokenMetadata().getPendingRanges(KEYSPACE)) {
                    set.addAll(e.getValue().build().endpoints());
                }
                Assert.assertEquals(set.size(), 1);
                Assert.assertTrue(String.format("%s should contain %s", set, ip), set.contains(DistributedTestSnitch.toCassandraInetAddressAndPort(ip)));
            }).accept(cluster.get(3).broadcastAddress());
            RestartFramework.at("after_pending_ranges_verification").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            BootstrapTest.populate(cluster, 100, 150);
            RestartFramework.at("after_second_populate").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            newInstance.nodetoolResult("join").asserts().success();
            RestartFramework.at("after_node_join").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            cluster.run(disseminateGossipState(newInstance), 1, 2);
            RestartFramework.at("after_gossip_dissemination").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.run((instance) -> {
                instance.runOnInstance(() -> {
                    PendingRangeCalculatorService.instance.update();
                    PendingRangeCalculatorService.instance.blockUntilFinished();
                });
            }, 1, 2);
            RestartFramework.at("after_pending_range_update").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).acceptsOnInstance((InetSocketAddress ip) -> {
                Set<InetAddressAndPort> set = new HashSet<>();
                for (Map.Entry<Range<Token>, EndpointsForRange.Builder> e : StorageService.instance.getTokenMetadata().getPendingRanges(KEYSPACE)) set.addAll(e.getValue().build().endpoints());
                assert set.size() == 0 : set;
            }).accept(cluster.get(3).broadcastAddress());
            for (Map.Entry<Integer, Long> e : BootstrapTest.count(cluster).entrySet()) Assert.assertEquals("Node " + e.getKey() + " has incorrect row state", e.getValue().longValue(), 150L);
        }
    }
}
