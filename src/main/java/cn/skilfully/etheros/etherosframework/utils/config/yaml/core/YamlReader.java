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
import cn.skilfully.etheros.etherosframework.utils.config.yaml.exception.YamlParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 逐行解析 YAML 文本到扁平化 Map，仅解析给定路径集合中的键。
 */
public final class YamlReader {

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NULL = "null";
    private static final String NULL_ALT = "~";

    private YamlReader() {}

    /**
     * 读取 YAML 文件，仅解析 {@code targetKeys} 中的路径。
     *
     * @param file       YAML 配置文件
     * @param targetKeys 关心的路径集合
     * @return 路径 → 值的扁平映射
     * @throws YamlParseException 文件读取失败或格式异常时抛出
     */
    public static Map<String, Object> read(File file, Set<String> targetKeys) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new YamlParseException("Failed to read YAML file: " + file.getAbsolutePath(), e);
        }

        int indentSize = detectIndent(lines);
        Map<String, Object> result = new LinkedHashMap<>();
        Deque<String> nesting = new ArrayDeque<>();
        Deque<Integer> nestingDepths = new ArrayDeque<>();

        for (String rawLine : lines) {
            String line = rawLine;
            if (isBlank(line) || isCommentOnly(line)) {
                continue;
            }

            int depth = countLeadingSpaces(line) / indentSize;

            while (!nesting.isEmpty() && nestingDepths.peek() != null && nestingDepths.peek() >= depth) {
                nesting.pop();
                nestingDepths.pop();
            }

            line = TextUtil.stripLeading(line);

            if (isListItem(line)) {
                String currentPath = joinDeque(nesting);
                if (targetKeys.contains(currentPath)) {
                    String rawValue = TextUtil.strip(line.substring(line.indexOf('-') + 1));
                    Object parsed = parseValue(rawValue);
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) result.computeIfAbsent(currentPath,
                            k -> new ArrayList<>());
                    list.add(parsed);
                }
                continue;
            }

            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }

            String key = TextUtil.strip(line.substring(0, colon));
            String rawValue = colon + 1 < line.length() ? TextUtil.strip(line.substring(colon + 1)) : "";

            if (rawValue.isEmpty()) {
                nesting.push(key);
                nestingDepths.push(depth);
                continue;
            }

            String commentPrefix = rawValue + " #";
            String valueStr = rawValue;
            int commentIdx = commentAwareIndexOf(rawValue);
            if (commentIdx >= 0) {
                valueStr = TextUtil.strip(rawValue.substring(0, commentIdx));
            }

            if (valueStr.isEmpty()) {
                nesting.push(key);
                nestingDepths.push(depth);
                continue;
            }

            String fullPath = buildPath(nesting, key);
            if (targetKeys.contains(fullPath)) {
                result.put(fullPath, parseValue(valueStr));
            }
        }

        return result;
    }

    public static Object parseValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (NULL.equalsIgnoreCase(raw) || NULL_ALT.equals(raw)) {
            return null;
        }
        if (TRUE.equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if (FALSE.equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        if (isQuoted(raw)) {
            return raw.substring(1, raw.length() - 1);
        }
        try {
            long longVal = Long.parseLong(raw);
            if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                return (int) longVal;
            }
            return longVal;
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    private static int detectIndent(List<String> lines) {
        for (String line : lines) {
            if (isBlank(line) || isCommentOnly(line)) {
                continue;
            }
            int spaces = countLeadingSpaces(line);
            if (spaces > 0) {
                return spaces;
            }
        }
        return 2;
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static boolean isBlank(String line) {
        return TextUtil.isBlank(line);
    }

    private static boolean isCommentOnly(String line) {
        String stripped = TextUtil.stripLeading(line);
        return stripped.startsWith("#");
    }

    private static boolean isListItem(String line) {
        return TextUtil.stripLeading(line).startsWith("- ");
    }

    private static boolean isQuoted(String value) {
        if (value.length() < 2) {
            return false;
        }
        char first = value.charAt(0);
        return (first == '"' || first == '\'') && first == value.charAt(value.length() - 1);
    }

    private static int commentAwareIndexOf(String value) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return i;
            }
        }
        return -1;
    }

    private static String joinDeque(Deque<String> deque) {
        if (deque.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = deque.descendingIterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    private static String buildPath(Deque<String> nesting, String key) {
        String prefix = joinDeque(nesting);
        if (prefix.isEmpty()) {
            return key;
        }
        return prefix + "." + key;
    }
}
