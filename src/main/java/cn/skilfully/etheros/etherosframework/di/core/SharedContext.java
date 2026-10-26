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
package cn.skilfully.etheros.etherosframework.di.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SharedContext {

    private static final Logger LOG = Logger.getLogger("EtherosFramework-SharedContext");

    private static final Map<String, Object> beansByName = new ConcurrentHashMap<>();
    private static final Map<String, List<Object>> beansByClassName = new ConcurrentHashMap<>();

    private SharedContext() {
    }

    public static synchronized void register(Class<?> type, Object bean) {
        String name = type.getSimpleName();
        Object existing = beansByName.get(name);
        if (existing != null && existing != bean) {
            LOG.log(Level.WARNING,
                    "Overwriting global bean name '{0}': {1} -> {2}",
                    new Object[]{name, existing.getClass().getName(), bean.getClass().getName()});
        }
        beansByName.put(name, bean);
        for (String className : collectHierarchyNames(type)) {
            List<Object> list = beansByClassName.computeIfAbsent(className, k -> new CopyOnWriteArrayList<>());
            if (!list.contains(bean)) {
                list.add(bean);
            }
        }
    }

    public static synchronized void syncFrom(ApplicationContext context) {
        for (Map.Entry<String, Object> entry : context.getBeansByName().entrySet()) {
            String name = entry.getKey();
            Object bean = entry.getValue();
            Object existing = beansByName.get(name);
            if (existing != null && existing != bean) {
                LOG.log(Level.WARNING,
                        "Overwriting global bean name '{0}': {1} -> {2}",
                        new Object[]{name, existing.getClass().getName(), bean.getClass().getName()});
            }
            beansByName.put(name, bean);
        }
        for (Map.Entry<Class<?>, Object> entry : context.getBeansByType().entrySet()) {
            Object bean = entry.getValue();
            for (String className : collectHierarchyNames(entry.getKey())) {
                List<Object> list = beansByClassName.computeIfAbsent(className, k -> new CopyOnWriteArrayList<>());
                if (!list.contains(bean)) {
                    list.add(bean);
                }
            }
        }
    }

    public static Object get(String name) {
        return beansByName.get(name);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(Class<T> type) {
        List<Object> list = beansByClassName.get(type.getName());
        if (list != null && !list.isEmpty()) {
            return (T) list.get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getBeans(Class<T> type) {
        List<Object> list = beansByClassName.get(type.getName());
        if (list != null) {
            return (List<T>) Collections.unmodifiableList(list);
        }
        return Collections.emptyList();
    }

    public static boolean contains(Class<?> type) {
        List<Object> list = beansByClassName.get(type.getName());
        return list != null && !list.isEmpty();
    }

    public static void clear() {
        beansByName.clear();
        beansByClassName.clear();
    }

    private static Set<String> collectHierarchyNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        collectHierarchyNames(type, names);
        return names;
    }

    private static void collectHierarchyNames(Class<?> clazz, Set<String> names) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        names.add(clazz.getName());
        for (Class<?> iface : clazz.getInterfaces()) {
            collectHierarchyNames(iface, names);
        }
        collectHierarchyNames(clazz.getSuperclass(), names);
    }
}
