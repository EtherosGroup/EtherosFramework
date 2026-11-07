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
import cn.etheros.etherosframework.utils.config.yaml.exception.BindingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集合（List / Set / Queue）与数组字段的元素级类型转换。
 */
class CollectionBindingTest {

    @YamlConfig
    public static class Cfg {
        public List<Mode> modes;
        public List<Integer> numbers;
        public List<Long> longs;
        public List<String> strings;
        public List<Boolean> flags;
        public List raw;
        public Set<Mode> modeSet;
        public LinkedHashSet<Mode> orderedSet;
        public SortedSet<Mode> sortedSet;
        public Queue<Mode> modeQueue;
        public Collection<Mode> modeCollection;
        public Mode[] modeArray;
        public String[] stringArray;
        public int[] intArray;
        public Integer[] integerArray;
        public Server server = new Server();

        public static class Server {
            public Network network = new Network();

            public static class Network {
                public List<Mode> modes;
            }
        }

        public enum Mode {
            official,
            local,
            proxy,
            custom
        }
    }

    @Test
    void testListElementsConvertedToGenericType(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "lists.yml",
                "modes:\n"
                        + "  - official\n"
                        + "  - LOCAL\n"
                        + "  - Proxy\n"
                        + "numbers:\n"
                        + "  - 1\n"
                        + "  - 2\n"
                        + "longs:\n"
                        + "  - 9999999999\n"
                        + "  - -42\n"
                        + "strings:\n"
                        + "  - 123\n"
                        + "  - hello\n"
                        + "flags:\n"
                        + "  - true\n"
                        + "  - false\n"
                        + "raw:\n"
                        + "  - a\n"
                        + "  - 7\n"
                        + "server:\n"
                        + "  network:\n"
                        + "    modes:\n"
                        + "      - custom\n");

        Cfg cfg = new YamlConfigurationParser().parse(file, Cfg.class);

        assertEquals(List.of(Cfg.Mode.official, Cfg.Mode.local, Cfg.Mode.proxy), cfg.modes);
        assertEquals(List.of(1, 2), cfg.numbers);
        assertEquals(List.of(9999999999L, -42L), cfg.longs);
        assertEquals(List.of("123", "hello"), cfg.strings);
        assertEquals(List.of(true, false), cfg.flags);
        assertEquals(List.of("a", 7), cfg.raw, "无泛型参数的 List 保持元素原样");
        assertEquals(List.of(Cfg.Mode.custom), cfg.server.network.modes);

