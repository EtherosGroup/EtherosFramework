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
package cn.etheros.etherosframework.event;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 事件载荷校验
 *
 * <p>载荷只允许 JDK 值类型：String、包装类型、UUID、JDK 枚举、JDK 集合/数组/Optional
 * <p>插件自定义类型在各插件的 classloader 下不是同一个类，跨插件投递必然失败
 * <p>Map 键必须是非空字符串；出现环引用直接拒绝
 * <p>资源上限只限制节点总数，不限制深度：深度受节点预算约束
 */
final class PayloadValidator {

    /** 单次校验最多处理的节点数 */
    static final int MAX_NODES = 1024;

    private PayloadValidator() {
    }

    static Map<String, Object> validateMap(Map<?, ?> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        IdentityHashMap<Object, Boolean> path = new IdentityHashMap<Object, Boolean>();
        int[] budget = {MAX_NODES};
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = requireKey(entry.getKey(), "data");
            Object value = entry.getValue();
            validateValue(key, value, path, budget);
            copy.put(key, value);
        }
        return copy;
    }

    static void validateEntry(String key, Object value) {
        requireKey(key, "data");
        validateValue(key, value, new IdentityHashMap<Object, Boolean>(), new int[]{MAX_NODES});
    }

    private static void validateValue(String key, Object value,
                                      IdentityHashMap<Object, Boolean> path, int[] budget) {
        if (value == null) {
            return;
        }
        if (budget[0]-- <= 0) {
            throw new IllegalArgumentException("错误：事件载荷超过节点上限：" + MAX_NODES);
        }
        Class<?> type = value.getClass();
        // 数组先处理：String[] 的类加载器也为 null，但其元素仍需逐个校验
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) {
                return;
            }
            enter(path, value, key);
            try {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    validateValue(key + "[" + index + "]", Array.get(value, index), path, budget);
                }
            } finally {
                path.remove(value);
            }
            return;
        }
        if (value instanceof Map) {
            enter(path, value, key);
            try {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String childKey = requireKey(entry.getKey(), key);
                    validateValue(childKey, entry.getValue(), path, budget);
                }
            } finally {
                path.remove(value);
            }
            return;
        }
        if (value instanceof Collection) {
            enter(path, value, key);
            try {
                int index = 0;
                for (Object element : (Collection<?>) value) {
                    validateValue(key + "[" + index + "]", element, path, budget);
                    index++;
                }
            } finally {
                path.remove(value);
            }
            return;
        }
        if (value instanceof Optional) {
            validateValue(key, ((Optional<?>) value).orElse(null), path, budget);
            return;
        }
        // 值类型：仅允许由 JDK/bootstrap 加载的类
        if (type.getClassLoader() == null) {
            return;
        }
        throw new IllegalArgumentException("错误：事件载荷类型不安全：" + key + " -> " + type.getName());
    }

    private static void enter(IdentityHashMap<Object, Boolean> path, Object value, String key) {
        if (path.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("错误：事件载荷存在环引用：" + key);
        }
    }

    private static String requireKey(Object key, String parent) {
        if (!(key instanceof String) || ((String) key).isEmpty()) {
            throw new IllegalArgumentException("错误：事件载荷键必须为非空字符串：" + parent);
        }
        return (String) key;
    }
}
