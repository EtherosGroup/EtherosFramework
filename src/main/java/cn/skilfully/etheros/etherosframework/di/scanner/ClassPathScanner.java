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
package cn.skilfully.etheros.etherosframework.di.scanner;

import cn.skilfully.etheros.etherosframework.internal.ClassFileAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassPathScanner {

    private static final Set<String> TARGET_DESCRIPTORS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "Lcn/skilfully/etheros/etherosframework/di/annotation/Service;",
            "Lcn/skilfully/etheros/etherosframework/di/annotation/GlobalService;",
            "Lcn/skilfully/etheros/etherosframework/di/annotation/Configuration;",
            "Lcn/skilfully/etheros/etherosframework/di/annotation/Prototype;"
    )));

    private final ClassLoader classLoader;

    public ClassPathScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Set<String> scan(String basePackage) {
        Set<String> candidates = new LinkedHashSet<String>();
        String path = basePackage.replace('.', '/');

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    scanDirectory(toFile(url), basePackage, candidates);
                } else if ("jar".equals(protocol)) {
                    scanJar(url, path, candidates);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan package: " + basePackage, e);
        }

        return candidates;
    }

    private void scanDirectory(File dir, String basePackage, Set<String> candidates) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, basePackage + "." + file.getName(), candidates);
            } else if (file.getName().endsWith(".class")) {
                String className = basePackage + "."
                        + file.getName().substring(0, file.getName().length() - 6);
                if (hasTargetAnnotation(className)) {
                    candidates.add(className);
                }
            }
        }
    }

    private void scanJar(URL jarUrl, String path, Set<String> candidates) {
        try {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(path) && name.endsWith(".class")) {
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        if (hasTargetAnnotation(className)) {
                            candidates.add(className);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan jar: " + jarUrl, e);
        }
    }

    // file: URL 的路径经过百分号编码，非 ASCII 与空格路径需先还原
    private static File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid classpath URL: " + url, e);
        }
    }

    private boolean hasTargetAnnotation(String className) {
        String classPath = className.replace('.', '/') + ".class";
        try (InputStream in = classLoader.getResourceAsStream(classPath)) {
            if (in == null) return false;
            Set<String> descriptors = ClassFileAnnotations.readClassAnnotationDescriptors(readAll(in));
            for (String descriptor : descriptors) {
                if (TARGET_DESCRIPTORS.contains(descriptor)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to parse class file: " + className, e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
