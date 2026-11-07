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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 枚举字段的解析、大小写匹配、错误提示与写出。
 */
class EnumBindingTest {

    @YamlConfig
    public static class PluginSetting {
        public String language = "zh-CN.yml";
        public String version;
        public Authentication authentication = new Authentication();
        public Registration registration = new Registration();

        public static class Authentication {
            public boolean enabled;
            public Type type = Type.local;

            public enum Type {
                official,
                local,
                proxy,
                custom
            }
        }

        public static class Registration {
            public boolean enabled;
        }
    }

    @YamlConfig
    public static class UpperCfg {
        public Mode mode = Mode.AUTO;

        public enum Mode {
            AUTO,
            MANUAL
        }
    }

    @Test
    void testParseEnumInNestedClass(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "nested.yml",
                "language: en-US.yml\n"
                        + "version: 1.0.0\n"
                        + "authentication:\n"
                        + "  enabled: true\n"
                        + "  type: official\n"
                        + "registration:\n"
                        + "  enabled: false\n");

        PluginSetting setting = new YamlConfigurationParser().parse(file, PluginSetting.class);

        assertEquals("en-US.yml", setting.language);
        assertEquals("1.0.0", setting.version);
        assertTrue(setting.authentication.enabled);
        assertEquals(PluginSetting.Authentication.Type.official, setting.authentication.type);
        assertEquals(false, setting.registration.enabled);
    }

    @Test
    void testEnumMatchingIgnoresCase(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "case.yml",
                "authentication:\n"
                        + "  type: OFFICIAL\n");

        PluginSetting setting = new YamlConfigurationParser().parse(file, PluginSetting.class);

        assertEquals(PluginSetting.Authentication.Type.official, setting.authentication.type);
    }

    @Test
    void testUpperCaseConstantsMatchLowerCaseYaml(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "upper.yml", "mode: manual\n");

        UpperCfg cfg = new YamlConfigurationParser().parse(file, UpperCfg.class);

        assertEquals(UpperCfg.Mode.MANUAL, cfg.mode);
    }

    @Test
    void testMissingEnumKeepsDefault(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "missing.yml",
                "authentication:\n"
                        + "  enabled: true\n");

        PluginSetting setting = new YamlConfigurationParser().parse(file, PluginSetting.class);

        assertEquals(PluginSetting.Authentication.Type.local, setting.authentication.type);
    }

    @Test
    void testUnknownEnumValueThrowsWithAllowedValues(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "unknown.yml",
                "authentication:\n"
                        + "  type: magic\n");

        BindingException exception = assertThrows(BindingException.class,
                () -> new YamlConfigurationParser().parse(file, PluginSetting.class));

        assertTrue(exception.getMessage().contains("magic"), exception.getMessage());
        assertTrue(exception.getMessage().contains("official"), exception.getMessage());
        assertTrue(exception.getMessage().contains("allowed values"), exception.getMessage());
    }

    @Test
    void testWriteAndReadBackEnum(@TempDir Path tempDir) throws Exception {
        File file = write(tempDir, "write.yml",
                "# 认证配置\n"
                        + "authentication:\n"
                        + "  enabled: true\n"
                        + "  type: local      # 认证方式\n");

        YamlConfigurationParser parser = new YamlConfigurationParser();
        PluginSetting setting = parser.parse(file, PluginSetting.class);
        assertEquals(PluginSetting.Authentication.Type.local, setting.authentication.type);

        setting.authentication.type = PluginSetting.Authentication.Type.proxy;
        parser.write(file, setting);

        String written = Files.readString(file.toPath());
        assertTrue(written.contains("# 认证配置"), written);
        assertTrue(written.contains("type: proxy"), written);

        PluginSetting reloaded = parser.parse(file, PluginSetting.class);
        assertEquals(PluginSetting.Authentication.Type.proxy, reloaded.authentication.type);
    }

    private static File write(Path dir, String name, String content) throws Exception {
        File file = dir.resolve(name).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }
}
