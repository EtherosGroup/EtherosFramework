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

import cn.skilfully.etheros.etherosframework.di.annotation.Service;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassFileAnnotationsTest {

    private static final String SERVICE_DESCRIPTOR =
            "Lcn/skilfully/etheros/etherosframework/di/annotation/Service;";

    @Test
    void readsClassLevelAnnotation() throws IOException {
        Set<String> descriptors = ClassFileAnnotations.readClassAnnotationDescriptors(bytesOf(Annotated.class));
        assertTrue(descriptors.contains(SERVICE_DESCRIPTOR), "descriptors: " + descriptors);
    }

    @Test
    void readsNoAnnotation() throws IOException {
        assertEquals(Collections.emptySet(), ClassFileAnnotations.readClassAnnotationDescriptors(bytesOf(Plain.class)));
    }

    @Test
    void ignoresClassFileVersion() throws IOException {
        byte[] original = bytesOf(Annotated.class);
        int[] majorVersions = {45, 52, 61, 65, 69, 71};
        Set<String> expected = new LinkedHashSet<String>();
        expected.add(SERVICE_DESCRIPTOR);
        for (int major : majorVersions) {
            byte[] patched = original.clone();
            patched[6] = (byte) (major >>> 8);
            patched[7] = (byte) major;
            assertEquals(expected,
                    ClassFileAnnotations.readClassAnnotationDescriptors(patched),
                    "major version " + major);
        }
    }

    @Test
    void rejectsBadMagic() throws IOException {
        byte[] bytes = bytesOf(Annotated.class);
        bytes[0] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> ClassFileAnnotations.readClassAnnotationDescriptors(bytes));
    }

    @Service
    static class Annotated {
    }

    static class Plain {
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = ClassFileAnnotationsTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing class resource: " + resource);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
