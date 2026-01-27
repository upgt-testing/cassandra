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

import java.util.function.Consumer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.IInstanceConfig;
import org.apache.cassandra.distributed.api.IMessageFilters;
import org.apache.cassandra.distributed.impl.UnsafeGossipHelper;
import org.apache.cassandra.exceptions.CasWriteTimeoutException;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.distributed.api.ConsistencyLevel.ANY;
import static org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM;
import static org.apache.cassandra.distributed.shared.AssertUtils.assertRows;
import static org.apache.cassandra.distributed.shared.AssertUtils.row;
import static org.apache.cassandra.net.Verb.PAXOS2_PREPARE_REQ;
import static org.apache.cassandra.net.Verb.PAXOS2_PROPOSE_REQ;
import static org.apache.cassandra.net.Verb.PAXOS_PREPARE_REQ;
import static org.apache.cassandra.net.Verb.PAXOS_PROPOSE_REQ;

public class LegacyCASTest_RestartInjected_Randomized_456 extends CASCommonTestCases {

    private static Cluster CLUSTER;

    @BeforeClass
    public static void beforeClass() throws Throwable {
        TestBaseImpl.beforeClass();
        CLUSTER = init(Cluster.create(3, config()));
    }

    @AfterClass
    public static void afterClass() {
        if (CLUSTER != null)
            CLUSTER.close();
    }

    private static Consumer<IInstanceConfig> config() {
        return config -> config.set("paxos_variant", "v1").set("write_request_timeout_in_ms", 5000L).set("cas_contention_timeout_in_ms", 5000L).set("request_timeout_in_ms", 5000L);
    }

