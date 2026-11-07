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

import cn.etheros.etherosframework.utils.config.yaml.annotation.YamlConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写回时的注释保留：标量行的行内注释、列表项的条目注释与块内注释。
 */
class CommentPreservationTest {

    @YamlConfig
    public static class Cfg {
        public String mode = "old";
        public List<String> items;
        public Server server = new Server();

        public static class Server {
            public String host = "old-host";
        }
    }

    @Test
    void testScalarInlineCommentKeepsSpacing(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "scalar.yml",
                "mode: old            # a text（行内注释）\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.mode = "new";
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("mode: new            # a text（行内注释）"), written);
    }

    @Test
    void testNestedScalarInlineComment(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "nested.yml",
                "server:\n"
                        + "  host: old-host # 服务器地址\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.server.host = "new-host";
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("host: new-host # 服务器地址"), written);
    }

    @Test
    void testListCommentsFollowMatchedItems(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "list.yml",
                "items:\n"
                        + "  - one    # 第一个\n"
                        + "  # 说明 two\n"
                        + "  - two\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.items = List.of("two", "one", "three");
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        List<String> lines = Arrays.asList(written.split("\\R"));

        assertEquals("items:", lines.get(0), written);
        assertEquals("  # 说明 two", lines.get(1), written);
        assertEquals("  - two", lines.get(2), written);
        assertEquals("  - one    # 第一个", lines.get(3), written);
        assertEquals("  - three", lines.get(4), written);
    }

    @Test
    void testRemovedItemCommentDropped(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "removed.yml",
                "items:\n"
                        + "  - one    # 第一个\n"
                        + "  - two\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.items = List.of("two");
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertFalse(written.contains("第一个"), written);
        assertFalse(written.contains("- one"), written);
        assertTrue(written.contains("- two"), written);
    }

    @Test
    void testBlockCommentOutsideListNotDuplicated(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "outside.yml",
                "# 列表标题\n"
                        + "items:\n"
                        + "  - one\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.items = List.of("one");
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertEquals(1, countOccurrences(written, "# 列表标题"), written);
    }

    @Test
    void testWriteTwiceIsStable(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "stable.yml",
                "# 文件头\n"
                        + "mode: old    # 模式说明\n"
                        + "items:\n"
                        + "  - one    # 第一个\n"
                        + "  # 说明 two\n"
                        + "  - two\n"
                        + "server:\n"
                        + "  host: old-host # 地址\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        cfg.mode = "new";
        cfg.items = List.of("two", "one");
        cfg.server.host = "new-host";
        parser.write(file, cfg);
        String first = Files.readString(file.toPath());

        Cfg reloaded = parser.parse(file, Cfg.class);
        parser.write(file, reloaded);
        String second = Files.readString(file.toPath());

        assertEquals(first, second, "重复写回不应产生差异");
        assertEquals(1, countOccurrences(second, "# 模式说明"), second);
        assertEquals(1, countOccurrences(second, "# 第一个"), second);
        assertEquals(1, countOccurrences(second, "# 说明 two"), second);
        assertEquals(1, countOccurrences(second, "# 地址"), second);
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(part, index)) >= 0) {
            count++;
            index += part.length();
        }
        return count;
    }

    private static File write(Path dir, String name, String content) throws Exception {
        File file = dir.resolve(name).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
