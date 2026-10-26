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
package cn.skilfully.etheros.etherosframework.internal;

/**
 * Java 11 字符串方法的 Java 8 等价实现：{@code String#strip}、{@code String#stripLeading}、
 * {@code String#isBlank}、{@code String#repeat}。
 */
public final class TextUtil {

    private TextUtil() {
    }

    public static String strip(String value) {
        if (value == null) {
            return null;
        }
        return stripTrailing(stripLeading(value));
    }

    public static String stripLeading(String value) {
        if (value == null) {
            return null;
        }
        int length = value.length();
        int index = 0;
        while (index < length && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index == 0 ? value : value.substring(index);
    }

    public static String stripTrailing(String value) {
        if (value == null) {
            return null;
        }
        int index = value.length();
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index == value.length() ? value : value.substring(0, index);
    }

    public static boolean isBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String repeat(String value, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (value == null || count == 0 || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