    @Test
    public void simpleUpdate() throws Throwable {
        String tableName = tableName();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (1, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
        RestartFramework.at("after_failed_cas_update").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_cas_insert").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_serial_select").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL), row(1, 1, 1));
        getCluster().coordinator(1).execute("UPDATE " + fullTableName + " SET v = 3 WHERE pk = 1 and ck = 1 IF v = 2", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
        assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL), row(1, 1, 1));
        getCluster().coordinator(1).execute("UPDATE " + fullTableName + " SET v = 2 WHERE pk = 1 and ck = 1 IF v = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
        RestartFramework.at("after_successful_cas_update").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL), row(1, 1, 2));
    }

    @Test
    public void incompletePrepare() throws Throwable {
        String tableName = tableName();
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        RestartFramework.at("after_cas_update").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        IMessageFilters.Filter drop = getCluster().filters().verbs(PAXOS2_PREPARE_REQ.id, PAXOS_PREPARE_REQ.id).from(1).to(2, 3).drop();
        RestartFramework.at("after_filter_setup").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_incomplete_prepare").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try {
            getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (1, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
            Assert.assertTrue(false);
        } catch (RuntimeException t) {
            if (!t.getClass().getName().equals(CasWriteTimeoutException.class.getName()))
                throw new AssertionError(t);
        }
        drop.off();
        getCluster().coordinator(1).execute("UPDATE " + fullTableName + " SET v = 2 WHERE pk = 1 and ck = 1 IF v = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
        assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL));
    }

    @Test
    public void incompletePropose() throws Throwable {
        RestartFramework.at("after_drop_filter_setup").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        String tableName = tableName();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        RestartFramework.at("after_cas_update").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_filter_setup").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_incomplete_propose").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        IMessageFilters.Filter drop1 = getCluster().filters().verbs(PAXOS2_PROPOSE_REQ.id, PAXOS_PROPOSE_REQ.id).from(1).to(2, 3).drop();
        try {
            getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (1, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
            Assert.assertTrue(false);
        } catch (RuntimeException t) {
            if (!t.getClass().getName().equals(CasWriteTimeoutException.class.getName()))
                throw new AssertionError(t);
        }
        drop1.off();
        drop(getCluster(), 1, to(2), to(), to());
        getCluster().coordinator(1).execute("UPDATE " + fullTableName + " SET v = 2 WHERE pk = 1 and ck = 1 IF v = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
        assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL), row(1, 1, 2));
    }

    @Test
    public void incompleteCommit() throws Throwable {
        String tableName = tableName();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        RestartFramework.at("after_incomplete_commit").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try (AutoCloseable drop = drop(getCluster(), 1, to(), to(), to(2, 3))) {
            RestartFramework.at("after_drop_filter_setup").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (1, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
            Assert.assertTrue(false);
        } catch (RuntimeException t) {
            if (!t.getClass().getName().equals(CasWriteTimeoutException.class.getName()))
                throw new AssertionError(t);
        }
        try (AutoCloseable drop = drop(getCluster(), 1, to(2), to(2), to())) {
            getCluster().coordinator(1).execute("UPDATE " + fullTableName + " SET v = 2 WHERE pk = 1 and ck = 1 IF v = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM);
            RestartFramework.at("after_cas_update").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertRows(getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = 1", org.apache.cassandra.distributed.api.ConsistencyLevel.SERIAL), row(1, 1, 2));
        }
    }

    @Test
    public void testRepairIncompletePropose() throws Throwable {
        String tableName = tableName();
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        for (int repairWithout = 1; repairWithout <= 3; ++repairWithout) {
            try (AutoCloseable drop = drop(getCluster(), 1, to(), to(2, 3), to())) {
                RestartFramework.at("after_drop_filter_setup_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (?, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM, repairWithout);
                Assert.assertTrue(false);
            } catch (RuntimeException t) {
                if (!t.getClass().getName().equals(CasWriteTimeoutException.class.getName()))
                    throw new AssertionError(t);
            }
            RestartFramework.at("after_incomplete_propose_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            int repairWith = repairWithout == 3 ? 2 : 3;
            repair(getCluster(), tableName, repairWithout, repairWith, repairWithout);
            RestartFramework.at("after_paxos_repair_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            try (AutoCloseable drop = drop(getCluster(), repairWith, to(repairWithout), to(), to())) {
                Object[][] rows = getCluster().coordinator(1).execute("SELECT * FROM " + fullTableName + " WHERE pk = ?", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM, repairWithout);
                if (repairWithout == 1)
                    assertRows(rows);
                else
                    assertRows(rows, row(repairWithout, 1, 1));
            }
        }
    }

    @Test
    public void testRepairIncompleteCommit() throws Throwable {
        String tableName = tableName();
        RestartFramework.at("after_table_create").on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        String fullTableName = KEYSPACE + "." + tableName;
        getCluster().schemaChange("CREATE TABLE " + fullTableName + " (pk int, ck int, v int, PRIMARY KEY (pk, ck))");
        for (int repairWithout = 1; repairWithout <= 3; ++repairWithout) {
            try (AutoCloseable drop = drop(getCluster(), 1, to(), to(3), to(2, 3))) {
                RestartFramework.at("after_drop_filter_setup_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
                getCluster().coordinator(1).execute("INSERT INTO " + fullTableName + " (pk, ck, v) VALUES (?, 1, 1) IF NOT EXISTS", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM, repairWithout);
                Assert.assertTrue(false);
            } catch (RuntimeException t) {
                if (!t.getClass().getName().equals(CasWriteTimeoutException.class.getName()))
                    throw new AssertionError(t);
            }
            RestartFramework.at("after_incomplete_commit_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            int repairWith = repairWithout == 3 ? 2 : 3;
            repair(getCluster(), tableName, repairWithout, repairWith, repairWithout);
            RestartFramework.at("after_paxos_repair_iteration_" + repairWithout).on(getCluster()).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            try (AutoCloseable drop = drop(getCluster(), repairWith, to(repairWithout), to(), to())) {
                assertRows(getCluster().coordinator(repairWith).execute("SELECT * FROM " + fullTableName + " WHERE pk = ?", org.apache.cassandra.distributed.api.ConsistencyLevel.QUORUM, repairWithout), row(repairWithout, 1, 1));
            }
        }
    }

    /**
     * This particular variant is unique to legacy Paxos because of the differing quorums for consensus, read and commit.
     * It is also unique to range movements with an even-numbered RF under legacy paxos.
     *
     * Range movements do not necessarily complete; they may be aborted.
     * CAS consistency should not be affected by this.
     *
     *  - Range moving from {1, 2} to {2, 3}; witnessed by all
     *  - Promised and Accepted on {2, 3}; Commits are delayed and arrive after next commit (or perhaps vanish)
     *  - Range move cancelled; a new one starts moving {1, 2} to {2, 4}; witnessed by all
     *  - Promised, Accepted and Committed on {1, 4}
     */
    @Ignore
    @Test
    public void testAbortedRangeMovement() throws Throwable {
        try (Cluster cluster = Cluster.create(4, config())) {
            cluster.schemaChange("CREATE KEYSPACE " + KEYSPACE + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3};");
            RestartFramework.at("after_keyspace_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChange("CREATE TABLE " + KEYSPACE + ".tbl (pk int, ck int, v1 int, v2 int, PRIMARY KEY (pk, ck))");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            int pk = pk(cluster, 1, 2);
            for (int i = 1; i <= 4; ++i) cluster.get(i).acceptsOnInstance(UnsafeGossipHelper::removeFromRing).accept(cluster.get(3));
            for (int i = 1; i <= 4; ++i) cluster.get(i).acceptsOnInstance(UnsafeGossipHelper::removeFromRing).accept(cluster.get(4));
            for (int i = 1; i <= 4; ++i) cluster.get(i).acceptsOnInstance(UnsafeGossipHelper::addToRingBootstrapping).accept(cluster.get(3));
            RestartFramework.at("after_gossip_ring_manipulation").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            drop(cluster, 3, to(1), to(), to(1), to(1, 2));
            RestartFramework.at("after_filter_setup").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertRows(cluster.coordinator(3).execute("INSERT INTO " + KEYSPACE + ".tbl (pk, ck, v1) VALUES (?, 1, 1) IF NOT EXISTS", ANY, pk), row(true));
            RestartFramework.at("after_first_cas_operation").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 1; i <= 4; ++i) cluster.get(i).acceptsOnInstance(UnsafeGossipHelper::removeFromRing).accept(cluster.get(3));
            for (int i = 1; i <= 4; ++i) cluster.get(i).acceptsOnInstance(UnsafeGossipHelper::addToRingBootstrapping).accept(cluster.get(4));
            RestartFramework.at("after_abort_node3_start_node4").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            drop(cluster, 4, to(2), to(), to(2), to());
            RestartFramework.at("after_second_filter_setup").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertRows(cluster.coordinator(4).execute("INSERT INTO " + KEYSPACE + ".tbl (pk, ck, v2) VALUES (?, 1, 2) IF NOT EXISTS", QUORUM, pk), row(false, pk, 1, 1, null));
        }
    }

    protected Cluster getCluster() {
        return CLUSTER;
    }
}
