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

import org.junit.Test;

import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.api.Feature;
import org.apache.cassandra.service.ActiveRepairService;
import org.apache.cassandra.utils.TimeUUID;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.apache.cassandra.net.Verb.FINALIZE_COMMIT_MSG;
import static org.assertj.core.api.Assertions.assertThat;

public class IncRepairCoordinatorErrorTest_RestartInjected extends TestBaseImpl
{
    @Test
    public void errorTest() throws Exception
    {
        try (Cluster cluster = init(Cluster.build(3)
                                           .withConfig(c -> c.with(Feature.NETWORK, Feature.GOSSIP))
                                           .start()))
        {
            cluster.schemaChange(withKeyspace("create table %s.tbl (id int primary key, x int)"));

            RestartFramework.at("after_table_create")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            for (int i = 0; i < 10; i++)
                cluster.coordinator(1).execute(withKeyspace("insert into %s.tbl (id, x) values (?, ?)"), ConsistencyLevel.ALL, i, i);

            RestartFramework.at("after_data_insert")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            cluster.filters().inbound()
                   .to(3)
                   .messagesMatching((from, to, msg) -> msg.verb() == FINALIZE_COMMIT_MSG.id).drop();

            RestartFramework.at("after_filter_setup")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            cluster.get(1).nodetoolResult("repair", KEYSPACE).asserts().success();

            RestartFramework.at("after_repair")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            assertThat(cluster.get(1).logs().watchFor("Removing completed session .* with state FINALIZED").getResult()).isNotEmpty();

            RestartFramework.at("after_verify_finalized")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            TimeUUID result = (TimeUUID) cluster.get(1).executeInternal("select parent_id from system_distributed.repair_history")[0][0];

            RestartFramework.at("after_get_parent_id")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();

            cluster.get(3).runOnInstance(() -> {
                ActiveRepairService.instance().failSession(result.toString(), true);
            });

            RestartFramework.at("after_fail_session")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }
}
