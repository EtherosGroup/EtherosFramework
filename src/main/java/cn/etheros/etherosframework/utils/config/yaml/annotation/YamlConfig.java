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
package cn.etheros.etherosframework.utils.config.yaml.annotation;

import cn.etheros.etherosframework.utils.config.yaml.YamlConfigurationParser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为 YAML 配置映射的目标类。
 *
 * <h3>基本用法</h3>
 * <pre>{@code
 * @YamlConfig
 * public class PluginConfig {
 *     private String name = "default";
 *
 *     // 静态内部类对应的字段需手动声明
 *     private Server server = new Server();
 *
 *     public static class Server {
 *         private String host;
 *         private int port = 25565;
 *     }
 * }
 * }</pre>
 *
 * <h3>嵌套字段</h3>
 * <p>字段类型是目标类内部声明的静态类时，按该类的字段递归展开为子节点，层数不限。
 * 对应字段需用户手动声明：
 * <pre>{@code
 * private Server server;                // 解析器在需要时创建默认实例
 * private Server server = new Server(); // 保留字段上定义的默认值
 * }</pre>
 * <p>同一类型已出现在当前绑定路径上（自引用）时按叶子节点处理，不会无限递归。
 *
 * <h3>配合 Lombok</h3>
 * <p>可叠加 {@code @Data} 来自动生成 getter/setter（可选）：
 * <pre>{@code
 * @YamlConfig @Data
 * public class PluginConfig { ... }
 * }</pre>
 * 框架本身不生成访问器方法，所有注入通过反射完成。
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>类必须具备无参构造器（或默认构造器）</li>
 *   <li>静态内部类同样需要无参构造器</li>
 * </ul>
 *
 * @see YamlConfigurationParser
 * @see ConfigNode
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface YamlConfig {
}
