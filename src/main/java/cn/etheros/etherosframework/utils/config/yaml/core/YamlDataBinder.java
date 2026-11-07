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

import cn.etheros.etherosframework.internal.TextUtil;
import cn.etheros.etherosframework.utils.config.yaml.exception.BindingException;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
            T instance = newInstance(targetClass);

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

    private static void bindNode(Map<String, Object> data, Object target,
                                 FieldBinding binding) throws Exception {
        if (binding.isLeaf()) {
            Object rawValue = data.get(binding.yamlPath());
            if (rawValue == null) {
                return;
            }
            Object coerced = coerceValue(rawValue, binding.type(), binding.genericType());
            FieldAccessor.setField(binding.field(), target, coerced);
        } else {
            Object nestedInstance = FieldAccessor.getField(binding.field(), target);
            if (nestedInstance == null) {
                nestedInstance = newInstance(binding.nestedClass());
            }

            for (FieldBinding child : binding.children()) {
                bindNode(data, nestedInstance, child);
            }

            FieldAccessor.setField(binding.field(), target, nestedInstance);
        }
    }

    private static <T> T newInstance(Class<T> type) throws Exception {
        java.lang.reflect.Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object coerceValue(Object raw, Class<?> targetType, Type genericType) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List) {
            List<?> rawList = (List<?>) raw;
            if (targetType.isArray()) {
                return coerceArray(rawList, targetType, genericType);
            }
            if (targetType == Object.class || targetType.isAssignableFrom(ArrayList.class)) {
                return coerceElements(rawList, elementType(genericType),
                        new ArrayList<>(rawList.size()));
            }
            if (Collection.class.isAssignableFrom(targetType)) {
                return coerceElements(rawList, elementType(genericType),
                        newCollection(targetType));
            }
        }
        if (targetType.isAssignableFrom(raw.getClass())) {
            return raw;
        }
        if (targetType.isEnum()) {
            return coerceEnum(raw, targetType);
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

    /**
     * 按枚举常量名匹配，忽略大小写；没有匹配项时抛出带可用值列表的异常。
     */
    private static Object coerceEnum(Object raw, Class<?> enumType) {
        String text = raw instanceof String ? ((String) raw).trim() : String.valueOf(raw);
        Object[] constants = enumType.getEnumConstants();

        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }

        StringBuilder allowed = new StringBuilder();
        for (Object constant : constants) {
            if (allowed.length() > 0) {
                allowed.append(", ");
            }
            allowed.append(((Enum<?>) constant).name());
        }
        throw new BindingException("Cannot convert value \"" + text + "\" to enum "
                + enumType.getName() + "; allowed values: [" + allowed + "]");
    }

    /**
     * 按元素类型逐个转换集合元素；无泛型参数时元素保持原样。
     */
    private static Collection<Object> coerceElements(List<?> raw, Type elementType,
                                                     Collection<Object> target) {
        Class<?> elementClass = rawClassOf(elementType);
        for (Object item : raw) {
            target.add(coerceValue(item, elementClass, elementType));
        }
        return target;
    }

    /**
     * 创建目标集合的实例：接口按常用实现处理，具体类型走无参构造器。
     */
    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Class<?> targetType) {
        if (targetType == Set.class) {
            return new LinkedHashSet<>();
        }
        if (targetType == SortedSet.class || targetType == NavigableSet.class) {
            return new TreeSet<>();
        }
        if (targetType == Queue.class || targetType == Deque.class) {
            return new ArrayDeque<>();
        }
        if (targetType.isInterface() || Modifier.isAbstract(targetType.getModifiers())) {
            throw new BindingException("Unsupported collection type: " + targetType.getName());
        }
        try {
            return (Collection<Object>) targetType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BindingException(
                    "Cannot instantiate collection type: " + targetType.getName(), e);
        }
    }

    private static Object coerceArray(List<?> raw, Class<?> arrayType, Type genericType) {
        Type componentType = arrayComponentType(arrayType, genericType);
        Class<?> componentClass = rawClassOf(componentType);

        Object array = Array.newInstance(componentClass, raw.size());
        int index = 0;
        for (Object item : raw) {
            Array.set(array, index++, coerceValue(item, componentClass, componentType));
        }
        return array;
    }

    private static Type arrayComponentType(Class<?> arrayType, Type genericType) {
        if (genericType instanceof GenericArrayType) {
            return ((GenericArrayType) genericType).getGenericComponentType();
        }
        Class<?> componentType = arrayType.getComponentType();
        return componentType != null ? componentType : Object.class;
    }

    private static Type elementType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (arguments.length == 1) {
                return arguments[0];
            }
        }
        return Object.class;
    }

    private static Class<?> rawClassOf(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        return Object.class;
    }
}
