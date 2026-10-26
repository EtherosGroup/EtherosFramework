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
import lombok.Getter;
import lombok.ToString;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@ToString
public class BeanDefinition {
    private final Class<?> beanClass;
    private final String beanName;
    private final boolean global;
    private final boolean prototype;
    private final boolean configuration;
    private final Constructor<?> constructor;
    private final List<Field> injectFields;
    private final List<Method> beanMethods;
    private final List<Method> postConstructMethods;
    private final List<Method> preDestroyMethods;

    private BeanDefinition(Class<?> beanClass, String beanName, boolean global, boolean prototype,
                           boolean configuration, Constructor<?> constructor,
                           List<Field> injectFields, List<Method> beanMethods,
                           List<Method> postConstructMethods, List<Method> preDestroyMethods) {
        this.beanClass = beanClass;
        this.beanName = beanName;
        this.global = global;
        this.prototype = prototype;
        this.configuration = configuration;
        this.constructor = constructor;
        this.injectFields = Collections.unmodifiableList(injectFields);
        this.beanMethods = Collections.unmodifiableList(beanMethods);
        this.postConstructMethods = Collections.unmodifiableList(postConstructMethods);
        this.preDestroyMethods = Collections.unmodifiableList(preDestroyMethods);
    }

    public static BeanDefinition from(Class<?> clazz) {
        Service service = clazz.getAnnotation(Service.class);
        GlobalService globalService = clazz.getAnnotation(GlobalService.class);
        Configuration configuration = clazz.getAnnotation(Configuration.class);
        Prototype prototype = clazz.getAnnotation(Prototype.class);

        boolean isGlobal = globalService != null;
        boolean isPrototype = prototype != null;
        boolean isConfig = configuration != null;

        String name = "";
        if (globalService != null && !globalService.value().isEmpty()) {
            name = globalService.value();
        } else if (service != null && !service.value().isEmpty()) {
            name = service.value();
        } else if (prototype != null && !prototype.value().isEmpty()) {
            name = prototype.value();
        }
        if (name.isEmpty()) {
            name = decapitalize(clazz.getSimpleName());
        }

        Constructor<?> ctor = resolveConstructor(clazz);

        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)
                    || field.isAnnotationPresent(GlobalAutowired.class)
                    || field.isAnnotationPresent(Value.class)) {
                field.setAccessible(true);
                fields.add(field);
            }
        }

        List<Method> beanMethods = new ArrayList<>();
        if (isConfig) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class)) {
                    method.setAccessible(true);
                    beanMethods.add(method);
                }
            }
        }

        List<Method> postConstructs = new ArrayList<>();
        List<Method> preDestroys = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                method.setAccessible(true);
                postConstructs.add(method);
            }
            if (method.isAnnotationPresent(PreDestroy.class)) {
                method.setAccessible(true);
                preDestroys.add(method);
            }
        }

        return new BeanDefinition(clazz, name, isGlobal, isPrototype, isConfig,
                ctor, fields, beanMethods, postConstructs, preDestroys);
    }

    private static Constructor<?> resolveConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        if (ctors.length == 0) {
            try {
                return clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No accessible constructor for " + clazz.getName(), e);
            }
        }
        if (ctors.length == 1) {
            ctors[0].setAccessible(true);
            return ctors[0];
        }
        for (Constructor<?> c : ctors) {
            if (c.isAnnotationPresent(Autowired.class)) {
                c.setAccessible(true);
                return c;
            }
        }
        try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Multiple constructors found on " + clazz.getName()
                    + " but none annotated with @Autowired and no default constructor", e);
        }
    }

    private static String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        char[] chars = str.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
