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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 事件载荷
 *
 * <p>载荷只允许 JDK 值类型：String、包装类型、UUID、JDK 枚举、JDK 集合/数组/Optional
 * <p>插件自定义类型与平台对象不要放进载荷：不同插件的 classloader 下类型不一致
 * <p>Map 键必须是非空字符串；单次校验最多 {@link PayloadValidator#MAX_NODES} 个节点
 *
 * @author 西博卡兹
 * @since 1.0.0
 */
public class BusEvent {

    private Map<String, Object> data;
    private boolean cancelled;
    private String cancelReason;

    public BusEvent() {
    }

    public BusEvent(Map<String, Object> data, boolean cancelled, String cancelReason) {
        setData(data);
        this.cancelled = cancelled;
        this.cancelReason = cancelReason;
    }

    public Map<String, Object> getData() {
        return data == null ? null : Collections.unmodifiableMap(data);
    }

    /**
     * 设置载荷，逐项校验类型、键、环引用与节点上限
     */
    public BusEvent setData(Map<String, Object> data) {
        this.data = PayloadValidator.validateMap(data);
        return this;
    }

    public BusEvent put(String key, String value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, long value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, double value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, boolean value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, UUID value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    /**
     * 枚举以名称字符串存入载荷，避免插件自定义枚举的类加载器差异
     */
    public BusEvent put(String key, Enum<?> value) {
        PayloadValidator.validateEntry(key, value == null ? null : value.name());
        mutableData().put(key, value == null ? null : value.name());
        return this;
    }

    public BusEvent put(String key, Collection<?> value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, Map<String, ?> value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent put(String key, Optional<?> value) {
        PayloadValidator.validateEntry(key, value);
        mutableData().put(key, value);
        return this;
    }

    public BusEvent remove(String key) {
        Objects.requireNonNull(key, "key");
        if (data != null) {
            data.remove(key);
        }
        return this;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public BusEvent setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        return this;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public BusEvent setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
        return this;
    }

    private Map<String, Object> mutableData() {
        if (data == null) {
            data = new LinkedHashMap<String, Object>();
        }
        return data;
    }
}
