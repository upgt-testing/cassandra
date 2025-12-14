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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.net.Verb;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.cassandra.distributed.api.Feature.GOSSIP;
import static org.apache.cassandra.distributed.api.Feature.NETWORK;
import static org.apache.cassandra.net.Verb.SYNC_REQ;
import static org.apache.cassandra.net.Verb.VALIDATION_REQ;
import static org.junit.Assert.assertTrue;

public class RepairRequestTimeoutTest_RestartInjected extends TestBaseImpl
{
    static Cluster CLUSTER;
    static final long timeoutMillis = 1000;
    @BeforeClass
    public static void setup() throws IOException
    {
        CLUSTER = init(Cluster.build(3)
                              .withConfig(config -> config.with(GOSSIP, NETWORK).set("repair_request_timeout_in_ms", timeoutMillis))
                              .start());
        CLUSTER.schemaChange(withKeyspace("create table %s.tbl (id int primary key)"));
    }

    @Before
    public void before()
    {
        CLUSTER.filters().reset();
    }

    @Test
    public void testLostSYNC_REQ()
    {
        testLostMessageHelper(SYNC_REQ);
    }

    @Test
    public void testLostVALIDATION_REQ()
    {
        testLostMessageHelper(VALIDATION_REQ);
    }

    public void testLostMessageHelper(Verb verb)
    {
        for (int i = 0; i < 10; i++)
            CLUSTER.coordinator(1).execute(withKeyspace("insert into %s.tbl (id) values (?)"), ConsistencyLevel.ALL, i);
        RestartFramework.at("after_coordinator_writes")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        for (int  i = 10; i < 20; i++)
            CLUSTER.get((i % 3) + 1).executeInternal(withKeyspace("insert into %s.tbl (id) values (?)"), i);
        RestartFramework.at("after_internal_writes")
            .on(CLUSTER)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.forEach(i -> i.flush(KEYSPACE));
        RestartFramework.at("after_flush")
            .on(CLUSTER)
            .restart("node")
            .withIndex(2)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.filters().verbs(verb.id).drop();
        RestartFramework.at("after_filter_setup")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.get(1).nodetoolResult("repair", "-full", KEYSPACE, "tbl").asserts().failure().notificationContains(verb + " failure from");
        RestartFramework.at("after_first_failed_repair")
            .on(CLUSTER)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.get(1).nodetoolResult("repair", "-full", "-os", KEYSPACE, "tbl").asserts().failure().notificationContains(verb + " failure from");
        RestartFramework.at("after_second_failed_repair")
            .on(CLUSTER)
            .restart("node")
            .withIndex(2)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.filters().reset();
        RestartFramework.at("after_filter_reset")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        long mark = CLUSTER.get(1).logs().mark();
        RestartFramework.at("after_log_mark")
            .on(CLUSTER)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.get(1).nodetoolResult("repair", "-full", KEYSPACE, "tbl").asserts().success();
        RestartFramework.at("after_successful_repair")
            .on(CLUSTER)
            .restart("node")
            .withIndex(2)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        for (int  i = 10; i < 20; i++)
            CLUSTER.get((i % 3) + 1).executeInternal(withKeyspace("insert into %s.tbl (id) values (?)"), i);
        RestartFramework.at("after_additional_writes")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.get(1).nodetoolResult("repair", "-full", "-os", KEYSPACE, "tbl").asserts().success();
        RestartFramework.at("after_final_repair")
            .on(CLUSTER)
            .restart("node")
            .withIndex(1)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        CLUSTER.get(1).runOnInstance(() -> {
            long expirationInterval = DatabaseDescriptor.getMinRpcTimeout(MILLISECONDS) / 2;
            sleepUninterruptibly((timeoutMillis + expirationInterval) * 2, MILLISECONDS);
        });

        assertTrue(CLUSTER.get(1).logs().grep(mark, "failure from").getResult().isEmpty());
    }
}
