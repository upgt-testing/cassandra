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
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.distributed.Cluster;
import org.apache.cassandra.distributed.api.ICluster;
import org.apache.cassandra.distributed.api.IInvokableInstance;
import org.apache.cassandra.distributed.api.NodeToolResult;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import static org.junit.Assert.assertEquals;

public class NodeToolTest_RestartInjected extends TestBaseImpl
{
    private static Cluster CLUSTER;
    private static IInvokableInstance NODE;

    @BeforeClass
    public static void before() throws IOException
    {
        CLUSTER = init(Cluster.build().withNodes(1).start());
        NODE = CLUSTER.get(1);
    }

    @AfterClass
    public static void after()
    {
        if (CLUSTER != null)
            CLUSTER.close();
    }

    @Test
    public void testCommands()
    {
        assertEquals(0, NODE.nodetool("help"));
        RestartFramework.at("after_help_command")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        assertEquals(0, NODE.nodetool("flush"));
        RestartFramework.at("after_flush")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        assertEquals(1, NODE.nodetool("not_a_legal_command"));
        RestartFramework.at("after_invalid_command")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    @Test
    public void testCaptureConsoleOutput()
    {
        NodeToolResult ringResult = NODE.nodetoolResult("ring");
        ringResult.asserts().stdoutContains("Datacenter: datacenter0");
        ringResult.asserts().stdoutContains("127.0.0.1       rack0       Up     Normal");
        assertEquals("Non-empty error output", "", ringResult.getStderr());
        RestartFramework.at("after_ring_command")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    @Test
    public void testNodetoolSystemExit()
    {
        // Verify currently calls System.exit, this test uses that knowlege to test System.exit behavior in jvm-dtest
        NODE.nodetoolResult("verify", "--check-tokens", "--force")
            .asserts()
            .failure()
            .stdoutContains("Token verification requires --extended-verify");
        RestartFramework.at("after_verify_command")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    @Test
    public void testSetGetTimeout()
    {
        Consumer<String> test = timeout ->
        {
            if (timeout != null)
                NODE.nodetool("settimeout", "internodestreaminguser", timeout);
            timeout = NODE.callOnInstance(() -> String.valueOf(DatabaseDescriptor.getInternodeStreamingTcpUserTimeoutInMS()));
            NODE.nodetoolResult("gettimeout", "internodestreaminguser")
                .asserts()
                .success()
                .stdoutContains("Current timeout for type internodestreaminguser: " + timeout + " ms");
        };

        test.accept(null); // test the default value
        RestartFramework.at("after_default_timeout_check")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        test.accept("1000"); // 1 second
        RestartFramework.at("after_first_timeout_set")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
        test.accept("36000000"); // 10 minutes
        RestartFramework.at("after_second_timeout_set")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    @Test
    public void testSetTimeoutInvalidInput()
    {
        NODE.nodetoolResult("settimeout", "internodestreaminguser", "-1")
            .asserts()
            .failure()
            .stdoutContains("timeout must be non-negative");
        RestartFramework.at("after_invalid_timeout")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    @Test
    public void testSetCacheCapacityWhenDisabled() throws Throwable
    {
        try (ICluster cluster = init(builder().withNodes(1).withConfig(c->c.set("row_cache_size", "0MiB")).start()))
        {
            RestartFramework.at("after_cluster_start")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
            NodeToolResult ringResult = cluster.get(1).nodetoolResult("setcachecapacity", "1", "1", "1");
            ringResult.asserts().stderrContains("is not permitted as this cache is disabled");
            RestartFramework.at("after_setcachecapacity")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }

    @Test
    public void testInfoOutput() throws Throwable
    {
        try (ICluster<?> cluster = init(builder().withNodes(1).start()))
        {
            NodeToolResult ringResult = cluster.get(1).nodetoolResult("info");
            ringResult.asserts().stdoutContains("ID");
            ringResult.asserts().stdoutContains("Gossip active");
            ringResult.asserts().stdoutContains("Native Transport active");
            ringResult.asserts().stdoutContains("Load");
            ringResult.asserts().stdoutContains("Uncompressed load");
            ringResult.asserts().stdoutContains("Generation");
            ringResult.asserts().stdoutContains("Uptime");
            ringResult.asserts().stdoutContains("Heap Memory");
            RestartFramework.at("after_info_command")
                .on(cluster)
                .restart("node")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }
    }

    @Test
    public void testVersionIncludesGitSHAWhenVerbose() throws Throwable
    {
        NODE.nodetoolResult("version")
            .asserts()
            .success()
            .stdoutNotContains("GitSHA:");
        RestartFramework.at("after_version_command")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        NODE.nodetoolResult("version", "--verbose")
            .asserts()
            .success()
            .stdoutContains("GitSHA:");
        RestartFramework.at("after_verbose_version")
            .on(CLUSTER)
            .restart("node")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }
}
