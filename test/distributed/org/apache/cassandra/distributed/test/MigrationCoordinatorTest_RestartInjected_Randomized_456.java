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

import java.net.InetAddress;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInstanceConfig;
import org.apache.cassandra.distributed.api.TokenSupplier;
import org.apache.cassandra.distributed.shared.NetworkTopology;
import org.apache.cassandra.distributed.shared.WithProperties;
import org.apache.cassandra.schema.Schema;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.BROADCAST_INTERVAL_MS;
import static org.apache.cassandra.config.CassandraRelevantProperties.IGNORED_SCHEMA_CHECK_ENDPOINTS;
import static org.apache.cassandra.config.CassandraRelevantProperties.IGNORED_SCHEMA_CHECK_VERSIONS;
import static org.apache.cassandra.config.CassandraRelevantProperties.REPLACE_ADDRESS;
import static org.apache.cassandra.config.CassandraRelevantProperties.RING_DELAY;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class MigrationCoordinatorTest_RestartInjected_Randomized_456 extends TestBaseImpl {

    private static WithProperties withProperties;

    @Before
    public void setUp() {
        withProperties = new WithProperties();
        withProperties.set(RING_DELAY, 5000);
        withProperties.set(BROADCAST_INTERVAL_MS, 30000);
    }

    @After
    public void afterTestCleanup() {
        withProperties.close();
    }

    /**
     * We shouldn't wait on versions only available from a node being replaced
     * see CASSANDRA-
     */
    @Test
    public void replaceNode() throws Throwable {
        try (Cluster cluster = Cluster.build(2).withTokenSupplier(TokenSupplier.evenlyDistributedTokens(3)).withNodeIdTopology(NetworkTopology.singleDcNetworkTopology(3, "dc0", "rack0")).withConfig(config -> config.with(NETWORK, GOSSIP)).start()) {
            cluster.schemaChange("CREATE KEYSPACE ks with replication={'class':'SimpleStrategy', 'replication_factor':2}");
            RestartFramework.at("after_keyspace_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            InetAddress replacementAddress = cluster.get(2).broadcastAddress().getAddress();
            cluster.get(2).shutdown(false);
            RestartFramework.at("after_node_shutdown").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChangeIgnoringStoppedInstances("CREATE TABLE ks.tbl (k int primary key, v int)");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInstanceConfig config = cluster.newInstanceConfig();
            config.set("auto_bootstrap", true);
            withProperties.set(REPLACE_ADDRESS, replacementAddress.getHostAddress());
            cluster.bootstrap(config).startup();
            RestartFramework.at("after_bootstrap").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    @Test
    public void explicitEndpointIgnore() throws Throwable {
        try (Cluster cluster = Cluster.build(2).withTokenSupplier(TokenSupplier.evenlyDistributedTokens(3)).withNodeIdTopology(NetworkTopology.singleDcNetworkTopology(3, "dc0", "rack0")).withConfig(config -> config.with(NETWORK, GOSSIP)).start()) {
            cluster.schemaChange("CREATE KEYSPACE ks with replication={'class':'SimpleStrategy', 'replication_factor':2}");
            RestartFramework.at("after_keyspace_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            InetAddress ignoredEndpoint = cluster.get(2).broadcastAddress().getAddress();
            cluster.get(2).shutdown(false);
            RestartFramework.at("after_node_shutdown").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChangeIgnoringStoppedInstances("CREATE TABLE ks.tbl (k int primary key, v int)");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInstanceConfig config = cluster.newInstanceConfig();
            config.set("auto_bootstrap", true);
            withProperties.set(IGNORED_SCHEMA_CHECK_ENDPOINTS, ignoredEndpoint.getHostAddress());
            cluster.bootstrap(config).startup();
            RestartFramework.at("after_bootstrap").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    @Test
    public void explicitVersionIgnore() throws Throwable {
        try (Cluster cluster = Cluster.build(2).withTokenSupplier(TokenSupplier.evenlyDistributedTokens(3)).withNodeIdTopology(NetworkTopology.singleDcNetworkTopology(3, "dc0", "rack0")).withConfig(config -> config.with(NETWORK, GOSSIP)).start()) {
            UUID initialVersion = cluster.get(2).callsOnInstance(() -> Schema.instance.getVersion()).call();
            RestartFramework.at("after_initial_version").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChange("CREATE KEYSPACE ks with replication={'class':'SimpleStrategy', 'replication_factor':2}");
            RestartFramework.at("after_keyspace_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            UUID oldVersion;
            do {
                oldVersion = cluster.get(2).callsOnInstance(() -> Schema.instance.getVersion()).call();
            } while (oldVersion.equals(initialVersion));
            RestartFramework.at("after_version_change").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(2).shutdown(false);
            RestartFramework.at("after_node_shutdown").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChangeIgnoringStoppedInstances("CREATE TABLE ks.tbl (k int primary key, v int)");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            IInstanceConfig config = cluster.newInstanceConfig();
            config.set("auto_bootstrap", true);
            withProperties.set(IGNORED_SCHEMA_CHECK_VERSIONS, initialVersion.toString() + ',' + oldVersion);
            cluster.bootstrap(config).startup();
            RestartFramework.at("after_bootstrap").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }
}
