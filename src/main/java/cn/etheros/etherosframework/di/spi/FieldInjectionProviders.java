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
package cn.etheros.etherosframework.di.spi;

import cn.etheros.etherosframework.di.core.ApplicationContext;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link FieldInjectionProvider} 的进程级注册表。
 *
 * <p>同一实现类重复注册时替换旧实例，便于插件重载；注册必须在
 * {@code ApplicationContext.run(...)} 之前完成。
 *
 * @author 西博卡兹
 * @since 2.5.0
 */
public final class FieldInjectionProviders {

    private static final List<FieldInjectionProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private FieldInjectionProviders() {
    }

    /**
     * 注册提供者；同实现类已存在时替换（保持注册顺序）。
     */
    public static void register(FieldInjectionProvider provider) {
        if (provider == null) {
            return;
        }
        for (int i = 0; i < PROVIDERS.size(); i++) {
            if (PROVIDERS.get(i).getClass() == provider.getClass()) {
                PROVIDERS.set(i, provider);
                return;
            }
        }
        PROVIDERS.add(provider);
    }

    /**
     * 移除与给定实例相同的提供者。
     */
    public static void unregister(FieldInjectionProvider provider) {
        PROVIDERS.remove(provider);
    }

    /**
     * 清空全部提供者（插件卸载或测试用）。
     */
    public static void clear() {
        PROVIDERS.clear();
    }

    /**
     * 是否已注册任何提供者。
     */
    public static boolean isEmpty() {
        return PROVIDERS.isEmpty();
    }

    /**
     * 是否有提供者认领该字段（Bean 扫描阶段使用）。
     */
    public static boolean supports(Field field) {
        for (FieldInjectionProvider provider : PROVIDERS) {
            if (provider.supports(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 依次询问提供者，返回第一个非 {@code null} 的注入值。
     *
     * @return 注入值；没有提供者认领或全部返回 {@code null} 时返回 {@code null}
     */
    public static Object resolve(Field field, String beanName, ApplicationContext context) throws Exception {
        for (FieldInjectionProvider provider : PROVIDERS) {
            if (!provider.supports(field)) {
                continue;
            }
            Object value = provider.resolve(field, beanName, context);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
