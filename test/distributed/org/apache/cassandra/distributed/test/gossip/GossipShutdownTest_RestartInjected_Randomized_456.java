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
package org.apache.cassandra.distributed.test.gossip;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.cassandra.distributed.api.Feature;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.TokenSupplier;
import org.apache.cassandra.distributed.shared.ClusterUtils;
import org.apache.cassandra.distributed.test.TestBaseImpl;
import org.apache.cassandra.net.Verb;

public class GossipShutdownTest_RestartInjected_Randomized_456 extends TestBaseImpl {

    @Test
    public void emptyStateAndShutdownEvent() throws IOException {
        try (Cluster cluster = builder().withNodes(2).withConfig(c -> c.with(Feature.values())).start()) {
            IInvokableInstance n1 = cluster.get(1);
            IInvokableInstance n2 = cluster.get(2);
            ClusterUtils.awaitRingJoin(n1, n2);
            ClusterUtils.awaitRingJoin(n2, n1);
            RestartFramework.at("after_ring_join").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            ClusterUtils.stopUnchecked(n1);
            // make sure we don't see gossip events, but do allow gossip shutdown
            cluster.filters().verbs(Verb.GOSSIP_DIGEST_ACK.id, Verb.GOSSIP_DIGEST_ACK.id, Verb.GOSSIP_DIGEST_ACK2.id).to(1).drop();
            RestartFramework.at("after_filter_setup").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            n1.startup();
            RestartFramework.at("after_node_startup").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            n2.nodetoolResult("disablegossip").asserts().success();
            RestartFramework.at("after_gossip_disable").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    @Test
    public void emptyStateAndHostJoin() throws IOException, InterruptedException {
        try (Cluster cluster = builder().withNodes(2).withTokenSupplier(TokenSupplier.evenlyDistributedTokens(3)).withConfig(c -> c.with(Feature.values())).start()) {
            IInvokableInstance n1 = cluster.get(1);
            IInvokableInstance n2 = cluster.get(2);
            ClusterUtils.awaitRingJoin(n1, n2);
            ClusterUtils.awaitRingJoin(n2, n1);
            RestartFramework.at("after_ring_join_host").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            ClusterUtils.stopUnchecked(n1);
            // make sure we don't see gossip events, but do allow gossip shutdown
            cluster.filters().verbs(Verb.GOSSIP_DIGEST_ACK.id, Verb.GOSSIP_DIGEST_ACK.id, Verb.GOSSIP_DIGEST_ACK2.id).to(1).drop();
            RestartFramework.at("after_filter_setup_host").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            n1.startup();
            RestartFramework.at("after_node_startup_host").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            CountDownLatch latch = new CountDownLatch(1);
            cluster.filters().verbs(Verb.GOSSIP_SHUTDOWN.id).messagesMatching((from, to, iMessage) -> {
                latch.countDown();
                return false;
            }).drop();
            n2.nodetoolResult("disablegossip").asserts().success();
            RestartFramework.at("after_gossip_disable_host").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            latch.await();
            cluster.filters().reset();
            RestartFramework.at("after_filter_reset").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInvokableInstance n3 = ClusterUtils.addInstance(cluster, n1.config(), c -> {
            });
            RestartFramework.at("after_node_add").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            n3.startup();
            RestartFramework.at("after_node3_startup").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }
}
