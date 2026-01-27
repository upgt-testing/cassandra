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
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.apache.cassandra.concurrent.Stage;
import org.apache.cassandra.distributed.api.ConsistencyLevel;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.impl.IsolatedExecutor;
import org.apache.cassandra.distributed.impl.TracingUtil;
import org.apache.cassandra.distributed.shared.WithProperties;
import org.apache.cassandra.utils.TimeUUID;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.apache.cassandra.config.CassandraRelevantProperties.WAIT_FOR_TRACING_EVENTS_TIMEOUT_SECS;
import static org.apache.cassandra.utils.TimeUUID.Generator.nextTimeUUID;

public class MessageForwardingTest_RestartInjected_Randomized_456 extends TestBaseImpl {

    @Test
    public void mutationsForwardedToAllReplicasTest() {
        final int numInserts = 100;
        Map<InetAddress, Integer> forwardFromCounts = new HashMap<>();
        Map<InetAddress, Integer> commitCounts = new HashMap<>();
        try (WithProperties properties = new WithProperties().set(WAIT_FOR_TRACING_EVENTS_TIMEOUT_SECS, 1);
            Cluster cluster = (Cluster) init(builder().withDC("dc0", 1).withDC("dc1", 3).start())) {
            cluster.schemaChange("CREATE TABLE " + KEYSPACE + ".tbl (pk int, ck int, v text, PRIMARY KEY (pk, ck))");
            RestartFramework.at("after_table_create").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(instance -> commitCounts.put(instance.broadcastAddress().getAddress(), 0));
            final TimeUUID sessionId = nextTimeUUID();
            Stream<Future<Object[][]>> inserts = IntStream.range(0, numInserts).mapToObj((idx) -> {
                return cluster.coordinator(1).asyncExecuteWithTracing(sessionId.asUUID(), "INSERT INTO " + KEYSPACE + ".tbl(pk,ck,v) VALUES (1, 1, 'x')", ConsistencyLevel.ALL);
            });
            inserts.map(IsolatedExecutor::waitOn).collect(Collectors.toList());
            RestartFramework.at("after_inserts_complete").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.forEach(instance -> instance.runOnInstance(() -> {
                Future<?> result = Stage.TRACING.submit(() -> null);
                try {
                    result.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException | InterruptedException | TimeoutException ex) {
                    throw new RuntimeException(ex);
                }
            }));
            RestartFramework.at("after_tracing_sync").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            cluster.stream("dc1").forEach(instance -> forwardFromCounts.put(instance.broadcastAddress().getAddress(), 0));
            cluster.forEach(instance -> commitCounts.put(instance.broadcastAddress().getAddress(), 0));
            List<TracingUtil.TraceEntry> traces = TracingUtil.getTrace(cluster, sessionId.asUUID(), ConsistencyLevel.ALL);
            RestartFramework.at("after_trace_retrieval").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            traces.forEach(traceEntry -> {
                if (traceEntry.activity.contains("Appending to commitlog")) {
                    commitCounts.compute(traceEntry.source, (k, v) -> (v != null ? v : 0) + 1);
                } else if (traceEntry.activity.contains("Enqueuing forwarded write to ")) {
                    forwardFromCounts.compute(traceEntry.source, (k, v) -> (v != null ? v : 0) + 1);
                }
            });
            RestartFramework.at("after_trace_processing").on(cluster).restart("node").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            forwardFromCounts.forEach((source, count) -> Assert.assertTrue(source + " should have been randomized to forward messages", count > 0));
            commitCounts.forEach((source, count) -> Assert.assertEquals(source + " appending to commitlog traces", (long) numInserts, (long) count));
        } catch (IOException e) {
            Assert.fail("Threw exception: " + e);
        }
    }
}
