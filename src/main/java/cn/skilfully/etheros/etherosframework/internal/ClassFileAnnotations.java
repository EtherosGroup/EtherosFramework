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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * class 文件类级注解读取器。
 *
 * <p>只解析 class 文件常量池与类级 {@code RuntimeVisibleAnnotations} /
 * {@code RuntimeInvisibleAnnotations} 属性，不加载类、不解析方法体，因此不受
 * JDK class 文件版本限制：Java 8 到以后任何版本的 class 文件走同一条路径。
 */
public final class ClassFileAnnotations {

    private ClassFileAnnotations() {
    }

    /**
     * 读取 class 文件中声明在类上的注解 descriptor，例如
     * {@code Lcn/skilfully/etheros/etherosframework/di/annotation/Service;}。
     *
     * @param classFile class 文件字节
     * @return 注解 descriptor 集合，无注解时为空集合
     * @throws IllegalArgumentException class 文件损坏或常量池出现未知 tag
     */
    public static Set<String> readClassAnnotationDescriptors(byte[] classFile) {
        Cursor cursor = new Cursor(classFile);
        if (cursor.u4() != 0xCAFEBABEL) {
            throw new IllegalArgumentException("Not a class file: bad magic");
        }
        cursor.skip(4); // minor_version, major_version —— 不做版本校验
        String[] pool = readConstantPool(cursor);
        cursor.skip(6); // access_flags, this_class, super_class
        cursor.skip(cursor.u2() * 2); // interfaces

        int fields = cursor.u2();
        for (int i = 0; i < fields; i++) {
            skipMember(cursor);
        }
        int methods = cursor.u2();
        for (int i = 0; i < methods; i++) {
            skipMember(cursor);
        }
        return readClassAttributes(cursor, pool);
    }

    private static String[] readConstantPool(Cursor cursor) {
        int count = cursor.u2();
        String[] pool = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = cursor.u1();
            switch (tag) {
                case 1: // Utf8
                    pool[i] = cursor.utf8();
                    break;
                case 7: // Class
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    cursor.skip(2);
                    break;
                case 15: // MethodHandle
                    cursor.skip(3);
                    break;
                case 3: // Integer
                case 4: // Float
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    cursor.skip(4);
                    break;
                case 5: // Long
                case 6: // Double
                    cursor.skip(8);
                    i++; // 占两个常量池槽位
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
            }
        }
        return pool;
    }

    private static void skipMember(Cursor cursor) {
        cursor.skip(6); // access_flags, name_index, descriptor_index
        int attributes = cursor.u2();
        for (int i = 0; i < attributes; i++) {
            cursor.skip(2); // attribute_name_index
            cursor.skip(cursor.u4()); // attribute_length + info
        }
    }

    private static Set<String> readClassAttributes(Cursor cursor, String[] pool) {
        Set<String> descriptors = new LinkedHashSet<String>();
        int attributes = cursor.u2();
        for (int i = 0; i < attributes; i++) {
            int nameIndex = cursor.u2();
            long length = cursor.u4();
            int end = cursor.position() + (int) length;
            String name = pool[nameIndex];
            if ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name)) {
                readAnnotations(cursor, pool, descriptors);
            }
            cursor.position(end); // 未知属性按长度跳过
        }
        return Collections.unmodifiableSet(descriptors);
    }

    private static void readAnnotations(Cursor cursor, String[] pool, Set<String> out) {
        int count = cursor.u2();
        for (int i = 0; i < count; i++) {
            int typeIndex = cursor.u2();
            if (typeIndex > 0 && typeIndex < pool.length && pool[typeIndex] != null) {
                out.add(pool[typeIndex]);
            }
            skipElementValuePairs(cursor);
        }
    }

    private static void skipAnnotation(Cursor cursor) {
        cursor.skip(2); // type_index
        skipElementValuePairs(cursor);
    }

    private static void skipElementValuePairs(Cursor cursor) {
        int pairs = cursor.u2();
        for (int i = 0; i < pairs; i++) {
            cursor.skip(2); // element_name_index
            skipElementValue(cursor);
        }
    }

    private static void skipElementValue(Cursor cursor) {
        int tag = cursor.u1();
        switch (tag) {
            case 'B': // byte
            case 'C': // char
            case 'D': // double
            case 'F': // float
            case 'I': // int
            case 'J': // long
            case 'S': // short
            case 'Z': // boolean
            case 's': // String
            case 'c': // class
                cursor.skip(2);
                break;
            case 'e': // enum
                cursor.skip(4);
                break;
            case '@': // 嵌套注解
                skipAnnotation(cursor);
                break;
            case '[': // 数组
                int values = cursor.u2();
                for (int i = 0; i < values; i++) {
                    skipElementValue(cursor);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported element value tag: " + (char) tag);
        }
    }

    private static final class Cursor {

        private final byte[] data;
        private int position;

        Cursor(byte[] data) {
            this.data = data;
        }

        int position() {
            return position;
        }

        void position(int value) {
            require(value - position);
            position = value;
        }

        void skip(long count) {
            require(count);
            position += (int) count;
        }

        int u1() {
            require(1);
            return data[position++] & 0xFF;
        }

        int u2() {
            require(2);
            int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
            position += 2;
            return value;
        }

        long u4() {
            require(4);
            long value = ((long) (data[position] & 0xFF) << 24)
                    | ((data[position + 1] & 0xFF) << 16)
                    | ((data[position + 2] & 0xFF) << 8)
                    | (data[position + 3] & 0xFF);
            position += 4;
            return value;
        }

        String utf8() {
            int length = u2();
            require(length);
            String value = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        private void require(long count) {
            if (count < 0 || position + count > data.length) {
                throw new IllegalArgumentException("Truncated class file at offset " + position);
            }
        }
    }
}
