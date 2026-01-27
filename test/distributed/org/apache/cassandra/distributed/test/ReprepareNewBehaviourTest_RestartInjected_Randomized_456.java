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

import org.junit.Assert;
import org.junit.Test;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.apache.cassandra.distributed.api.ICluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NATIVE_PROTOCOL;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;

public class ReprepareNewBehaviourTest_RestartInjected_Randomized_456 extends ReprepareTestBase {

    @Test
    public void testUseWithMultipleKeyspaces() throws Throwable {
        try (ICluster<IInvokableInstance> c = init(builder().withNodes(1).withConfig(config -> config.with(GOSSIP, NETWORK, NATIVE_PROTOCOL)).start())) {
            try (com.datastax.driver.core.Cluster cluster = com.datastax.driver.core.Cluster.builder().addContactPoint("127.0.0.1").build();
                Session session = cluster.connect()) {
                c.schemaChange(withKeyspace("CREATE KEYSPACE ks1 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"));
                c.schemaChange(withKeyspace("CREATE TABLE ks1.tbl (pk int, ck int, v int, PRIMARY KEY (pk, ck));"));
                c.schemaChange(withKeyspace("CREATE KEYSPACE ks2 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"));
                c.schemaChange(withKeyspace("CREATE TABLE ks2.tbl (pk int, ck int, v int, PRIMARY KEY (pk, ck));"));
                RestartFramework.at("after_schema_setup").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                String query = "SELECT * FROM tbl";
                session.execute("USE ks1");
                PreparedStatement selectKs1 = session.prepare(query);
                RestartFramework.at("after_prepare_ks1").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                // Insert explicitly into the ks2 version of table...
                session.execute("INSERT INTO ks2.tbl (pk, ck, v) VALUES (1, 1, 1)");
                RestartFramework.at("after_insert_ks2").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                // There should be nothing in the ks1 version...
                ResultSet resultsKs1 = session.execute(selectKs1.bind());
                Assert.assertEquals(0, resultsKs1.all().size());
                RestartFramework.at("after_query_ks1").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                session.execute("USE ks2");
                RestartFramework.at("after_switch_ks2").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                // ... but after switching to use ks2, a new query prepared against tbl should return a result.
                PreparedStatement selectKs2 = session.prepare(query);
                Assert.assertEquals("ks2", selectKs2.getQueryKeyspace());
                RestartFramework.at("after_prepare_ks2").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                ResultSet resultsKs2 = session.execute(selectKs2.bind());
                Assert.assertEquals(1, resultsKs2.all().size());
                RestartFramework.at("after_query_ks2").on(c).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                resultsKs1 = session.execute(selectKs1.bind());
                Assert.assertEquals(0, resultsKs1.all().size());
            }
        }
    }

    @Test
    public void testReprepareNewBehaviour() throws Throwable {
        testReprepare(PrepareBehaviour::newBehaviour, cfg(true, false), cfg(false, false));
    }

    @Test
    public void testReprepareTwoKeyspacesNewBehaviour() throws Throwable {
        testReprepareTwoKeyspaces(PrepareBehaviour::newBehaviour);
    }
}
