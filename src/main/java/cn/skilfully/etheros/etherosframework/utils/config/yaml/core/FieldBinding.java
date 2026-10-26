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
package cn.skilfully.etheros.etherosframework.utils.config.yaml.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示一个 YAML 配置路径与目标字段之间的绑定关系。
 *
 * <p>叶子绑定直接映射到 YAML 值，嵌套绑定通过静态内部类的字段递归展开。
 */
public final class FieldBinding {

    private final String yamlPath;
    private final Field field;
    private Class<?> nestedClass;
    private final List<FieldBinding> children;

    /**
     * 创建叶子绑定。
     *
     * @param yamlPath 对应 YAML 文件中的键路径，如 {@code "top-level"}
     * @param field    目标类的 {@link Field} 对象
     */
    FieldBinding(String yamlPath, Field field) {
        this.yamlPath = yamlPath;
        this.field = field;
        this.children = Collections.emptyList();
    }

    /**
     * 创建嵌套绑定。
     *
     * @param yamlPath 对应 YAML 一级节点的键，如 {@code "server"}
     * @param field    外层类上的字段（用户手动声明的 {@code private Server server;}）
     * @param children 内部类的叶子绑���列表
     */
    FieldBinding(String yamlPath, Field field, List<FieldBinding> children) {
        this.yamlPath = yamlPath;
        this.field = field;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    void setNestedClass(Class<?> nestedClass) {
        this.nestedClass = nestedClass;
    }

    /** 对应 YAML 文件中的路径，叶子绑定为完整路径，嵌套绑定为其自身节点键。 */
    public String yamlPath() {
        return yamlPath;
    }

    /** 目标类上的字段名称。 */
    public String fieldName() {
        return field.getName();
    }

    /** 目标类上的反射 Field 对象。嵌套绑定的 field = 用户手动声明的字段。 */
    public Field field() {
        return field;
    }

    /** 嵌套绑定的内部类类型，叶子绑定返回 {@code null}。 */
    public Class<?> nestedClass() {
        return nestedClass;
    }

    /** 字段类型。嵌套绑定返回内部类类型，叶子绑定返回字段自身类型。 */
    public Class<?> type() {
        return field.getType();
    }

    /** 无子绑定时为 {@code true}。 */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** 嵌套绑定的子绑定列表。 */
    public List<FieldBinding> children() {
        return children;
    }

    /**
     * 扫描结果，包含目标类及其所有绑定的根节点树。
     */
    public static final class ScanResult {

        private final Class<?> targetClass;
        private final List<FieldBinding> rootBindings;

        ScanResult(Class<?> targetClass, List<FieldBinding> rootBindings) {
            this.targetClass = targetClass;
            this.rootBindings = Collections.unmodifiableList(new ArrayList<>(rootBindings));
        }

        /** 被扫���的目标类。 */
        public Class<?> targetClass() {
            return targetClass;
        }

        /** 根绑定列表（顶层字段 + 嵌套静态内部类）。 */
        public List<FieldBinding> rootBindings() {
            return rootBindings;
        }

        /**
         * 将所有叶子绑定展开为扁平映射，键为完整 YAML 路径，值为字段类型。
         *
         * <p>用于指导 YAML 文件读取时仅解析关心的路径。
         *
         * @return 不可修改的扁平映射，如 {@code "server.name" → String.class}
         */
        public Map<String, Class<?>> toFlatMap() {
            Map<String, Class<?>> flat = new LinkedHashMap<>();
            for (FieldBinding binding : rootBindings) {
                collectFlat(flat, binding);
            }
            return Collections.unmodifiableMap(flat);
        }

        private static void collectFlat(Map<String, Class<?>> flat, FieldBinding binding) {
            if (binding.isLeaf()) {
                flat.put(binding.yamlPath(), binding.type());
            } else {
                for (FieldBinding child : binding.children()) {
                    collectFlat(flat, child);
                }
            }
        }

        static ScanResult of(Class<?> targetClass, List<FieldBinding> rootBindings) {
            return new ScanResult(targetClass, rootBindings);
        }
    }
}
