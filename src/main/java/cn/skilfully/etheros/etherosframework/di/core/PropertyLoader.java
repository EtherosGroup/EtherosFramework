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
package cn.skilfully.etheros.etherosframework.di.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertyLoader {

    private static final String APPLICATION_PROPERTIES = "application.properties";
    private final Properties properties = new Properties();
    private boolean loaded;

    public PropertyLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public PropertyLoader(ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(APPLICATION_PROPERTIES)) {
            if (in != null) {
                properties.load(in);
                loaded = true;
            }
        } catch (IOException ignored) {
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean isLoaded() {
        return loaded;
    }
}
