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

/**
 * 通过文本级键值替换将配置对象写回 YAML 文件，保留原始注释和格式。
 */
public final class YamlWriter {

    private YamlWriter() {
    }

    /**
     * 将配置数据写回 YAML 文件，仅替换键值对和列表块，保留注释。
     *
     * @param file       目标 YAML 文件
     * @param configData 配置对象实例
     * @param scanResult 目标类的扫描结果
     */
    public static void replace(File file, Object configData,
                               FieldBinding.ScanResult scanResult) {
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(file.toPath()));
        } catch (IOException e) {
            throw new BindingException("Failed to read file for writing: " + file.getAbsolutePath(), e);
        }

        LineIndex index = indexLines(lines);
        writeBindings(scanResult.rootBindings(), configData, lines, index);

        try {
            Files.write(file.toPath(), lines);
        } catch (IOException e) {
            throw new BindingException("Failed to write file: " + file.getAbsolutePath(), e);
        }
    }

    private static void writeBindings(List<FieldBinding> bindings, Object target,
                                      List<String> lines, LineIndex index) {
        for (FieldBinding binding : bindings) {
            if (binding.isLeaf()) {
                Object value = FieldAccessor.getField(binding.field(), target);
                String path = binding.yamlPath();
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    replaceListBlock(lines, index, path, list);
                } else {
                    replaceLine(lines, index, path, value);
                }
            } else {
                Object nested = FieldAccessor.getField(binding.field(), target);
                if (nested != null) {
                    writeBindings(binding.children(), nested, lines, index);
                }
            }
        }
    }

    private static void replaceLine(List<String> lines, LineIndex index,
                                    String path, Object value) {
        LineInfo info = index.lineMap.get(path);
        if (info == null || info.isListItem) {
            return;
        }
        String line = lines.get(info.lineIndex);
        String valueStr = formatScalar(value);
        String prefix = line.substring(0, info.valueStart);
        int commentStart = commentAwareIndexOf(line, info.valueStart);
        String suffix = commentStart > 0 ? line.substring(commentStart) : "";
        lines.set(info.lineIndex, prefix + valueStr + suffix);
    }

    private static void replaceListBlock(List<String> lines, LineIndex index,
                                         String path, List<?> newItems) {
        LineInfo info = index.lineMap.get(path);
        ListBlock block = index.listBlocks.get(path);
        if (info == null || block == null) {
            return;
        }

        for (int i = block.endLine; i >= block.startLine; i--) {
            lines.remove(i);
        }

        String indent = TextUtil.repeat(" ", block.indent);
        List<String> newLines = new ArrayList<>();
        for (Object item : newItems) {
            newLines.add(indent + "- " + formatScalar(item));
        }
        lines.addAll(block.startLine, newLines);
    }

    static String formatScalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            return value.toString();
        }
        if (value instanceof String) {
            String s = (String) value;
            if (needsQuoting(s)) {
                return '"' + s + '"';
            }
            return s;
        }
        return value.toString();
    }

    private static boolean needsQuoting(String s) {
        if (s.isEmpty()) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']'
                    || c == ',' || c == '&' || c == '*' || c == '!' || c == '|'
                    || c == '>' || c == '\'' || c == '"' || c == '@' || c == '`') {
                return true;
            }
        }
        if (s.startsWith(" ") || s.endsWith(" ")) {
            return true;
        }
        String lower = s.toLowerCase();
        return "true".equals(lower) || "false".equals(lower) || "null".equals(lower) || "~".equals(lower);
    }

    static LineIndex indexLines(List<String> lines) {
        int indentSize = detectIndent(lines);
        LineIndex index = new LineIndex();
        Deque<String> nesting = new ArrayDeque<>();
        Deque<Integer> nestingDepths = new ArrayDeque<>();

        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (isBlank(rawLine) || isCommentOnly(rawLine)) {
                continue;
            }

            int depth = countLeadingSpaces(rawLine) / indentSize;
            String stripped = TextUtil.stripLeading(rawLine);

            while (!nesting.isEmpty() && nestingDepths.peek() != null
                    && nestingDepths.peek() >= depth) {
                nesting.pop();
                nestingDepths.pop();
            }

            if (isListItem(stripped)) {
                String path = joinDeque(nesting);
                int dashIdx = rawLine.indexOf('-');
                int indent = dashIdx;
                final int lineIdx = i;
                final int listIndent = indent;
                index.lineMap.putIfAbsent(path, new LineInfo(lineIdx, dashIdx + 2, true));
                ListBlock block = index.listBlocks.computeIfAbsent(path,
                        k -> new ListBlock(lineIdx, lineIdx, listIndent));
                block.endLine = lineIdx;
                continue;
            }

            int colon = stripped.indexOf(':');
            if (colon < 0) {
                continue;
            }

            String key = TextUtil.strip(stripped.substring(0, colon));
            String rawValue = colon + 1 < stripped.length()
                    ? TextUtil.strip(stripped.substring(colon + 1)) : "";

            if (rawValue.isEmpty()) {
                nesting.push(key);
                nestingDepths.push(depth);
                continue;
            }

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
            int colonInLine = rawLine.indexOf(':', 0);
            int valueStart = colonInLine + 1;
            while (valueStart < rawLine.length() && rawLine.charAt(valueStart) == ' ') {
                valueStart++;
            }
            index.lineMap.put(fullPath, new LineInfo(i, valueStart, false));
        }

        return index;
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
        return TextUtil.stripLeading(line).startsWith("#");
    }

    private static boolean isListItem(String line) {
        return TextUtil.stripLeading(line).startsWith("- ");
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

    private static int commentAwareIndexOf(String line, int fromIndex) {
        for (int i = fromIndex; i < line.length(); i++) {
            if (line.charAt(i) == '#') {
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

    static final class LineInfo {
        final int lineIndex;
        final int valueStart;
        final boolean isListItem;

        LineInfo(int lineIndex, int valueStart, boolean isListItem) {
            this.lineIndex = lineIndex;
            this.valueStart = valueStart;
            this.isListItem = isListItem;
        }
    }

    static final class ListBlock {
        final int startLine;
        int endLine;
        final int indent;

        ListBlock(int startLine, int endLine, int indent) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.indent = indent;
        }
    }

    static final class LineIndex {
        final Map<String, LineInfo> lineMap = new LinkedHashMap<>();
        final Map<String, ListBlock> listBlocks = new LinkedHashMap<>();
    }
}
