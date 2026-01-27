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
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.serializers.BooleanSerializer;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class BooleanTest_RestartInjected_Randomized_42 extends TestBaseImpl {

    @Test
    public void booleanTest() throws Throwable {
        try (Cluster cluster = init(Cluster.build(1).start())) {
            cluster.schemaChange("create table " + KEYSPACE + ".tbl (id int, ck boolean, t int, primary key ((id, ck)))");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            for (int i = 0; i < 10; i++) cluster.coordinator(1).execute(withKeyspace("insert into %s.tbl (id, ck, t) values (?, true, ?)"), ConsistencyLevel.ALL, i, i);
            RestartFramework.at("after_data_insert").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.get(1).nodetoolResult("getsstables", KEYSPACE, "tbl", "1:true");
            RestartFramework.at("after_getsstables").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(i -> i.runOnInstance(() -> {
                Assert.assertEquals(0, BooleanSerializer.instance.serialize(true).position());
                Assert.assertEquals(0, BooleanSerializer.instance.serialize(false).position());
            }));
        }
    }
}
