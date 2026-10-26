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

import cn.skilfully.etheros.etherosframework.internal.TextUtil;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.exception.BindingException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * 将扁平化的 YAML 数据绑定到目标对象的字段上。
 *
 * <p>缺失的键保留字段默认值；类型不兼容时抛出 {@link BindingException}。
 */
public final class YamlDataBinder {

    private YamlDataBinder() {
    }

    /**
     * 创建目标类实例并绑定 YAML 数据。
     *
     * @param yamlData     YAML 文件解析后的扁平化 Map
     * @param targetClass  目标类（需有无参构造器）
     * @param scanResult   扫描结果节点树
     * @param <T>          目标类类型
     * @return 绑定完数据的配置对象
     * @throws BindingException 类型转换或反射失败时抛出
     */
    public static <T> T bind(Map<String, Object> yamlData, Class<T> targetClass,
                             FieldBinding.ScanResult scanResult) {
        try {
            T instance = targetClass.getDeclaredConstructor().newInstance();

            for (FieldBinding binding : scanResult.rootBindings()) {
                bindNode(yamlData, instance, binding);
            }

            return instance;
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to bind configuration data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void bindNode(Map<String, Object> data, Object target,
                                 FieldBinding binding) throws Exception {
        if (binding.isLeaf()) {
            Object rawValue = data.get(binding.yamlPath());
            if (rawValue == null) {
                return;
            }
            Object coerced = coerceValue(rawValue, binding.type());
            FieldAccessor.setField(binding.field(), target, coerced);
        } else {
            Object nestedInstance = FieldAccessor.getField(binding.field(), target);
            if (nestedInstance == null) {
                nestedInstance = binding.nestedClass().getDeclaredConstructor().newInstance();
            }

            for (FieldBinding child : binding.children()) {
                Object childRaw = data.get(child.yamlPath());
                if (childRaw == null) {
                    continue;
                }
                Object coerced = coerceValue(childRaw, child.type());
                FieldAccessor.setField(child.field(), nestedInstance, coerced);
            }

            FieldAccessor.setField(binding.field(), target, nestedInstance);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object coerceValue(Object raw, Class<?> targetType) {
        if (raw == null) {
            return null;
        }
        if (targetType.isAssignableFrom(raw.getClass())) {
            return raw;
        }
        if (targetType == String.class) {
            return raw.toString();
        }
        if (raw instanceof String) {
            String s = (String) raw;
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(s);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(s);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(s);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(s);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(s);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(s);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(s);
            }
            if (targetType == char.class || targetType == Character.class) {
                if (s.length() == 1) {
                    return s.charAt(0);
                }
                throw new BindingException("Cannot convert string \"" + s + "\" to char");
            }
        }
        if (raw instanceof Number) {
            Number num = (Number) raw;
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return num.shortValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return num.byteValue();
            }
            if (targetType == String.class) {
                return num.toString();
            }
        }
        if (raw instanceof Boolean) {
            Boolean bool = (Boolean) raw;
            if (targetType == boolean.class || targetType == Boolean.class) {
                return bool;
            }
            if (targetType == String.class) {
                return bool.toString();
            }
        }
        if (targetType == List.class || targetType.isAssignableFrom(raw.getClass())) {
            return raw;
        }
        if (targetType.isInstance(raw)) {
            return raw;
        }
        throw new BindingException(
                "Cannot coerce value of type " + raw.getClass().getName()
                + " to " + targetType.getName());
    }
}
