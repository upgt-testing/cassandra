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
package org.apache.cassandra.distributed.test.metrics;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.SuperMethod;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.test.TestBaseImpl;
import org.apache.cassandra.exceptions.RequestFailureReason;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.net.RequestCallback;
import org.apache.cassandra.utils.AssertionUtils;
import org.apache.cassandra.exceptions.CasWriteTimeoutException;
import org.apache.cassandra.exceptions.ReadTimeoutException;
import org.apache.cassandra.exceptions.WriteTimeoutException;
import org.apache.cassandra.net.Verb;
import org.apache.cassandra.service.paxos.Paxos;
import org.apache.cassandra.utils.concurrent.Condition;
import org.assertj.core.api.Assertions;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.cassandra.utils.AssertionUtils.isThrowable;

public class RequestTimeoutTest_RestartInjected_Randomized_123 extends TestBaseImpl {

    private static final AtomicInteger NEXT = new AtomicInteger(0);

    public static final int COORDINATOR = 1;

    private static Cluster CLUSTER;

    @BeforeClass
    public static void init() throws IOException {
        CLUSTER = Cluster.build(3).withConfig(c -> c.set("truncate_request_timeout", "10s")).withInstanceInitializer(BB::install).start();
        init(CLUSTER);
        RestartFramework.at("after_cluster_init").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.schemaChange(withKeyspace("CREATE TABLE %s.tbl (pk int PRIMARY KEY, v int)"));
    }

    @AfterClass
    public static void cleanup() {
        CLUSTER.forEach(i -> {
            i.runOnInstance(() -> {
                BB.ENABLED = false;
            });
        });
        if (CLUSTER != null)
            CLUSTER.close();
    }

    @Before
    public void before() {
        CLUSTER.get(COORDINATOR).runOnInstance(() -> MessagingService.instance().callbacks.unsafeClear());
        CLUSTER.filters().reset();
        BB.reset();
    }

