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
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.service.StorageService;
import static org.apache.cassandra.distributed.shared.AssertUtils.assertRows;
import static org.apache.cassandra.distributed.shared.AssertUtils.row;

public class FrozenUDTTest_RestartInjected_Randomized_42 extends TestBaseImpl {

    @Test
    public void testAddAndUDTField() throws IOException {
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange("create type " + KEYSPACE + ".a (foo text)");
            cluster.schemaChange("create table " + KEYSPACE + ".x (id int, ck frozen<a>, i int, primary key (id, ck))");
            RestartFramework.at("after_udt_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 0; i < 10; i++) cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(i) + ", ? )", ConsistencyLevel.ALL, i, i);
            for (int i = 0; i < 10; i++) assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(i), ConsistencyLevel.ALL, i), row(i));
            RestartFramework.at("after_initial_inserts").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChange("alter type " + KEYSPACE + ".a add bar text");
            RestartFramework.at("after_alter_type").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 5; i < 15; i++) cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(i) + ", ? )", ConsistencyLevel.ALL, i, i);
            cluster.forEach(i -> i.flush(KEYSPACE));
            RestartFramework.at("after_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 5; i < 15; i++) assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(i), ConsistencyLevel.ALL, i), row(i));
        }
    }

    @Test
    public void testEmptyValue() throws IOException {
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange("create type " + KEYSPACE + ".a (foo text)");
            cluster.schemaChange("create table " + KEYSPACE + ".x (id int, ck frozen<a>, i int, primary key (id, ck))");
            RestartFramework.at("after_empty_value_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (1, system.from_json('{\"foo\":\"\"}'), 1)", ConsistencyLevel.ALL);
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (1, system.from_json('{\"foo\":\"a\"}'), 2)", ConsistencyLevel.ALL);
            cluster.forEach(i -> i.flush(KEYSPACE));
            RestartFramework.at("after_empty_value_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            Runnable check = () -> {
                assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = 1 and ck = system.from_json('{\"foo\":\"\"}')", ConsistencyLevel.ALL), row(1));
                assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = 1 and ck = system.from_json('{\"foo\":\"a\"}')", ConsistencyLevel.ALL), row(2));
            };
            check.run();
            cluster.schemaChange("alter type " + KEYSPACE + ".a add bar text");
            RestartFramework.at("after_empty_value_alter_type").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            check.run();
            assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = 1 and ck = system.from_json('{\"foo\":\"\",\"bar\":\"\"}')", ConsistencyLevel.ALL));
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (1, system.from_json('{\"foo\":\"\",\"bar\":\"\"}'), 3)", ConsistencyLevel.ALL);
            RestartFramework.at("after_empty_value_final_insert").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            check.run();
            assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = 1 and ck = system.from_json('{\"foo\":\"\",\"bar\":\"\"}')", ConsistencyLevel.ALL), row(3));
        }
    }

    @Test
    public void testUpgradeSStables() throws IOException {
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange("create type " + KEYSPACE + ".a (foo text)");
            cluster.schemaChange("create table " + KEYSPACE + ".x (id int, ck frozen<a>, i int, primary key (id, ck))");
            RestartFramework.at("after_upgrade_sstables_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(1) + ", ? )", ConsistencyLevel.ALL, 1, 1);
            assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(1), ConsistencyLevel.ALL, 1), row(1));
            cluster.forEach(i -> i.flush(KEYSPACE));
            assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(1), ConsistencyLevel.ALL, 1), row(1));
            RestartFramework.at("after_upgrade_sstables_first_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.schemaChange("alter type " + KEYSPACE + ".a add bar text");
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(2) + ", ? )", ConsistencyLevel.ALL, 2, 2);
            cluster.forEach(i -> i.flush(KEYSPACE));
            assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(2), ConsistencyLevel.ALL, 2), row(2));
            RestartFramework.at("after_upgrade_sstables_second_flush").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(i -> i.runOnInstance(() -> {
                try {
                    StorageService.instance.upgradeSSTables(KEYSPACE, false, "x");
                } catch (IOException | ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));
            RestartFramework.at("after_upgrade_sstables").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(i -> i.forceCompact(KEYSPACE, "x"));
            RestartFramework.at("after_upgrade_sstables_compact").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 1; i < 3; i++) assertRows(cluster.coordinator(1).execute("select i from " + KEYSPACE + ".x WHERE id = ? and ck = " + json(i), ConsistencyLevel.ALL, i), row(i));
        }
    }

    @Test
    public void testDivergentSchemas() throws Throwable {
        try (Cluster cluster = init(Cluster.create(2))) {
            cluster.schemaChange("create type " + KEYSPACE + ".a (foo text)");
            cluster.schemaChange("create table " + KEYSPACE + ".x (id int, ck frozen<a>, i int, primary key (id, ck))");
            RestartFramework.at("after_divergent_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).executeInternal("alter type " + KEYSPACE + ".a add bar text");
            RestartFramework.at("after_divergent_alter_type").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(1, 1) + ", ? )", ConsistencyLevel.ALL, 1, 1);
            cluster.coordinator(1).execute("insert into " + KEYSPACE + ".x (id, ck, i) VALUES (?, " + json(1, 2) + ", ? )", ConsistencyLevel.ALL, 2, 2);
            RestartFramework.at("after_divergent_inserts").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(2).flush(KEYSPACE);
            RestartFramework.at("after_divergent_flush").on(cluster).restart("node").withIndex(1).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    private String json(int i) {
        return String.format("system.from_json('{\"foo\":\"%d\"}')", i);
    }

    private String json(int i, int j) {
        return String.format("system.from_json('{\"foo\":\"%d\", \"bar\":\"%d\"}')", i, j);
    }
}
