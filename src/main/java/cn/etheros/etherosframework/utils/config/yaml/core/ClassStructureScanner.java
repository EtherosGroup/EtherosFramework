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
package cn.etheros.etherosframework.utils.config.yaml.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描 {@code @YamlConfig} 目标类的结构，生成字段绑定节点树。
 *
 * <p>扫描规则：
 * <ul>
 *   <li>忽略 {@code static} 字段与编译器合成字段</li>
 *   <li>目标类（含任意层静态内部类）声明的静态类视为节点类型；字段类型是节点类型时判定为嵌套节点，递归扫描</li>
 *   <li>节点类型已出现在当前绑定路径上（自引用）时按叶子节点处理，避免无限递归</li>
 *   <li>其余字段 → 叶子节点，绑定到 YAML 值</li>
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
        Set<Class<?>> nodeClasses = new LinkedHashSet<>();
        collectNodeClasses(targetClass, nodeClasses);

        List<FieldBinding> rootBindings =
                scanFields(targetClass, "", nodeClasses, new LinkedHashSet<>());

        return FieldBinding.ScanResult.of(targetClass, rootBindings);
    }

    /**
     * 收集目标类内部声明的全部静态类，含多层嵌套。
     */
    private static void collectNodeClasses(Class<?> owner, Set<Class<?>> nodeClasses) {
        for (Class<?> declared : owner.getDeclaredClasses()) {
            if (!isNodeClass(declared) || !nodeClasses.add(declared)) {
                continue;
            }
            collectNodeClasses(declared, nodeClasses);
        }
    }

    private static boolean isNodeClass(Class<?> type) {
        return Modifier.isStatic(type.getModifiers())
                && !type.isEnum()
                && !type.isInterface()
                && !type.isAnnotation();
    }

    private static List<FieldBinding> scanFields(Class<?> owner, String parentPath,
                                                 Set<Class<?>> nodeClasses,
                                                 Set<Class<?>> pathTypes) {
        List<FieldBinding> bindings = new ArrayList<>();

        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);

            String yamlKey = YamlNameConverter.resolveKey(field);
            String path = parentPath.isEmpty() ? yamlKey : parentPath + "." + yamlKey;
            Class<?> fieldType = field.getType();

            if (nodeClasses.contains(fieldType) && !pathTypes.contains(fieldType)) {
                Set<Class<?>> nestedPath = new LinkedHashSet<>(pathTypes);
                nestedPath.add(fieldType);

                FieldBinding nestedBinding =
                        new FieldBinding(path, field,
                                scanFields(fieldType, path, nodeClasses, nestedPath));
                nestedBinding.setNestedClass(fieldType);
                bindings.add(nestedBinding);
            } else {
                bindings.add(new FieldBinding(path, field));
            }
        }

        return bindings;
    }
}
