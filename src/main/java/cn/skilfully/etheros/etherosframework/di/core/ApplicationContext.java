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

import cn.skilfully.etheros.etherosframework.di.annotation.*;
import cn.skilfully.etheros.etherosframework.di.exception.BeanCreationException;
import cn.skilfully.etheros.etherosframework.di.exception.BeanNotFoundException;
import cn.skilfully.etheros.etherosframework.di.exception.CircularDependencyException;
import cn.skilfully.etheros.etherosframework.di.lifecycle.LifecycleProcessor;
import cn.skilfully.etheros.etherosframework.di.scanner.ClassPathScanner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class ApplicationContext {

    private final BeanRegistry registry = new BeanRegistry();
    private final List<BeanDefinition> definitions = new ArrayList<>();
    private final List<String> creationOrder = new ArrayList<>();
    private final LifecycleProcessor lifecycleProcessor = new LifecycleProcessor();
    private ClassLoader classLoader;
    private String basePackage;
    private PropertyLoader propertyLoader;

    public ApplicationContext() {
    }

    public static ApplicationContext run(Class<?> primarySource) {
        ApplicationContext ctx = new ApplicationContext();
        ctx.classLoader = primarySource.getClassLoader();
        ctx.basePackage = primarySource.getPackage().getName();
        ctx.propertyLoader = new PropertyLoader(ctx.classLoader);
        ctx.scan();
        ctx.refresh();
        return ctx;
    }

    public static ApplicationContext run(Class<?> primarySource, Object... externalBeans) {
        ApplicationContext ctx = run(primarySource);
        for (Object bean : externalBeans) {
            ctx.registerSingleton(bean.getClass(), bean);
        }
        return ctx;
    }

    void scan() {
        ClassPathScanner scanner = new ClassPathScanner(classLoader);
        Set<String> candidates = scanner.scan(basePackage);
        for (String className : candidates) {
            try {
                Class<?> clazz = Class.forName(className, false, classLoader);
                definitions.add(BeanDefinition.from(clazz));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load class: " + className, e);
            }
        }
    }

    void refresh() {
        instantiateSingletons();
        injectConfigurationFields();
        invokeBeanFactories();
        registerGlobalBeans();
        injectFields();
        invokePostConstructs();
    }

    private void instantiateSingletons() {
        int remaining;
        int maxPasses = definitions.size() + 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            remaining = 0;
            for (BeanDefinition def : definitions) {
                if (def.isPrototype()) continue;
                if (registry.getByName(def.getBeanName()) != null) continue;
                try {
                    Object instance = createInstance(def);
                    registry.register(def.getBeanName(), def.getBeanClass(), instance);
                    creationOrder.add(def.getBeanName());
                } catch (BeanNotFoundException e) {
                    remaining++;
                } catch (Exception e) {
                    throw new BeanCreationException(
                            "Failed to instantiate " + def.getBeanClass().getName(), e);
                }
            }
            if (remaining == 0) break;
        }
        checkUninstantiated(definitions);
    }

    private void checkUninstantiated(List<BeanDefinition> definitions) {
        List<String> unresolved = new ArrayList<>();
        for (BeanDefinition def : definitions) {
            if (def.isPrototype()) continue;
            if (registry.getByName(def.getBeanName()) == null) {
                unresolved.add(def.getBeanClass().getName());
            }
        }
        if (unresolved.isEmpty()) return;

        boolean allDepsDefined = true;
        for (BeanDefinition def : definitions) {
            if (def.isPrototype()) continue;
            if (registry.getByName(def.getBeanName()) != null) continue;
            for (Class<?> paramType : def.getConstructor().getParameterTypes()) {
                if (!isTypeDefined(paramType, definitions)) {
                    allDepsDefined = false;
                    break;
                }
            }
            if (!allDepsDefined) break;
        }

        if (allDepsDefined) {
            throw new CircularDependencyException(
                    "Circular dependency detected among beans: " + String.join(", ", unresolved));
        }
        throw new BeanCreationException(
                "Unresolved dependencies for beans: " + String.join(", ", unresolved));
    }

    private boolean isTypeDefined(Class<?> type, List<BeanDefinition> definitions) {
        for (BeanDefinition def : definitions) {
            if (type.isAssignableFrom(def.getBeanClass())) {
                return true;
            }
        }
        return false;
    }

    private Object createInstance(BeanDefinition def) throws Exception {
        Constructor<?> ctor = def.getConstructor();
        Class<?>[] paramTypes = ctor.getParameterTypes();
        if (paramTypes.length == 0) {
            return ctor.newInstance();
        }
        Object[] params = resolveParams(paramTypes,
                ctor.getParameterAnnotations(), def.getBeanClass().getName());
        return ctor.newInstance(params);
    }

    private Object[] resolveParams(Class<?>[] paramTypes, java.lang.annotation.Annotation[][] paramAnnos,
                                   String contextName) {
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolveParameter(paramTypes[i], paramAnnos[i], contextName);
        }
        return params;
    }

    private Object resolveParameter(Class<?> type, java.lang.annotation.Annotation[] annotations,
                                    String contextName) {
        for (java.lang.annotation.Annotation ann : annotations) {
            if (ann instanceof Value) {
                Value v = (Value) ann;
                String value = propertyLoader.getProperty(v.value());
                if (value == null) {
                    if (!v.defaultValue().isEmpty()) {
                        return convertValue(v.defaultValue(), type);
                    }
                    throw new BeanCreationException(
                            "Property not found: " + v.value() + " for " + contextName);
                }
                return convertValue(value, type);
            }
            if (ann instanceof GlobalAutowired) {
                GlobalAutowired ga = (GlobalAutowired) ann;
                return resolveGlobalBean(ga.value(), type, ga.required(), "constructor param in " + contextName);
            }
            if (ann instanceof Autowired) {
                Autowired a = (Autowired) ann;
                return resolveLocalBean(a.value(), type, a.required(), "constructor param in " + contextName);
            }
        }
        Object bean = registry.getByType(type);
        if (bean != null) return bean;
        bean = SharedContext.getBean(type);
        if (bean != null) return bean;
        throw new BeanNotFoundException(type);
    }

    private void injectFields() {
        for (BeanDefinition def : definitions) {
            if (def.isPrototype()) continue;
            Object instance = registry.getByName(def.getBeanName());
            if (instance == null) continue;
            for (Field field : def.getInjectFields()) {
                try {
                    Object value = resolveFieldValue(field, def.getBeanClass().getName());
                    field.set(instance, value);
                } catch (Exception e) {
                    throw new BeanCreationException(
                            "Failed to inject " + field.getName() + " on "
                                    + def.getBeanClass().getName(), e);
                }
            }
        }
    }

    private Object resolveFieldValue(Field field, String contextName) {
        Value valueAnn = field.getAnnotation(Value.class);
        if (valueAnn != null) {
            String raw = propertyLoader.getProperty(valueAnn.value());
            if (raw == null) {
                if (!valueAnn.defaultValue().isEmpty()) {
                    return convertValue(valueAnn.defaultValue(), field.getType());
                }
                throw new BeanCreationException(
                        "Property not found: " + valueAnn.value() + " for " + contextName + "." + field.getName());
            }
            return convertValue(raw, field.getType());
        }

        GlobalAutowired globalAnn = field.getAnnotation(GlobalAutowired.class);
        if (globalAnn != null) {
            return resolveGlobalBean(globalAnn.value(), field.getType(), globalAnn.required(), field.getName());
        }

        Autowired autowireAnn = field.getAnnotation(Autowired.class);
        if (autowireAnn != null) {
            return resolveLocalBean(autowireAnn.value(), field.getType(), autowireAnn.required(), field.getName());
        }
        return null;
    }

    private Object resolveLocalBean(String name, Class<?> type, boolean required, String fieldName) {
        Object bean;
        if (!name.isEmpty()) {
            bean = registry.getByName(name);
            if (bean == null) {
                bean = SharedContext.get(name);
            }
            if (bean != null && !type.isInstance(bean)) {
                throw new BeanCreationException(
                        "Bean '" + name + "' is not of type " + type.getName() + " for field " + fieldName);
            }
        } else {
            bean = registry.getByType(type);
            if (bean == null) {
                bean = SharedContext.getBean(type);
            }
        }
        if (bean == null && required) {
            throw new BeanNotFoundException(fieldName, type);
        }
        return bean;
    }

    private Object resolveGlobalBean(String name, Class<?> type, boolean required, String fieldName) {
        Object bean;
        if (!name.isEmpty()) {
            bean = SharedContext.get(name);
            if (bean == null) {
                bean = registry.getByName(name);
            }
            if (bean != null && !type.isInstance(bean)) {
                throw new BeanCreationException(
                        "Global bean '" + name + "' is not of type " + type.getName() + " for field " + fieldName);
            }
        } else {
            bean = SharedContext.getBean(type);
            if (bean == null) {
                bean = registry.getByType(type);
            }
        }
        if (bean == null && required) {
            throw new BeanNotFoundException(fieldName, type);
        }
        return bean;
    }

    private void injectConfigurationFields() {
        for (BeanDefinition def : definitions) {
            if (!def.isConfiguration()) continue;
            if (def.isPrototype()) continue;
            Object instance = registry.getByName(def.getBeanName());
            if (instance == null) continue;
            for (Field field : def.getInjectFields()) {
                try {
                    Object value = resolveFieldValue(field, def.getBeanClass().getName());
                    field.set(instance, value);
                } catch (Exception e) {
                    throw new BeanCreationException(
                            "Failed to inject " + field.getName() + " on "
                                    + def.getBeanClass().getName(), e);
                }
            }
        }
    }

    private void invokeBeanFactories() {
        for (BeanDefinition def : definitions) {
            if (!def.isConfiguration()) continue;
            Object configInstance = registry.getByName(def.getBeanName());
            if (configInstance == null) continue;
            for (Method method : def.getBeanMethods()) {
                try {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    Object[] params = resolveParams(paramTypes,
                            method.getParameterAnnotations(), def.getBeanClass().getName());
                    Object result = method.invoke(configInstance, params);
                    Bean beanAnn = method.getAnnotation(Bean.class);
                    String name = beanAnn.name().isEmpty() ? method.getName() : beanAnn.name();
                    registry.register(name, result.getClass(), result);
                } catch (Exception e) {
                    throw new BeanCreationException(
                            "Failed to invoke @Bean method " + method.getName()
                                    + " on " + def.getBeanClass().getName(), e);
                }
            }
        }
    }

    private void invokePostConstructs() {
        Map<String, BeanDefinition> defMap = new HashMap<>();
        for (BeanDefinition def : definitions) {
            if (!def.isPrototype() && registry.getByName(def.getBeanName()) != null) {
                defMap.put(def.getBeanName(), def);
            }
        }
        if (defMap.isEmpty()) return;

        Map<String, Set<String>> dependents = new HashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String name : defMap.keySet()) {
            dependents.put(name, new LinkedHashSet<>());
            inDegree.put(name, 0);
        }

        for (BeanDefinition def : definitions) {
            if (def.isPrototype()) continue;
            String name = def.getBeanName();
            if (!defMap.containsKey(name)) continue;

            for (Class<?> paramType : def.getConstructor().getParameterTypes()) {
                for (BeanDefinition other : definitions) {
                    if (other.isPrototype()) continue;
                    String otherName = other.getBeanName();
                    if (!otherName.equals(name) && paramType.isAssignableFrom(other.getBeanClass())
                            && defMap.containsKey(otherName)) {
                        if (dependents.get(otherName).add(name)) {
                            inDegree.put(name, inDegree.get(name) + 1);
                        }
                    }
                }
            }

            for (Field field : def.getInjectFields()) {
                if (!field.isAnnotationPresent(Autowired.class)
                        && !field.isAnnotationPresent(GlobalAutowired.class)) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                for (BeanDefinition other : definitions) {
                    if (other.isPrototype()) continue;
                    String otherName = other.getBeanName();
                    if (!otherName.equals(name) && fieldType.isAssignableFrom(other.getBeanClass())
                            && defMap.containsKey(otherName)) {
                        if (dependents.get(otherName).add(name)) {
                            inDegree.put(name, inDegree.get(name) + 1);
                        }
                    }
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            String beanName = queue.poll();
            BeanDefinition def = defMap.get(beanName);
            Object instance = registry.getByName(beanName);
            if (instance != null && def != null) {
                lifecycleProcessor.invokePostConstruct(instance, def.getPostConstructMethods());
            }
            for (String dependent : dependents.get(beanName)) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }

        List<Map.Entry<String, Integer>> remaining = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() > 0) {
                remaining.add(entry);
            }
        }
        remaining.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, Integer> entry : remaining) {
            BeanDefinition def = defMap.get(entry.getKey());
            Object instance = registry.getByName(entry.getKey());
            if (instance != null && def != null) {
                lifecycleProcessor.invokePostConstruct(instance, def.getPostConstructMethods());
            }
        }
    }

    private void registerGlobalBeans() {
        for (BeanDefinition def : definitions) {
            if (!def.isGlobal()) continue;
            Object instance = registry.getByName(def.getBeanName());
            if (instance != null) {
                SharedContext.register(def.getBeanClass(), instance);
            }
        }
    }

    public <T> T getBean(Class<T> type) {
        return registry.getByType(type);
    }

    public Object getBean(String name) {
        return registry.getByName(name);
    }

    public <T> List<T> getBeans(Class<T> type) {
        return registry.getAllByType(type);
    }

    public ApplicationContext registerSingleton(Class<?> type, Object instance) {
        String name = decapitalize(type.getSimpleName());
        registry.register(name, type, instance);
        return this;
    }

    public ApplicationContext registerSingleton(String name, Class<?> type, Object instance) {
        registry.register(name, type, instance);
        return this;
    }

    public Map<String, Object> getBeansByName() {
        return registry.getBeansByName();
    }

    public Map<Class<?>, Object> getBeansByType() {
        return registry.getBeansByType();
    }

    public PropertyLoader getPropertyLoader() {
        return propertyLoader;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void shutdown() {
        Map<String, BeanDefinition> defMap = new HashMap<>();
        for (BeanDefinition def : definitions) {
            defMap.put(def.getBeanName(), def);
        }
        for (int i = creationOrder.size() - 1; i >= 0; i--) {
            String beanName = creationOrder.get(i);
            BeanDefinition def = defMap.get(beanName);
            if (def == null || def.isPrototype()) continue;
            Object instance = registry.getByName(beanName);
            if (instance == null) continue;
            lifecycleProcessor.invokePreDestroy(instance, def.getPreDestroyMethods());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertValue(String value, Class<T> targetType) {
        if (targetType == String.class) return (T) value;
        if (targetType == int.class || targetType == Integer.class) return (T) Integer.valueOf(value);
        if (targetType == long.class || targetType == Long.class) return (T) Long.valueOf(value);
        if (targetType == boolean.class || targetType == Boolean.class) return (T) Boolean.valueOf(value);
        if (targetType == double.class || targetType == Double.class) return (T) Double.valueOf(value);
        if (targetType == float.class || targetType == Float.class) return (T) Float.valueOf(value);
        if (targetType == short.class || targetType == Short.class) return (T) Short.valueOf(value);
        if (targetType == byte.class || targetType == Byte.class) return (T) Byte.valueOf(value);
        return (T) value;
    }

    private static String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        char[] chars = str.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
