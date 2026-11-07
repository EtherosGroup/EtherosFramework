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

/**
 * 自定义字段注入扩展点。
 *
 * <p>框架内置的注入注解只有 {@code @Autowired} / {@code @GlobalAutowired} / {@code @Value}。
 * 需要在扩展库里提供自定义注入（例如日志器、配置对象）时，实现本接口并通过
 * {@link FieldInjectionProviders#register(FieldInjectionProvider)} 注册，框架会：
 * <ol>
 *   <li>在 Bean 扫描阶段用 {@link #supports(Field)} 判断字段是否需要注入；</li>
 *   <li>在字段注入阶段调用 {@link #resolve(Field, String, ApplicationContext)} 取注入值。</li>
 * </ol>
 *
 * <p>注册必须发生在 {@code ApplicationContext.run(...)} 之前。
 *
 * @author 西博卡兹
 * @since 2.5.0
 */
public interface FieldInjectionProvider {

    /**
     * 是否由本提供者处理该字段（在 Bean 扫描阶段调用）。
     *
     * @param field 候选字段
     * @return 需要注入时返回 {@code true}
     */
    boolean supports(Field field);

    /**
     * 解析字段的注入值。
     *
     * @param field   目标字段
     * @param beanName 该字段所在 Bean 的名称
     * @param context 当前应用上下文
     * @return 注入值；返回 {@code null} 表示跳过注入，字段保留默认值
     * @throws Exception 解析失败时抛出，框架会包装为 BeanCreationException
     */
    Object resolve(Field field, String beanName, ApplicationContext context) throws Exception;
}
