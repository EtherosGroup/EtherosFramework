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
package cn.skilfully.etheros.etherosframework.utils.config.yaml;

import cn.skilfully.etheros.etherosframework.utils.config.yaml.annotation.ConfigNode;
import cn.skilfully.etheros.etherosframework.utils.config.yaml.annotation.YamlConfig;

import java.util.List;

@YamlConfig
public class TestConfig {

    private String topLevel = "default";

    private Boolean enabled = true;

    private Server server = new Server();

    private AuthSetting authSetting = new AuthSetting();

    public static class Server {
        private String name;
        private Boolean enabled = true;
    }

    public static class AuthSetting {
        private List<String> banndList;
        @ConfigNode("kick-message")
        private String message;
    }
}
