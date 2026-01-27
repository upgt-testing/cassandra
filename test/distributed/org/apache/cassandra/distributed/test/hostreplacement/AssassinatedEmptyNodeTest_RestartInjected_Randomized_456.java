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
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.BOOTSTRAP_SCHEMA_DELAY_MS;
import static org.apache.cassandra.config.CassandraRelevantProperties.BOOTSTRAP_SKIP_SCHEMA_CHECK;
import static org.apache.cassandra.distributed.shared.ClusterUtils.assertGossipInfo;
import static org.apache.cassandra.distributed.shared.ClusterUtils.assertRingState;
import static org.apache.cassandra.distributed.shared.ClusterUtils.awaitGossipStatus;
import static org.apache.cassandra.distributed.shared.ClusterUtils.getBroadcastAddressHostWithPortString;
import static org.apache.cassandra.distributed.shared.ClusterUtils.replaceHostAndStart;
import static org.apache.cassandra.distributed.shared.ClusterUtils.stopAll;
import static org.apache.cassandra.distributed.test.hostreplacement.HostReplacementTest.setupCluster;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * If the operator attempts to assassinate the node before replacing it, this will cause the node to fail to start
 * as the status is non-normal.
 *
 * The cluster is put into the "empty" state for the node to remove.
 */
public class AssassinatedEmptyNodeTest_RestartInjected_Randomized_456 extends BaseAssassinatedCase {

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
            RestartFramework.at("after_node_stop_restart").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
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
    protected String expectedMessage(IInvokableInstance nodeToRemove) {
        return "Could not find tokens for " + nodeToRemove.config().broadcastAddress() + " to replace";
    }

    @Override
    void consume(Cluster cluster, IInvokableInstance nodeToRemove) {
        IInvokableInstance seed = cluster.get(SEED_NUM);
        IInvokableInstance peer = cluster.get(PEER_NUM);
        InetSocketAddress addressToReplace = nodeToRemove.broadcastAddress();
        stopAll(cluster);
        seed.startup();
        peer.startup();
        assertGossipInfo(seed, addressToReplace, 0, -1);
        assertGossipInfo(peer, addressToReplace, 0, -1);
    }
}
