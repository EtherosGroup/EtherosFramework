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
package cn.etheros.etherosframework.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusEventTest {

    /** 插件自定义载荷，校验应拒绝 */
    private static final class UnsafePayload {
    }

    @Test
    void acceptsSafeValueTypes() {
        BusEvent event = new BusEvent()
                .put("player", "Steve")
                .put("playerId", UUID.randomUUID())
                .put("level", 12)
                .put("ratio", 0.5D)
                .put("online", true)
                .put("tags", Arrays.asList("a", "b"));

        assertEquals("Steve", event.getData().get("player"));
        assertEquals(12L, event.getData().get("level"));
        assertEquals(Arrays.asList("a", "b"), event.getData().get("tags"));
        assertTrue(event.getData().get("playerId") instanceof UUID);
    }

    @Test
    void storesEnumName() {
        BusEvent event = new BusEvent().put("mode", Mode.FAST);
        assertEquals("FAST", event.getData().get("mode"));
    }

    @Test
    void rejectsPluginClassValue() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("payload", new UnsafePayload());
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(data));
    }

    @Test
    void rejectsPluginClassInsideCollection() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("list", Collections.singletonList(new UnsafePayload()));
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(data));
    }

    @Test
    void rejectsNonStringNestedKey() {
        Map<Object, Object> nested = new HashMap<Object, Object>();
        nested.put(1, "value");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("nested", nested);
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) (Map<?, ?>) data;
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(raw));
    }

    @Test
    void rejectsCycle() {
        List<Object> list = new ArrayList<Object>();
        list.add(list);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("list", list);
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(data));
    }

    @Test
    void rejectsPayloadOverNodeBudget() {
        List<String> values = new ArrayList<String>();
        for (int index = 0; index < PayloadValidator.MAX_NODES + 1; index++) {
            values.add("v" + index);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("values", values);
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(data));
    }

    @Test
    void acceptsDeepNestingWithinNodeBudget() {
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        Map<String, Object> current = nested;
        for (int depth = 0; depth < 200; depth++) {
            Map<String, Object> child = new LinkedHashMap<String, Object>();
            current.put("child", child);
            current = child;
        }
        current.put("value", "leaf");

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("root", nested);

        BusEvent event = new BusEvent().setData(data);
        assertNotNull(event.getData());
    }

    @Test
    void acceptsOptionalAndRejectsUnsafeOptional() {
        Map<String, Object> safe = new LinkedHashMap<String, Object>();
        safe.put("value", Optional.of("ok"));
        assertNotNull(new BusEvent().setData(safe).getData());

        Map<String, Object> unsafe = new LinkedHashMap<String, Object>();
        unsafe.put("value", Optional.of(new UnsafePayload()));
        assertThrows(IllegalArgumentException.class, () -> new BusEvent().setData(unsafe));
    }

    @Test
    void dataViewIsUnmodifiable() {
        BusEvent event = new BusEvent().put("player", "Steve");
        assertThrows(UnsupportedOperationException.class, () -> event.getData().put("x", "y"));
    }

    private enum Mode {
        FAST, SLOW
    }
}
