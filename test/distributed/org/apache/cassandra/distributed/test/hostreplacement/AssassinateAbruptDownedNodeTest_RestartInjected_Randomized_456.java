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
package org.apache.cassandra.distributed.test.hostreplacement;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.Feature;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.TokenSupplier;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.BOOTSTRAP_SCHEMA_DELAY_MS;
import static org.apache.cassandra.config.CassandraRelevantProperties.BOOTSTRAP_SKIP_SCHEMA_CHECK;
import static org.apache.cassandra.distributed.shared.ClusterUtils.assertRingState;
import static org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipStatus;
import static org.apache.cassandra.distributed.shared.ClusterUtils.getBroadcastAddressHostWithPortString;
import static org.apache.cassandra.distributed.shared.ClusterUtils.getTokens;
import static org.apache.cassandra.distributed.shared.ClusterUtils.replaceHostAndStart;
import static org.apache.cassandra.distributed.shared.ClusterUtils.stopAbrupt;
import static org.apache.cassandra.distributed.test.hostreplacement.HostReplacementTest.setupCluster;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * If the operator attempts to assassinate the node before replacing it, this will cause the node to fail to start
 * as the status is non-normal.
 *
 * The node is removed abruptly before assassinate, leaving gossip without an empty entry.
 */
public class AssassinateAbruptDownedNodeTest_RestartInjected_Randomized_456 extends BaseAssassinatedCase {

    @Test
    @Override
    public void test() throws IOException {
        TokenSupplier even = TokenSupplier.evenlyDistributedTokens(3);
        try (Cluster cluster = Cluster.build(3).withConfig(c -> c.with(Feature.GOSSIP, Feature.NETWORK)).withTokenSupplier(node -> even.token(node == 4 || node == 5 ? NODE_TO_REMOVE_NUM : node)).start()) {
            IInvokableInstance seed = cluster.get(SEED_NUM);
            IInvokableInstance nodeToRemove = cluster.get(NODE_TO_REMOVE_NUM);
            IInvokableInstance peer = cluster.get(PEER_NUM);
            setupCluster(cluster);
            RestartFramework.at("after_cluster_setup").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            consume(cluster, nodeToRemove);
            RestartFramework.at("after_node_stop").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertRingState(seed, nodeToRemove, "Normal");
            RestartFramework.at("after_ring_state_check").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            peer.nodetoolResult("assassinate", getBroadcastAddressHostWithPortString(nodeToRemove)).asserts().success();
            RestartFramework.at("after_assassinate").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            awaitGossipStatus(seed, nodeToRemove, "LEFT");
            RestartFramework.at("after_gossip_status_left").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            afterNodeStatusIsLeft(cluster, nodeToRemove);
            RestartFramework.at("after_node_status_left_hook").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertThatThrownBy(() -> replaceHostAndStart(cluster, nodeToRemove, properties -> {
                properties.set(BOOTSTRAP_SKIP_SCHEMA_CHECK, true);
                properties.set(BOOTSTRAP_SCHEMA_DELAY_MS, 10);
            })).hasMessage(expectedMessage(nodeToRemove));
            RestartFramework.at("after_replace_attempt_failed").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    @Override
    void consume(Cluster cluster, IInvokableInstance nodeToRemove) {
        stopAbrupt(cluster, nodeToRemove);
    }

    @Override
    protected void afterNodeStatusIsLeft(Cluster cluster, IInvokableInstance removedNode) {
        // Check it is possible to alter keyspaces (see CASSANDRA-16422)
        // First, make sure the node is convicted so the gossiper considers it unreachable
        InetSocketAddress socketAddress = removedNode.config().broadcastAddress();
        InetAddressAndPort removedEndpoint = InetAddressAndPort.getByAddressOverrideDefaults(socketAddress.getAddress(), socketAddress.getPort());
        cluster.get(BaseAssassinatedCase.SEED_NUM).runOnInstance(() -> Gossiper.instance.convict(removedEndpoint, 1.0));
        // Second, try and alter the keyspace.  Before the bug was fixed, this would fail as the check includes
        // unreachable nodes that could have LEFT status.
        cluster.schemaChangeIgnoringStoppedInstances(String.format("ALTER KEYSPACE %s WITH DURABLE_WRITES = false", KEYSPACE));
    }
}
