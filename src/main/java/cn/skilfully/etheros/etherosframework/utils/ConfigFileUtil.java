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
package cn.skilfully.etheros.etherosframework.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigFileUtil {

    /**
     * 创建工作目录
     * @param pluginName 插件名
     * @return 插件工作目录，一般是 plugins/Etheros/{pluginName}。当创建失败时返回null
     */
    public static File createWorkDirectory(String pluginName) {
        String workDirectoryPath = "plugins/Etheros/" + pluginName;
        Path path = Paths.get(workDirectoryPath);
        File directory = path.toFile();
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                return null;
            }
        }
        return directory;
    }

    /**
     * 获取工作目录url
     * @param pluginName 插件名
     * @return 工作目录 e.g. plugins/Etheros/EtherosCore/
     */
    public static String getWorkDirectory(String pluginName) {
        return "plugins/Etheros/" + pluginName + "/";
    }

    /**
     * 从Jar包的resources目录提取文件到目标位置
     * @param clazz 插件本体，一般类型为JavaPlugin
     * @param resourcePath 资源文件路径
     * @param toFile 将文件保存到
     * @param overwrite 如果文件已存在是否覆盖
     * @return 文件复制成功后的对象
     * @throws IOException
     */
    public static File extractFileFromJarResources(
            Class<?> clazz,
            String resourcePath,
            File toFile,
            boolean overwrite
    ) throws IOException {
        if (toFile.exists() && !overwrite) {
            return toFile;
        }

        File parentDir = toFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return null;
            }
        }

        try (InputStream in = clazz.getResourceAsStream("/" + resourcePath);
             OutputStream out = Files.newOutputStream(toFile.toPath())) {

            if (in == null) {
                return null;
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return toFile;
        }
    }

}
