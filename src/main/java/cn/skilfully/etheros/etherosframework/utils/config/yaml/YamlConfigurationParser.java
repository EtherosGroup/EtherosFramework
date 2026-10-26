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

import cn.skilfully.etheros.etherosframework.utils.config.yaml.annotation.YamlConfig;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.ClassStructureScanner;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.FieldBinding;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlDataBinder;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlReader;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.core.YamlWriter;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.exception.BindingException;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.exception.YamlParseException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用于Yaml类型配置文件的解析器，写入时不删除注释。
 */
public class YamlConfigurationParser {

    /**
     * 解析 YAML 配置文件，根据目标类的结构自动映射节点并绑定数据。
     *
     * <p>解析规则：
     * <ul>
     *   <li>静态内部类名首字母自动转为小写，匹配 YAML 中的一级节点</li>
     *   <li>字段名采用驼峰转短横线规则（如 {@code nodeOne} → {@code node-one}）</li>
     *   <li>YAML 中缺失的键会保留字段上定义的默认值，不会覆盖</li>
     *   <li>支持 {@code String}、基本类型及其包装类、{@link List} {@link Map} 以及嵌套静态类</li>
     *   <li>保留 YAML 中的注释，可在写出时恢复</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * YamlConfiguration config = parser.parse(
     *     new File("config.yml"),
     *     PluginConfig.class
     * );
     * }</pre>
      * <p>配置映射的目标类示例：
     * <pre>{@code
     * @YamlConfig
     * public class Example {
     *     private String topLevel = "default";
     *
     *     // 静态内部类对应的字段需手动声明，Scanner 通过类型匹配识别嵌套
     *     private Server server = new Server();
     *     private AuthSetting authSetting = new AuthSetting();
     *
     *     public static class Server {
     *         private String name;             // server.name
     *         private Boolean enabled = true;  // server.enabled
     *     }
     *     public static class AuthSetting {    // auth-setting
     *         private List<String> banndList;  // auth-setting.bannd-list
     *         @ConfigNode("kick-message")
     *         private String message;          // auth-setting.kick-message
     *     }
     * }
     * }</pre>
     *
     * @param configFile  YAML 配置文件，文件必须存在且格式合法
     * @param targetClass 配置映射的目标类，应被 {@link YamlConfig @YamlConfig} 注解标记
     * @param <T>         目标类的类型参数
     * @return 绑定完数据后的配置对象，字段值来源于 YAML 或保留类定义中的默认值
     * @throws IllegalArgumentException 如果 {@code configFile} 不存在或不是文件
     * @throws YamlParseException       如果 YAML 格式有误或解析失败
     * @throws BindingException         如果字段类型与 YAML 值类型不兼容或反射操作失败
     */
    public <T> T parse(File configFile, Class<T> targetClass) throws IllegalArgumentException, YamlParseException, BindingException {
        if (configFile == null) {
            throw new IllegalArgumentException("configFile must not be null");
        }
        if (!configFile.exists() || !configFile.isFile()) {
            throw new IllegalArgumentException("configFile does not exist or is not a file: " + configFile.getAbsolutePath());
        }
        if (!targetClass.isAnnotationPresent(YamlConfig.class)) {
            throw new IllegalArgumentException("targetClass must be annotated with @YamlConfig: " + targetClass.getName());
        }

        FieldBinding.ScanResult scanResult = ClassStructureScanner.scan(targetClass);

        Map<String, Class<?>> flatTypes = scanResult.toFlatMap();
        Set<String> targetKeys = flatTypes.keySet();

        Map<String, Object> yamlData = parseYaml(configFile, targetKeys);

        return YamlDataBinder.bind(yamlData, targetClass, scanResult);
    }

    /**
     * 将配置对象写入 YAML 文件，保留原始注释和格式。
     *
     * @param file       YAML 配置文件
     * @param configData 配置对象实例（被 {@code @YamlConfig} 标记的类）
     * @throws IllegalArgumentException 如果 {@code file} 为 {@code null}
     * @throws BindingException         如果反射读取字段失败
     */
    public void write(File file, Object configData) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("file does not exist or is not a file: " + file.getAbsolutePath());
        }
        if (configData == null) {
            throw new IllegalArgumentException("configData must not be null");
        }

        FieldBinding.ScanResult writeScanResult =
                ClassStructureScanner.scan(configData.getClass());
        YamlWriter.replace(file, configData, writeScanResult);
    }

    private Map<String, Object> parseYaml(File configFile, Set<String> targetKeys) throws YamlParseException {
        return YamlReader.read(configFile, targetKeys);
    }
}
