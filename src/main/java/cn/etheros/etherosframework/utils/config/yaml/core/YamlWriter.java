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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
        List<ListReplacement> replacements = new ArrayList<>();
        writeBindings(scanResult.rootBindings(), configData, lines, index, replacements);
        applyListReplacements(lines, replacements);

        try {
            Files.write(file.toPath(), lines);
        } catch (IOException e) {
            throw new BindingException("Failed to write file: " + file.getAbsolutePath(), e);
        }
    }

    private static void writeBindings(List<FieldBinding> bindings, Object target,
                                      List<String> lines, LineIndex index,
                                      List<ListReplacement> replacements) {
        for (FieldBinding binding : bindings) {
            if (binding.isLeaf()) {
                Object value = FieldAccessor.getField(binding.field(), target);
                String path = binding.yamlPath();
                if (value instanceof Collection) {
                    collectListReplacement(index, path,
                            new ArrayList<Object>((Collection<?>) value), replacements);
                } else if (value != null && value.getClass().isArray()) {
                    collectListReplacement(index, path, arrayValues(value), replacements);
                } else {
                    replaceLine(lines, index, path, value);
                }
            } else {
                Object nested = FieldAccessor.getField(binding.field(), target);
                if (nested != null) {
                    writeBindings(binding.children(), nested, lines, index, replacements);
                }
            }
        }
    }

    private static List<Object> arrayValues(Object array) {
        int length = Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            values.add(Array.get(array, i));
        }
        return values;
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
        lines.set(info.lineIndex, prefix + valueStr + trailingSuffix(line, info.valueStart));
    }

    /**
     * 取「值之后到行尾」的原文，包含值与原注释之间的空白；没有行内注释时返回空串。
     */
    private static String trailingSuffix(String line, int valueStart) {
        int commentStart = commentAwareIndexOf(line, valueStart);
        if (commentStart < 0) {
            return "";
        }
        int suffixStart = commentStart;
        while (suffixStart > valueStart && line.charAt(suffixStart - 1) == ' ') {
            suffixStart--;
        }
        return line.substring(suffixStart);
    }

    private static void collectListReplacement(LineIndex index, String path, List<?> newItems,
                                               List<ListReplacement> replacements) {
        LineInfo info = index.lineMap.get(path);
        ListBlock block = index.listBlocks.get(path);
        if (info == null || block == null) {
            return;
        }

        String indent = TextUtil.repeat(" ", block.indent);
        List<BlockEntry> remaining = new ArrayList<>(block.entries);
        List<String> newLines = new ArrayList<>(newItems.size());
        for (Object item : newItems) {
            String formatted = formatScalar(item);
            BlockEntry match = findEntry(remaining, formatted);
            if (match == null) {
                newLines.add(indent + "- " + formatted);
            } else {
                newLines.addAll(match.headerComments);
                newLines.add(indent + "- " + formatted + match.inlineSuffix);
            }
        }
        replacements.add(new ListReplacement(block.startLine, block.endLine, newLines));
    }

    /**
     * 在尚未匹配的旧条目中查找同值条目，命中后从候选中移除，保证注释只复用一次。
     */
    private static BlockEntry findEntry(List<BlockEntry> remaining, String itemText) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).itemText.equals(itemText)) {
                return remaining.remove(i);
            }
        }
        return null;
    }

    private static BlockEntry createEntry(String rawLine, int dashIdx) {
        int valueStart = dashIdx + 1;
        while (valueStart < rawLine.length() && rawLine.charAt(valueStart) == ' ') {
            valueStart++;
        }

        String tail = rawLine.substring(valueStart);
        int commentIdx = commentAwareIndexOf(tail);
        int valueRegionEnd = commentIdx >= 0 ? valueStart + commentIdx : rawLine.length();

        int trimmedEnd = valueRegionEnd;
        while (trimmedEnd > valueStart && rawLine.charAt(trimmedEnd - 1) == ' ') {
            trimmedEnd--;
        }

        String itemText = TextUtil.strip(rawLine.substring(valueStart, valueRegionEnd));
        String inlineSuffix = commentIdx >= 0 ? rawLine.substring(trimmedEnd) : "";
        return new BlockEntry(itemText, inlineSuffix);
    }

    /**
     * 从文件末尾向前应用列表块替换，避免多处替换时行号互相错位。
     */
    private static void applyListReplacements(List<String> lines,
                                              List<ListReplacement> replacements) {
        replacements.sort((left, right) -> Integer.compare(right.startLine, left.startLine));
        for (ListReplacement replacement : replacements) {
            for (int i = replacement.endLine; i >= replacement.startLine; i--) {
                lines.remove(i);
            }
            lines.addAll(replacement.startLine, replacement.newLines);
        }
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

        List<String> pendingComments = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (isBlank(rawLine)) {
                continue;
            }
            if (isCommentOnly(rawLine)) {
                pendingComments.add(rawLine);
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
                index.lineMap.putIfAbsent(path, new LineInfo(i, dashIdx + 2, true));

                BlockEntry entry = createEntry(rawLine, dashIdx);
                ListBlock block = index.listBlocks.get(path);
                if (block == null) {
                    block = new ListBlock(i, i, dashIdx);
                    index.listBlocks.put(path, block);
                    // 块首条目之前的注释在替换范围之外，原样保留
                    pendingComments.clear();
                } else {
                    entry.headerComments.addAll(pendingComments);
                    pendingComments.clear();
                }
                block.entries.add(entry);
                block.endLine = i;
                continue;
            }

            // 非列表行：块外注释不参与块重写
            pendingComments.clear();

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

    static final class ListReplacement {
        final int startLine;
        final int endLine;
        final List<String> newLines;

        ListReplacement(int startLine, int endLine, List<String> newLines) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.newLines = newLines;
        }
    }

    static final class BlockEntry {
        final String itemText;
        final String inlineSuffix;
        final List<String> headerComments = new ArrayList<>();

        BlockEntry(String itemText, String inlineSuffix) {
            this.itemText = itemText;
            this.inlineSuffix = inlineSuffix;
        }
    }

    static final class ListBlock {
        final int startLine;
        int endLine;
        final int indent;
        final List<BlockEntry> entries = new ArrayList<>();

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
