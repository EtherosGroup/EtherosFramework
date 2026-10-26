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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描 {@code @YamlConfig} 目标类的结构，生成字段绑定节点树。
 *
 * <p>扫描规则：
 * <ul>
 *   <li>忽略 {@code static} 字段</li>
 *   <li>如果字段类型是目标类内部的静态类 → 判定为嵌套节点，递归扫描该静态类</li>
 *   <li>否则 → 叶子节点，绑定到 YAML 值</li>
 * </ul>
 */
public final class ClassStructureScanner {

    private ClassStructureScanner() {
    }

    /**
     * 扫描目标类，返回绑定节点树。
     *
     * @param targetClass 被 {@code @YamlConfig} 标注的目标类
     * @return 扫描结果
     */
    public static FieldBinding.ScanResult scan(Class<?> targetClass) {
        Set<Class<?>> staticInnerClasses = Arrays.stream(targetClass.getDeclaredClasses())
                .filter(c -> Modifier.isStatic(c.getModifiers()))
                .collect(Collectors.toSet());

        List<FieldBinding> rootBindings = new ArrayList<>();

        for (Field field : targetClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Class<?> fieldType = field.getType();

            if (staticInnerClasses.contains(fieldType)) {
                String yamlKey = YamlNameConverter.classNameToKey(fieldType);
                yamlKey = YamlNameConverter.camelToKebab(yamlKey);
                List<FieldBinding> children = scanNestedFields(fieldType, yamlKey);
                FieldBinding nestedBinding = new FieldBinding(yamlKey, field, children);
                nestedBinding.setNestedClass(fieldType);
                rootBindings.add(nestedBinding);
            } else {
                String yamlKey = YamlNameConverter.resolveKey(field);
                rootBindings.add(new FieldBinding(yamlKey, field));
            }
        }

        return FieldBinding.ScanResult.of(targetClass, rootBindings);
    }

    private static List<FieldBinding> scanNestedFields(Class<?> clazz, String parentPath) {
        List<FieldBinding> bindings = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            String yamlKey = YamlNameConverter.resolveKey(field);
            String fullPath = parentPath + "." + yamlKey;
            bindings.add(new FieldBinding(fullPath, field));
        }

        return bindings;
    }
}
