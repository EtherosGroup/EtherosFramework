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
package cn.skilfully.etheros.etherosframework.utils.config.yaml;

import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.ClassStructureScanner;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.FieldBinding;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlDataBinder;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlReader;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigurationParserTest {

    @Test
    void testClassStructureScanning() {
        FieldBinding.ScanResult result = ClassStructureScanner.scan(TestConfig.class);

        assertEquals(TestConfig.class, result.targetClass());
        assertEquals(4, result.rootBindings().size(),
                "topLevel + enabled + server + authSetting");

        boolean hasTopLevel = false;
        boolean hasEnabled = false;
        boolean hasServer = false;
        boolean hasAuthSetting = false;
        for (FieldBinding b : result.rootBindings()) {
            if ("top-level".equals(b.yamlPath()) && b.isLeaf()) hasTopLevel = true;
            if ("enabled".equals(b.yamlPath()) && b.isLeaf()) hasEnabled = true;
            if ("server".equals(b.yamlPath()) && !b.isLeaf()) {
                hasServer = true;
                assertNotNull(b.nestedClass());
                assertNotNull(b.field(), "Field must be non-null (user-declared)");
                assertEquals(2, b.children().size());
            }
            if ("auth-setting".equals(b.yamlPath()) && !b.isLeaf()) {
                hasAuthSetting = true;
                assertNotNull(b.nestedClass());
                assertNotNull(b.field(), "Field must be non-null (user-declared)");
                assertEquals(2, b.children().size());
            }
        }
        assertTrue(hasTopLevel);
        assertTrue(hasEnabled);
        assertTrue(hasServer);
        assertTrue(hasAuthSetting);
    }

    @Test
    void testDataBinding() throws Exception {
        FieldBinding.ScanResult result = ClassStructureScanner.scan(TestConfig.class);

        Map<String, Object> yamlData = new LinkedHashMap<>();
        yamlData.put("top-level", "hello world");
        yamlData.put("enabled", false);
        yamlData.put("server.name", "MyServer");
        yamlData.put("server.enabled", false);
        yamlData.put("auth-setting.bannd-list", List.of("player1", "player2"));
        yamlData.put("auth-setting.kick-message", "You are kicked!");

        TestConfig config = YamlDataBinder.bind(yamlData, TestConfig.class, result);

        assertNotNull(config);
        assertEquals("hello world", getField(config, "topLevel"));
        assertEquals(false, getField(config, "enabled"));

        Object server = getField(config, "server");
        assertNotNull(server);
        assertEquals("MyServer", getField(server, "name"));
        assertEquals(false, getField(server, "enabled"));

        Object authSetting = getField(config, "authSetting");
        assertNotNull(authSetting);
        assertEquals("You are kicked!", getField(authSetting, "message"));
        assertEquals(List.of("player1", "player2"), getField(authSetting, "banndList"));
    }

    @Test
    void testDefaultValuesPreserved() throws Exception {
        FieldBinding.ScanResult result = ClassStructureScanner.scan(TestConfig.class);

        TestConfig config = YamlDataBinder.bind(new LinkedHashMap<>(), TestConfig.class, result);

        assertEquals("default", getField(config, "topLevel"),
                "Default value should be preserved");
        assertEquals(true, getField(config, "enabled"),
                "Default value should be preserved");

        Object server = getField(config, "server");
        assertNotNull(server, "server field default = new Server() should be preserved");
        assertNull(getField(server, "name"), "Inner field should keep null default");
        assertEquals(true, getField(server, "enabled"), "Inner field should keep true default");
    }

    @Test
    void testToFlatMap() {
        FieldBinding.ScanResult result = ClassStructureScanner.scan(TestConfig.class);
        Map<String, Class<?>> flat = result.toFlatMap();

        assertEquals(6, flat.size(), "2 top-level + 2 server leaves + 2 auth-setting leaves");
        assertEquals(String.class, flat.get("top-level"));
        assertEquals(Boolean.class, flat.get("enabled"));
        assertEquals(String.class, flat.get("server.name"));
        assertEquals(Boolean.class, flat.get("server.enabled"));
        assertEquals(String.class, flat.get("auth-setting.kick-message"));
        assertTrue(List.class.isAssignableFrom(flat.get("auth-setting.bannd-list")));
    }

    @Test
    void testYamlReaderAndWriter(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("config.yml").toFile();
        String yamlContent = "# 服务器配置\nserver:\n  name: MyServer\n  enabled: false\n\n# 认证设置\nauth-setting:\n  bannd-list:\n    - player1\n    - player2\n  # 自定义键名\n  kick-message: You are kicked!\n";
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write(yamlContent);
        }

        Set<String> targetKeys = Set.of("server.name", "server.enabled",
                "auth-setting.bannd-list", "auth-setting.kick-message");
        Map<String, Object> data = YamlReader.read(yamlFile, targetKeys);

        assertEquals("MyServer", data.get("server.name"));
        assertEquals(false, data.get("server.enabled"));
        assertEquals("You are kicked!", data.get("auth-setting.kick-message"));
        assertInstanceOf(List.class, data.get("auth-setting.bannd-list"));
        assertEquals(List.of("player1", "player2"), data.get("auth-setting.bannd-list"));

        FieldBinding.ScanResult scanResult = ClassStructureScanner.scan(TestConfig.class);
        TestConfig config = YamlDataBinder.bind(data, TestConfig.class, scanResult);
        assertEquals("MyServer", getField(getField(config, "server"), "name"));

        YamlWriter.replace(yamlFile, config, scanResult);

        String written = String.join("\n", java.nio.file.Files.readAllLines(yamlFile.toPath()));
        assertTrue(written.contains("# 服务器配置"), "Comment should be preserved");
        assertTrue(written.contains("# 认证设置"), "Comment should be preserved");
        assertTrue(written.contains("# 自定义键名"), "Comment should be preserved");
    }

    @Test
    void testFullParsePipeline(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("test.yml").toFile();
        String yamlContent = "top-level: hello world\nenabled: false\n\nserver:\n  name: MyServer\n  enabled: false\n\nauth-setting:\n  bannd-list:\n    - player1\n    - player2\n  kick-message: You are kicked!\n";
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write(yamlContent);
        }

        YamlConfigurationParser parser = new YamlConfigurationParser();
        TestConfig config = parser.parse(yamlFile, TestConfig.class);

        assertNotNull(config);
        assertEquals("hello world", getField(config, "topLevel"));
        assertEquals(false, getField(config, "enabled"));

        Object server = getField(config, "server");
        assertEquals("MyServer", getField(server, "name"));
        assertEquals(false, getField(server, "enabled"));

        Object auth = getField(config, "authSetting");
        assertEquals("You are kicked!", getField(auth, "message"));
        assertEquals(List.of("player1", "player2"), getField(auth, "banndList"));
    }

    @Test
    void testYamlValueTypes() {
        assertEquals("hello", YamlReader.parseValue("hello"));
        assertEquals(true, YamlReader.parseValue("true"));
        assertEquals(false, YamlReader.parseValue("false"));
        assertEquals(null, YamlReader.parseValue("null"));
        assertEquals(null, YamlReader.parseValue("~"));
        assertEquals(42, YamlReader.parseValue("42"));
        assertEquals(9999999999L, YamlReader.parseValue("9999999999"));
        assertEquals(3.14, YamlReader.parseValue("3.14"));
        assertEquals("hello", YamlReader.parseValue("'hello'"));
        assertEquals("hello", YamlReader.parseValue("\"hello\""));
    }

    @Test
    void testYamlReaderMissingKeys(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("partial.yml").toFile();
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write("server:\n  name: OnlyName\n");
        }

        Set<String> targetKeys = Set.of("server.name", "server.enabled");
        Map<String, Object> data = YamlReader.read(yamlFile, targetKeys);

        assertEquals("OnlyName", data.get("server.name"));
        assertNull(data.get("server.enabled"), "Missing key should not be in result");
    }

    @Test
    void testYamlWriterPreservesComments(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("comment-test.yml").toFile();
        String original = "# top comment\ntop-level: old\n# server comment\nserver:\n  name: old\n  enabled: true\n";
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write(original);
        }

        FieldBinding.ScanResult scanResult = ClassStructureScanner.scan(TestConfig.class);
        TestConfig config = new TestConfig();
        YamlWriter.replace(yamlFile, config, scanResult);

        String written = String.join("\n", java.nio.file.Files.readAllLines(yamlFile.toPath()));
        assertTrue(written.contains("# top comment"), "Top comment should be preserved");
        assertTrue(written.contains("# server comment"), "Server comment should be preserved");
        assertTrue(written.contains("top-level: default"), "Value should be updated");
    }

    @Test
    void testYamlReaderListOnly(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("list.yml").toFile();
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write("banned:\n  - player1\n  - player2\n");
        }
        Set<String> keys = Set.of("banned");
        Map<String, Object> data = YamlReader.read(yamlFile, keys);

        assertNotNull(data.get("banned"), "List should be parsed for key 'banned'");
        List<?> list = (List<?>) data.get("banned");
        assertEquals(2, list.size());
        assertEquals("player1", list.get(0));
        assertEquals("player2", list.get(1));
    }

    @Test
    void testYamlReaderNestedList(@TempDir Path tempDir) throws Exception {
        File yamlFile = tempDir.resolve("nested-list.yml").toFile();
        try (FileWriter fw = new FileWriter(yamlFile)) {
            fw.write("auth-setting:\n  bannd-list:\n    - player1\n    - player2\n  kick-message: hello\n");
        }

        Set<String> keys = Set.of("auth-setting.bannd-list", "auth-setting.kick-message");
        Map<String, Object> data = YamlReader.read(yamlFile, keys);

        assertNotNull(data.get("auth-setting.bannd-list"),
                "Nested list should be parsed. Data: " + data);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) data.get("auth-setting.bannd-list");
        assertEquals(2, list.size(), "Should have 2 items. Data: " + data);
        assertEquals("player1", list.get(0));
        assertEquals("player2", list.get(1));
        assertEquals("hello", data.get("auth-setting.kick-message"));
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
