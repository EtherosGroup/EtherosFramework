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

import java.util.List;
import java.util.function.Consumer;

/**
 * 按事件类型发布与订阅的事件总线
 */
public interface EventBus {

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param listener 监听器
     * @param priority 优先级
     * @return 取消该订阅的句柄
     */
    Subscription subscribe(String eventType, Consumer<BusEvent> listener, Priority priority);

    /**
     * 订阅多个事件类型
     *
     * @param eventTypes 事件类型
     * @param listener 监听器
     * @param priority 优先级
     * @return 取消该订阅的句柄
     */
    Subscription subscribe(List<String> eventTypes, Consumer<BusEvent> listener, Priority priority);

    /**
     * 投递事件
     *
     * @param eventType 事件类型
     * @param event 事件载荷
     */
    void post(String eventType, BusEvent event);

    /**
     * 订阅句柄
     */
    interface Subscription {

        /**
         * 取消订阅
         */
        void unsubscribe();
    }
}
