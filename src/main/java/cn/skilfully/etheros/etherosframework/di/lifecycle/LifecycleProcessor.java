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
package cn.skilfully.etheros.etherosframework.di.lifecycle;

import cn.skilfully.etheros.etherosframework.di.exception.BeanCreationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LifecycleProcessor {

    private static final Logger LOG = Logger.getLogger("EtherosFramework-Lifecycle");

    public void invokePostConstruct(Object bean, List<Method> methods) {
        for (Method method : methods) {
            try {
                method.invoke(bean);
            } catch (Exception e) {
                throw new BeanCreationException(
                        "Failed to invoke @PostConstruct on " + bean.getClass().getName() + "." + method.getName(), e);
            }
        }
    }

    public void invokePreDestroy(Object bean, List<Method> methods) {
        for (Method method : methods) {
            try {
                method.invoke(bean);
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "Failed to invoke @PreDestroy on " + bean.getClass().getName() + "." + method.getName(), e);
            }
        }
    }
}
