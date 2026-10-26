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

public class BeanRegistry {
    private final Map<String, Object> beansByName = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> beansByType = new ConcurrentHashMap<>();

    public void register(String name, Class<?> type, Object instance) {
        beansByName.put(name, instance);
        beansByType.put(type, instance);
    }

    public Object getByName(String name) {
        return beansByName.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getByType(Class<T> type) {
        Object bean = beansByType.get(type);
        if (bean != null) {
            return (T) bean;
        }
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return (T) entry.getValue();
            }
        }
        return null;
    }

    public <T> List<T> getAllByType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Map.Entry<Class<?>, Object> entry : beansByType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                result.add(type.cast(entry.getValue()));
            }
        }
        return result;
    }

    public boolean containsType(Class<?> type) {
        if (beansByType.containsKey(type)) return true;
        for (Class<?> key : beansByType.keySet()) {
            if (type.isAssignableFrom(key)) return true;
        }
        return false;
    }

    public Map<String, Object> getBeansByName() {
        return Collections.unmodifiableMap(beansByName);
    }

    public Map<Class<?>, Object> getBeansByType() {
        return Collections.unmodifiableMap(beansByType);
    }

    public void clear() {
        beansByName.clear();
        beansByType.clear();
    }
}
