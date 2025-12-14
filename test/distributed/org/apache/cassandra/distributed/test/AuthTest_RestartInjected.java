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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Test;

import com.datastax.driver.core.PlainTextAuthProvider;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.api.ICoordinator;
import org.apache.cassandra.distributed.api.IInstanceConfig;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.IMessageFilters.Filter;
import org.apache.cassandra.distributed.api.TokenSupplier;
import org.apache.cassandra.distributed.util.Auth;
import org.apache.cassandra.locator.SimpleSeedProvider;
import org.apache.cassandra.service.StorageService;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NATIVE_PROTOCOL;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuthTest_RestartInjected extends TestBaseImpl
{
    /**
     * Simply tests that initialisation of a test Instance results in
     * StorageService.instance.doAuthSetup being called as the regular
     * startup does in CassandraDaemon.setup
     */
    @Test
    public void authSetupIsCalledAfterStartup() throws IOException
    {
        try (Cluster cluster = Cluster.build().withNodes(1).start())
        {
            IInvokableInstance instance = cluster.get(1);
            await().pollDelay(1, SECONDS)
                   .pollInterval(1, SECONDS)
                   .atMost(10, SECONDS)
                   .until(() -> instance.callOnInstance(() -> StorageService.instance.authSetupCalled()));

            RestartFramework.at("after_authsetup")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }

    /**
     * See CASSANDRA-12525 for more information.
     */
    @Test
    public void testZeroTimestampForDefaultRoleCreation() throws Exception
    {
        try (Cluster cluster = builder().withDCs(2)
                                        .withNodes(1)
                                        .withTokenSupplier(TokenSupplier.evenlyDistributedTokens(2, 1))
                                        .withConfig(config -> config.with(NETWORK, GOSSIP, NATIVE_PROTOCOL)
                                                                    .set("authenticator", "PasswordAuthenticator"))
                                        .start())
        {
            Auth.waitForExistingRoles(cluster.get(1));

            RestartFramework.at("after_roles_exist")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            long writeTime = getPasswordWritetime(cluster.coordinator(1));
            assertEquals(0, writeTime);

            RestartFramework.at("after_get_writetime")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            changePassword();

            RestartFramework.at("after_password_change")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            long writeTimeAfterPasswordChange = getPasswordWritetime(cluster.coordinator(1));

            assertTrue(writeTime < writeTimeAfterPasswordChange);

            IInvokableInstance secondNode = getSecondNode(cluster);

            Filter to = cluster.filters().allVerbs().inbound().drop();
            Filter from = cluster.filters().allVerbs().outbound().drop();

            secondNode.startup();

            RestartFramework.at("after_second_node_start")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            Auth.waitForExistingRoles(secondNode);

            RestartFramework.at("after_second_node_roles")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            long passwordWritetimeOnSecondNode = getPasswordWritetime(cluster.coordinator(2));

            assertEquals(0, passwordWritetimeOnSecondNode);

            doWithSession("127.0.0.2",
                          "datacenter2",
                          "cassandra", session -> session.execute("select * from system.local"));

            RestartFramework.at("after_login_second_node")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            to.off();
            from.off();

            RestartFramework.at("after_filters_off")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            await()
            .atMost(1, TimeUnit.MINUTES)
            .pollInterval(10, SECONDS)
            .until(() -> {
                List<Row> rows = doWithSession("127.0.0.2",
                                               "datacenter2",
                                               "cassandra",
                                               session -> session.execute("select * from system.peers")).all();
                if (rows.isEmpty())
                    return false;

                return rows.get(0).getInet("peer").getHostAddress().equals("127.0.0.1");
            });

            RestartFramework.at("after_peers_appear")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            doWithSession("127.0.0.2",
                          "datacenter2",
                          "cassandra",
                          session -> session.execute("ALTER KEYSPACE system_auth WITH replication = {'class': 'NetworkTopologyStrategy', 'datacenter1': 1, 'datacenter2': 1}"));

            RestartFramework.at("after_alter_replication")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            assertEquals(0, secondNode.nodetool("repair", "--full"));

            RestartFramework.at("after_repair")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            doWithSession("127.0.0.2",
                          "datacenter2",
                          "newpassword", session -> session.execute("select * from system.local"));

            doWithSession("127.0.0.1",
                          "datacenter1",
                          "newpassword", session -> session.execute("select * from system.local"));

            RestartFramework.at("after_final_login")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }

    private IInvokableInstance getSecondNode(Cluster cluster)
    {
        IInstanceConfig config = cluster.newInstanceConfig();
        // set both nodes as seed nodes in the list
        config.set("seed_provider", new IInstanceConfig.ParameterizedClass(SimpleSeedProvider.class.getName(),
                                                                           Collections.singletonMap("seeds", "127.0.0.1, 127.0.0.2")));
        return cluster.bootstrap(config);
    }

    private long getPasswordWritetime(ICoordinator coordinator)
    {
        return (Long) coordinator.execute("SELECT WRITETIME (salted_hash) from system_auth.roles where role = 'cassandra'",
                                          ConsistencyLevel.LOCAL_ONE)[0][0];
    }

    private void changePassword()
    {
        doWithSession("127.0.0.1", "datacenter1", "cassandra", (Function<Session, Void>) session -> {
            session.execute("ALTER ROLE cassandra WITH PASSWORD = 'newpassword'");
            return null;
        });
    }

    private <V> V doWithSession(String host, String datacenter, String password, Function<Session, V> fn)
    {
        com.datastax.driver.core.Cluster.Builder builder = com.datastax.driver.core.Cluster.builder()
                                                                                           .withLoadBalancingPolicy(new DCAwareRoundRobinPolicy.Builder().withLocalDc(datacenter).build())
                                                                                           .withAuthProvider(new PlainTextAuthProvider("cassandra", password))
                                                                                           .addContactPoint(host);

        try (com.datastax.driver.core.Cluster c = builder.build(); Session session = c.connect())
        {
            return fn.apply(session);
        }
    }
}
