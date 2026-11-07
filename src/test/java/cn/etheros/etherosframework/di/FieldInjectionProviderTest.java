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
package cn.etheros.etherosframework.di;

import cn.etheros.etherosframework.di.core.ApplicationContext;
import cn.etheros.etherosframework.di.core.BeanDefinition;
import cn.etheros.etherosframework.di.fixture.Anchor;
import cn.etheros.etherosframework.di.fixture.InjectedByProvider;
import cn.etheros.etherosframework.di.fixture.ProviderBean;
import cn.etheros.etherosframework.di.spi.FieldInjectionProvider;
import cn.etheros.etherosframework.di.spi.FieldInjectionProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义字段注入扩展点的扫描、注入顺序与注册语义。
 */
class FieldInjectionProviderTest {

    static final class TestProvider implements FieldInjectionProvider {

        private final String marker;

        TestProvider(String marker) {
            this.marker = marker;
        }

        @Override
        public boolean supports(Field field) {
            return field.isAnnotationPresent(InjectedByProvider.class);
        }

        @Override
        public Object resolve(Field field, String beanName, cn.etheros.etherosframework.di.core.ApplicationContext context) {
            InjectedByProvider annotation = field.getAnnotation(InjectedByProvider.class);
            if (annotation.skip()) {
                return null;
            }
            return marker + "-" + annotation.value() + "@" + beanName;
        }
    }

    @BeforeEach
    void setUp() {
        FieldInjectionProviders.clear();
        FieldInjectionProviders.register(new TestProvider("base"));
    }

    @AfterEach
    void tearDown() {
        FieldInjectionProviders.clear();
    }

    @Test
    void testBeanDefinitionCollectsProviderFields() {
        BeanDefinition definition = BeanDefinition.from(ProviderBean.class);

        boolean hasInjected = false;
        boolean hasSkipped = false;
        for (Field field : definition.getInjectFields()) {
            if ("injected".equals(field.getName())) {
                hasInjected = true;
            }
            if ("skipped".equals(field.getName())) {
                hasSkipped = true;
            }
        }
        assertTrue(hasInjected, "provider 支持的字段应被收集");
        assertTrue(hasSkipped, "provider 支持的字段应被收集");
    }

    @Test
    void testWithoutProviderFieldNotCollected() {
        FieldInjectionProviders.clear();

        BeanDefinition definition = BeanDefinition.from(ProviderBean.class);

        assertTrue(definition.getInjectFields().isEmpty(),
                "没有 provider 时自定义注解字段不应被收集");
    }

    @Test
    void testInjectionRunsBeforePostConstruct() {
        ApplicationContext context = ApplicationContext.run(Anchor.class);
        try {
            ProviderBean bean = context.getBean(ProviderBean.class);
            assertNotNull(bean);
            assertEquals("base-alpha@providerBean", bean.getInjected());
            assertEquals("base-alpha@providerBean", bean.getPostConstructValue(),
                    "自定义注入应早于 @PostConstruct");
            assertEquals("default", bean.getSkipped(),
                    "provider 返回 null 时应保留字段默认值");
        } finally {
            context.shutdown();
        }
    }

    @Test
    void testRegisterReplacesSameImplementationClass() {
        FieldInjectionProviders.register(new TestProvider("replaced"));

        ApplicationContext context = ApplicationContext.run(Anchor.class);
        try {
            ProviderBean bean = context.getBean(ProviderBean.class);
            assertEquals("replaced-alpha@providerBean", bean.getInjected(),
                    "同实现类重复注册应替换旧实例");
        } finally {
            context.shutdown();
        }
    }
}