        for (Cfg.Mode mode : cfg.modes) {
            assertEquals(Cfg.Mode.class, mode.getClass(), "元素应为枚举实例");
        }
    }

    @Test
    void testMissingListKeepsDefault(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "missing.yml",
                "modes:\n"
                        + "  - local\n");

        Cfg cfg = new YamlConfigurationParser().parse(file, Cfg.class);

        assertEquals(List.of(Cfg.Mode.local), cfg.modes);
        assertNull(cfg.numbers);
        assertNull(cfg.server.network.modes);
    }

    @Test
    void testInvalidListElementThrows(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "invalid.yml",
                "modes:\n"
                        + "  - official\n"
                        + "  - magic\n");

        BindingException exception = assertThrows(BindingException.class,
                () -> new YamlConfigurationParser().parse(file, Cfg.class));

        assertTrue(exception.getMessage().contains("magic"), exception.getMessage());
        assertTrue(exception.getMessage().contains("allowed values"), exception.getMessage());
    }

    @Test
    void testWriteAndReadBackList(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "write.yml",
                "# 列表配置\n"
                        + "modes:\n"
                        + "  - local      # 默认模式\n"
                        + "  - official\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        assertEquals(List.of(Cfg.Mode.local, Cfg.Mode.official), cfg.modes);

        cfg.modes = List.of(Cfg.Mode.proxy, Cfg.Mode.custom);
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("# 列表配置"), written);
        assertTrue(written.contains("- proxy"), written);
        assertTrue(written.contains("- custom"), written);

        Cfg reloaded = parser.parse(file, Cfg.class);
        assertEquals(List.of(Cfg.Mode.proxy, Cfg.Mode.custom), reloaded.modes);
    }

    @Test
    void testSetQueueArrayBinding(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "collections.yml",
                "mode-set:\n"
                        + "  - official\n"
                        + "  - proxy\n"
                        + "  - official\n"
                        + "ordered-set:\n"
                        + "  - local\n"
                        + "  - official\n"
                        + "sorted-set:\n"
                        + "  - proxy\n"
                        + "  - official\n"
                        + "  - local\n"
                        + "mode-queue:\n"
                        + "  - local\n"
                        + "  - custom\n"
                        + "mode-collection:\n"
                        + "  - official\n"
                        + "  - local\n"
                        + "mode-array:\n"
                        + "  - official\n"
                        + "  - proxy\n"
                        + "string-array:\n"
                        + "  - 123\n"
                        + "  - hello\n"
                        + "int-array:\n"
                        + "  - 1\n"
                        + "  - 2\n"
                        + "  - 3\n"
                        + "integer-array:\n"
                        + "  - 4\n"
                        + "  - 5\n");

        Cfg cfg = new YamlConfigurationParser().parse(file, Cfg.class);

        assertInstanceOf(LinkedHashSet.class, cfg.modeSet);
        assertEquals(List.of(Cfg.Mode.official, Cfg.Mode.proxy),
                List.copyOf(cfg.modeSet), "Set 去重且保持 YAML 顺序");
        assertEquals(List.of(Cfg.Mode.local, Cfg.Mode.official), List.copyOf(cfg.orderedSet));
        assertInstanceOf(TreeSet.class, cfg.sortedSet);
        assertEquals(List.of(Cfg.Mode.official, Cfg.Mode.local, Cfg.Mode.proxy),
                List.copyOf(cfg.sortedSet), "SortedSet 按枚举自然顺序");
        assertEquals(List.of(Cfg.Mode.local, Cfg.Mode.custom), List.copyOf(cfg.modeQueue));
        assertEquals(List.of(Cfg.Mode.official, Cfg.Mode.local), List.copyOf(cfg.modeCollection));

        assertArrayEquals(new Cfg.Mode[]{Cfg.Mode.official, Cfg.Mode.proxy}, cfg.modeArray);
        assertArrayEquals(new String[]{"123", "hello"}, cfg.stringArray);
        assertArrayEquals(new int[]{1, 2, 3}, cfg.intArray);
        assertArrayEquals(new Integer[]{4, 5}, cfg.integerArray);
    }

    @Test
    void testInvalidSetElementThrows(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "invalid-set.yml",
                "mode-set:\n"
                        + "  - magic\n");

        BindingException exception = assertThrows(BindingException.class,
                () -> new YamlConfigurationParser().parse(file, Cfg.class));

        assertTrue(exception.getMessage().contains("magic"), exception.getMessage());
        assertTrue(exception.getMessage().contains("allowed values"), exception.getMessage());
    }

    @Test
    void testWriteAndReadBackSetAndArray(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "write-collections.yml",
                "# 集合配置\n"
                        + "mode-set:\n"
                        + "  - local\n"
                        + "mode-array:\n"
                        + "  - local\n"
                        + "  - official\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        Cfg cfg = parser.parse(file, Cfg.class);
        assertEquals(List.of(Cfg.Mode.local), List.copyOf(cfg.modeSet));

        cfg.modeSet = new LinkedHashSet<>(List.of(Cfg.Mode.proxy, Cfg.Mode.custom));
        cfg.modeArray = new Cfg.Mode[]{Cfg.Mode.custom};
        parser.write(file, cfg);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("# 集合配置"), written);
        assertTrue(written.contains("- proxy"), written);
        assertTrue(written.contains("- custom"), written);

        Cfg reloaded = parser.parse(file, Cfg.class);
        assertEquals(List.of(Cfg.Mode.proxy, Cfg.Mode.custom), List.copyOf(reloaded.modeSet));
        assertArrayEquals(new Cfg.Mode[]{Cfg.Mode.custom}, reloaded.modeArray);
    }

    private static File write(Path dir, String name, String content) throws Exception {
        File file = dir.resolve(name).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