    @Test
    public void insert() {
        RestartFramework.at("after_insert_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_insert_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.MUTATION_REQ.id).to(2).drop();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("INSERT INTO %s.tbl (pk, v) VALUES (?, ?)"), ConsistencyLevel.ALL, NEXT.getAndIncrement(), NEXT.getAndIncrement())).is(isThrowable(WriteTimeoutException.class));
        BB.assertIsTimeoutTrue();
    }

    @Test
    public void update() {
        RestartFramework.at("after_update_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.MUTATION_REQ.id).to(2).drop();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("UPDATE %s.tbl SET v=? WHERE pk=?"), ConsistencyLevel.ALL, NEXT.getAndIncrement(), NEXT.getAndIncrement())).is(isThrowable(WriteTimeoutException.class));
        BB.assertIsTimeoutTrue();
        RestartFramework.at("after_update_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void batchInsert() {
        CLUSTER.filters().verbs(Verb.MUTATION_REQ.id).to(2).drop();
        RestartFramework.at("after_batch_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(batch(withKeyspace("INSERT INTO %s.tbl (pk, v) VALUES (?, ?)")), ConsistencyLevel.ALL, NEXT.getAndIncrement(), NEXT.getAndIncrement())).is(isThrowable(WriteTimeoutException.class));
        RestartFramework.at("after_batch_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        BB.assertIsTimeoutTrue();
    }

    @Test
    public void rangeSelect() {
        RestartFramework.at("after_range_select_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.RANGE_REQ.id).to(2).drop();
        RestartFramework.at("after_range_select_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("SELECT * FROM %s.tbl"), ConsistencyLevel.ALL)).is(isThrowable(ReadTimeoutException.class));
        BB.assertIsTimeoutTrue();
    }

    @Test
    public void select() {
        CLUSTER.filters().verbs(Verb.READ_REQ.id).to(2).drop();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("SELECT * FROM %s.tbl WHERE pk=?"), ConsistencyLevel.ALL, NEXT.getAndIncrement())).is(isThrowable(ReadTimeoutException.class));
        RestartFramework.at("after_select_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_select_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        BB.assertIsTimeoutTrue();
    }

    @Test
    public void truncate() {
        CLUSTER.filters().verbs(Verb.TRUNCATE_REQ.id).to(2).drop();
        RestartFramework.at("after_truncate_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_truncate_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("TRUNCATE %s.tbl"), ConsistencyLevel.ALL)).is(AssertionUtils.rootCauseIs(TimeoutException.class));
        BB.assertIsTimeoutTrue();
    }

    // don't call BB.assertIsTimeoutTrue(); for CAS, as it has its own logic
    @Test
    public void casV2PrepareInsert() {
        withPaxos(Config.PaxosVariant.v2);
        RestartFramework.at("after_cas_prepare_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_paxos_v2_set").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.PAXOS2_PREPARE_REQ.id).to(2, 3).drop();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("INSERT INTO %s.tbl (pk, v) VALUES (?, ?) IF NOT EXISTS"), ConsistencyLevel.ALL, NEXT.getAndIncrement(), NEXT.getAndIncrement())).is(isThrowable(CasWriteTimeoutException.class));
        RestartFramework.at("after_cas_prepare_insert_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void casV2PrepareSelect() {
        RestartFramework.at("after_paxos_v2_set_for_select").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        withPaxos(Config.PaxosVariant.v2);
        RestartFramework.at("after_cas_prepare_select_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.PAXOS2_PREPARE_REQ.id).to(2, 3).drop();
        RestartFramework.at("after_cas_prepare_select_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("SELECT * FROM %s.tbl WHERE pk=?"), ConsistencyLevel.SERIAL, NEXT.getAndIncrement())).is(// why does write have its own type but not read?
        isThrowable(ReadTimeoutException.class));
    }

    @Test
    public void casV2CommitInsert() {
        RestartFramework.at("after_cas_commit_insert_timeout_test").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        withPaxos(Config.PaxosVariant.v2);
        RestartFramework.at("after_cas_commit_filter_setup").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_paxos_v2_set_for_commit").on(CLUSTER).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        CLUSTER.filters().verbs(Verb.PAXOS_COMMIT_REQ.id).to(2, 3).drop();
        Assertions.assertThatThrownBy(() -> CLUSTER.coordinator(COORDINATOR).execute(withKeyspace("INSERT INTO %s.tbl (pk, v) VALUES (?, ?) IF NOT EXISTS"), ConsistencyLevel.ALL, NEXT.getAndIncrement(), NEXT.getAndIncrement())).is(isThrowable(CasWriteTimeoutException.class));
    }

    private static void withPaxos(Config.PaxosVariant variant) {
        CLUSTER.forEach(i -> i.runOnInstance(() -> Paxos.setPaxosVariant(variant)));
    }

    private static String batch(String cql) {
        return "BEGIN " + BatchStatement.Type.UNLOGGED.name() + " BATCH\n" + cql + "\nAPPLY BATCH";
    }

    public static class BB {

        private static boolean ENABLED = true;

        public static void install(ClassLoader cl, int num) {
            if (num != COORDINATOR)
                return;
            new ByteBuddy().rebase(Condition.Async.class).method(named("awaitUntil").and(takesArguments(long.class))).intercept(MethodDelegation.to(BB.class)).make().load(cl, ClassLoadingStrategy.Default.INJECTION);
            new ByteBuddy().rebase(RequestCallback.class).method(named("isTimeout")).intercept(MethodDelegation.to(BB.class)).make().load(cl, ClassLoadingStrategy.Default.INJECTION);
        }

        public static boolean awaitUntil(long deadlineNanos, @This Condition.Async self, @SuperMethod Method method) throws InterruptedException, InvocationTargetException, IllegalAccessException {
            if (!ENABLED)
                return (boolean) method.invoke(self, deadlineNanos);
            boolean res = false;
            // make sure that the underline condition is met before returnning true
            // this way its know that the timeouts triggered!
            while (ENABLED) {
                res = (boolean) method.invoke(self, deadlineNanos);
                if (res)
                    return true;
            }
            return res;
        }

        private static final AtomicInteger TIMEOUTS = new AtomicInteger(0);

        public static boolean isTimeout(Map<InetAddressAndPort, RequestFailureReason> failureReasonByEndpoint, @SuperCall Callable<Boolean> fn) throws Exception {
            boolean timeout = fn.call();
            if (timeout)
                TIMEOUTS.incrementAndGet();
            return timeout;
        }

        public static void assertIsTimeoutTrue() {
            int timeouts = CLUSTER.get(COORDINATOR).callOnInstance(() -> TIMEOUTS.getAndSet(0));
            Assertions.assertThat(timeouts).isGreaterThan(0);
        }

        public static void reset() {
            CLUSTER.get(COORDINATOR).runOnInstance(() -> TIMEOUTS.set(0));
        }
    }
}
