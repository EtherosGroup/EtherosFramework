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
package cn.etheros.etherosframework.di.fixture;

import cn.etheros.etherosframework.di.annotation.PostConstruct;
import cn.etheros.etherosframework.di.annotation.Service;

/**
 * 测试用 Bean：字段由 {@link InjectedByProvider} 提供者注入。
 */
@Service
public class ProviderBean {

    @InjectedByProvider("alpha")
    private String injected;

    @InjectedByProvider(skip = true)
    private String skipped = "default";

    private String postConstructValue;

    @PostConstruct
    void init() {
        this.postConstructValue = injected;
    }

    public String getInjected() {
        return injected;
    }

    public String getSkipped() {
        return skipped;
    }

    public String getPostConstructValue() {
        return postConstructValue;
    }
}
