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
package cn.skilfully.etheros.etherosframework.hook.core;

import cn.skilfully.etheros.etherosframework.hook.entity.HookEvent;
import cn.skilfully.etheros.etherosframework.hook.entity.Priority;

import java.util.List;
import java.util.function.Consumer;

public interface HookManager {

    /**
     * 注册钩子
     * @param eventType 事件类型
     * @param hook 钩子实体
     * @return 钩子ID
     */
    String register(String eventType, Consumer<HookEvent> hook, Priority priority);

    /**
     * 注册绑定多事件钩子
     * @param eventTypes 事件类型
     * @param hook 钩子实体
     * @return 钩子ID
     */
    String register(List<String> eventTypes, Consumer<HookEvent> hook, Priority priority);

    /**
     * 调用所有事件钩子
     * @param eventType 事件类型
     * @param eventData 事件参数
     */
    void callEvent(String eventType, HookEvent eventData);

    /**
     * 调用指定钩子
     * @param hookId 钩子ID
     * @param eventData 时间参数
     */
    void callHook(String hookId, HookEvent eventData);

    /**
     * 移除钩子
     * @param eventType 事件类型
     * @param hook 钩子实体
     */
    void remove(String eventType, Consumer<HookEvent> hook);

    /**
     * 移除钩子
     * @param hookId 钩子ID
     */
    void remove(String hookId);

    /**
     * 移除所有钩子
     * @param eventType 事件类型
     */
    void removeAll(String eventType);

}
