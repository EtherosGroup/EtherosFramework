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

import cn.skilfully.etheros.etherosframework.utils.config.yaml.annotation.ConfigNode;

import java.lang.reflect.Field;

public final class YamlNameConverter {

    private YamlNameConverter() {
    }

    public static String camelToKebab(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String classNameToKey(Class<?> clazz) {
        return classNameToKey(clazz.getSimpleName());
    }

    public static String classNameToKey(String simpleName) {
        if (simpleName.isEmpty()) {
            return simpleName;
        }
        char first = simpleName.charAt(0);
        if (Character.isLowerCase(first)) {
            return simpleName;
        }
        if (simpleName.length() == 1) {
            return String.valueOf(Character.toLowerCase(first));
        }
        return Character.toLowerCase(first) + simpleName.substring(1);
    }

    public static String resolveKey(Field field) {
        ConfigNode annotation = field.getAnnotation(ConfigNode.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return camelToKebab(field.getName());
    }

    public static String resolveKey(String configNodeValue, String fieldName) {
        if (configNodeValue != null && !configNodeValue.isEmpty()) {
            return configNodeValue;
        }
        return camelToKebab(fieldName);
    }
}
