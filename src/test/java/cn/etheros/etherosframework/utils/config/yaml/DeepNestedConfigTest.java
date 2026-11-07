/*
 * This file is part of EtherosFramework, licensed under the Apache License, Version 2.0.
 *
 *  Copyright (c) EtherosFramework <etheros@126.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package cn.etheros.etherosframework.utils.config.yaml;

import cn.etheros.etherosframework.utils.config.yaml.annotation.ConfigNode;
import cn.etheros.etherosframework.utils.config.yaml.annotation.YamlConfig;
import cn.etheros.etherosframework.utils.config.yaml.core.ClassStructureScanner;
import cn.etheros.etherosframework.utils.config.yaml.core.FieldBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多层嵌套节点的扫描、绑定与写出。
 */
class DeepNestedConfigTest {

    @YamlConfig
    public static class Root {
        public String name = "root";
        public Server server = new Server();

        public static class Server {
            public String host = "localhost";
            public Network network = new Network();

            public static class Network {
                public int port = 25565;
                @ConfigNode("bind-address")
                public String bindAddress = "0.0.0.0";
                public List<String> whitelist;
            }
        }
    }

    @YamlConfig
    public static class FlatRoot {
        public Database database = new Database();

        public static class Database {
            public Auth auth = new Auth();
        }

        public static class Auth {
            public String token;
        }
    }

    @YamlConfig
    public static class SelfRefRoot {
        public Node node = new Node();

        public static class Node {
            public String name = "default";
            public Node child;
        }
    }

    @Test
    void testScanFlatMapContainsDeepPaths() {
        FieldBinding.ScanResult result = ClassStructureScanner.scan(Root.class);

        assertEquals(String.class, result.toFlatMap().get("name"));
        assertEquals(String.class, result.toFlatMap().get("server.host"));
        assertEquals(int.class, result.toFlatMap().get("server.network.port"));
        assertEquals(String.class, result.toFlatMap().get("server.network.bind-address"));
        assertEquals(List.class, result.toFlatMap().get("server.network.whitelist"));
    }

    @Test
    void testParseThreeLevelNesting(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "deep.yml",
                "name: prod\n"
                        + "server:\n"
                        + "  host: 10.0.0.1\n"
                        + "  network:\n"
                        + "    port: 8080\n"
                        + "    bind-address: 127.0.0.1\n"
                        + "    whitelist:\n"
                        + "      - alice\n"
                        + "      - bob\n");

        Root root = new YamlConfigurationParser().parse(file, Root.class);

        assertEquals("prod", root.name);
        assertEquals("10.0.0.1", root.server.host);
        assertEquals(8080, root.server.network.port);
        assertEquals("127.0.0.1", root.server.network.bindAddress);
        assertEquals(List.of("alice", "bob"), root.server.network.whitelist);
    }

    @Test
    void testMissingNodesKeepDefaults(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "partial.yml", "name: dev\n");

        Root root = new YamlConfigurationParser().parse(file, Root.class);

        assertEquals("dev", root.name);
        assertEquals("localhost", root.server.host);
        assertEquals(25565, root.server.network.port);
        assertEquals("0.0.0.0", root.server.network.bindAddress);
        assertNull(root.server.network.whitelist);
    }

    @Test
    void testWriteDeepValuesPreservesComments(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "write.yml",
                "# 顶层注释\n"
                        + "name: old\n"
                        + "server:\n"
                        + "  host: localhost\n"
                        + "  # 网络段\n"
                        + "  network:\n"
                        + "    port: 25565      # 监听端口\n"
                        + "    bind-address: 0.0.0.0\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Root root = parser.parse(file, Root.class);
        assertEquals(25565, root.server.network.port);

        root.server.network.port = 8080;
        root.server.network.bindAddress = "127.0.0.1";
        parser.write(file, root);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("# 顶层注释"), written);
        assertTrue(written.contains("# 网络段"), written);
        assertTrue(written.contains("# 监听端口"), written);
        assertTrue(written.contains("port: 8080"), written);
        assertTrue(written.contains("bind-address: 127.0.0.1"), written);
    }

    @Test
    void testNodeClassDeclaredAtRootLevel(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "flat.yml",
                "database:\n"
                        + "  auth:\n"
                        + "    token: s3cret\n");

        FlatRoot root = new YamlConfigurationParser().parse(file, FlatRoot.class);

        assertEquals("s3cret", root.database.auth.token);
    }

    @Test
    void testSelfReferencingNodeTreatedAsLeaf(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "self.yml",
                "node:\n"
                        + "  name: hi\n");

        SelfRefRoot root = new YamlConfigurationParser().parse(file, SelfRefRoot.class);

        assertEquals("hi", root.node.name);
        assertNull(root.node.child);
    }

    private static File write(Path dir, String name, String content) throws Exception {
        File file = dir.resolve(name).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
