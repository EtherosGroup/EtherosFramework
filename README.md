# EtherosFramework

轻量级依赖注入、钩子与配置文件解析框架，专为 Minecraft 服务端插件设计，支持跨插件 Bean 共享。

## 适用范围

- **Java 8+**（jar 内字节码为 major 52）
- **Minecraft 1.8.x – 26.2**
- **适用品牌：`Bukkit及其下游 | Folia | Velocity | BungeeCord及其下游`**

### 服务端与 JVM

| 服务端 | 最低 JVM |
|--------|---------|
| 1.8.x – 1.16.5 | Java 8 |
| 1.17 | Java 16 |
| 1.18 – 1.20.4 | Java 17 |
| 1.20.5 – 26.1 | Java 21 |
| 26.2 | Java 25 |

### 全版本通用的条件

框架本身不引用任何服务端 API，运行时只依赖 JDK。两处约束决定它能否跨版本加载：

- **类扫描不使用 ASM**：`internal/ClassFileAnnotations` 直接解析 class 文件的常量池与类级注解属性，不加载类、不校验 class 文件版本。Java 8 到 Java 25 的 class 文件走同一条路径，也不会与 Minecraft 自带 ASM 在类路径上冲突。
- **主源码编译到 Java 8 字节码**：`options.release = 8`。Java 8 到 25+ 的服务端加载同一个 jar。

构建需要 JDK 17+（Gradle 9.2 的要求），产物面向 Java 8+。

## 核心能力

| 模块 | 功能                                                                      |
|------|-------------------------------------------------------------------------|
| **DI 容器** | `@Service` / `@GlobalService` / `@Configuration` / `@Bean` 自动发现与实例化     |
| **依赖注入** | `@Autowired` / `@GlobalAutowired` 字段、构造器、Setter 注入，支持按名称和按类型            |
| **跨插件共享** | `@GlobalService` 将 Bean 自动注册到 `SharedContext`，其他插件可通过 `@Autowired` 透明获取 |
| **属性注入** | `@Value("key")` 从 `application.properties` 读取配置值                        |
| **生命周期** | `@PostConstruct` / `@PreDestroy` 回调                                     |
| **钩子系统** | `HookManager` 接口 + `HookEvent` 事件模型，支持优先级和取消传播                              |
| **YAML 解析** | `@YamlConfig` / `@ConfigNode` 注解驱动，静态内部类自动映射，写出保留注释                    |
| **字节码扫描** | 内置 class 文件注解解析，不加载类，不依赖 ASM                                              |

## 快速开始

### 选择依赖模式

框架根据是否需要**跨插件 Bean 共享**分为两种依赖模式：

---

#### 模式 A：独立使用（不跨插件共享）

每个用到框架的插件各自 shade 一份，互不干扰。

**build.gradle**（每个插件都要这样配）：

```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.6'
}

dependencies {
    implementation 'cn.skilfully.etheros:EtherosFramework:2.0.0'
}
```

---

#### 模式 B：跨插件共享 Bean（API 宿主 + 子插件）

一个插件作为 API 宿主，提供 `@GlobalService` bean；其他子插件依赖宿主，共享同一份 `SharedContext`。

**宿主插件 build.gradle**：

```groovy
plugins {
    id 'java'
    id 'com.gradleup.shadow' version '8.3.6'
}

dependencies {
    implementation 'cn.skilfully.etheros:EtherosFramework:2.0.0'
}
```

**宿主插件 plugin.yml**：

```yaml
name: ExampleCore
main: com.example.core.ExampleCore
version: 1.0.0
api-version: 1.17
```

**子插件 build.gradle**：

```groovy
plugins {
    id 'java'
}

dependencies {
    compileOnly 'cn.skilfully.etheros:EtherosFramework:2.0.0'
}
```

**子插件 plugin.yml**：

```yaml
name: ExampleAddon
main: com.example.addon.ExampleAddon
version: 1.0.0
api-version: 1.17
depend: [ExampleCore]
```

---

#### 运行时类加载链（模式 B）

```
Minecraft Server (parent ClassLoader)
 └─ ExampleCore PluginClassLoader
      ├── SharedContext.class  (static 字段 — 全局唯一)
      └─ ExampleAddon PluginClassLoader (depend: [ExampleCore])
           └─ @Autowired Foo foo → SharedContext.getBean(Foo.class) ✓
```

### 基本用法

```java
@Service
public class UserService {
    @Autowired
    private DatabaseManager db;

    @PostConstruct
    void init() {
        db.connect();
    }
}
```

```java
// 主插件入口
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        ApplicationContext.run(MyPlugin.class);
    }
}
```

### 跨插件 DI

宿主插件中标注 `@GlobalService` 的 Bean 在容器启动后自动注册到 `SharedContext`，子插件通过 `@Autowired` 即可获取：

```java
// 宿主插件
@GlobalService
public class DbManager implements DatabaseManager {
    public void connect() { /* ... */ }
}
```

```java
// 子插件 — 自动从 SharedContext 回退获取
@Service
public class UserModule {
    @Autowired
    private DatabaseManager db;   // 来自宿主插件
}
```

## YAML 配置解析

`YamlConfigurationParser` 提供注解驱动的 YAML 配置解析，静态内部类自动映射为节点，写出时保留原始注释与格式。

### 配置类定义

```java
@YamlConfig
public class PluginConfig {
    private String debugMode = "false";
    private int maxPlayers = 100;

    private Database database = new Database();
    private Messages messages = new Messages();

    public static class Database {
        private String host = "localhost";
        private int port = 3306;
        private String username;
        private String password;
        private Boolean sslEnabled = true;
    }

    public static class Messages {
        @ConfigNode("welcome-msg")
        private String welcomeMsg = "Welcome to the server!";
        private List<String> motd;
    }
}
```

对应 YAML 文件（`plugins/Etheros/ExamplePlugin/config.yml`）：

```yaml
debug-mode: true                           # true / false
max-players: 200                           # 玩家上限
database:
  host: 192.168.1.100                      # 数据库IP
  port: 3306                               # 数据库端口
  username: root                           # 数据库用户
  password: "abc#123"                      # 数据库密码
  ssl-enabled: false                       # 是否启用SSL
messages:
  welcome-msg: "&aHello %player%!"         # 欢迎语
  motd:
    - "&6欢迎来到服务器"                     # MOTD第一行
    - "&eHave fun!"                        # MOTD第二行
```

### 读取与写出

```java
// 加载
YamlConfigurationParser parser = new YamlConfigurationParser();
PluginConfig config = parser.parse(
    new File("plugins/Etheros/ExamplePlugin/config.yml"),
    PluginConfig.class
);

// 修改后写回（保留注释）
config.setDebugMode("false");
parser.write(new File("plugins/Etheros/ExamplePlugin/config.yml"), config);
```

### 命名规则

| 规则 | 示例 |
|------|------|
| 静态内部类首字母小写 | `Database` → `database` |
| 类名驼峰转短横线 | `AuthSetting` → `auth-setting` |
| 字段名驼峰转短横线 | `welcomeMsg` → `welcome-msg` |
| `@ConfigNode` 手动指定 | `@ConfigNode("kick-message")` 优先于自动转换 |

### 支持的类型

`String`、基本类型及包装类（`int`、`boolean`、`double` 等）、`List<T>`、`Map<String, Object>`，以及嵌套静态内部类。YAML 中缺失的键会保留字段上定义的默认值。

### ConfigFileUtil

```java
// 创建工作目录并释放默认配置文件
File workDir = ConfigFileUtil.createWorkDirectory("ExamplePlugin");
File configFile = ConfigFileUtil.extractFileFromJarResources(
    JavaPlugin.class, "config.yml",
    new File(workDir, "config.yml"), false
);
```

## 包结构

```
cn.skilfully.etheros.etherosframework
├── di/
│   ├── annotation/   — @Service, @GlobalService, @Autowired, @GlobalAutowired,
│   │                    @Configuration, @Bean, @Value, @PostConstruct, @PreDestroy, @Prototype
│   ├── core/          — ApplicationContext, BeanDefinition, BeanRegistry, SharedContext, PropertyLoader
│   ├── scanner/       — ClassPathScanner
│   ├── lifecycle/     — LifecycleProcessor
│   └── exception/     — BeanCreationException, BeanNotFoundException, CircularDependencyException
├── hook/
│   ├── core/          — HookManager (interface)
│   ├── entity/        — HookEvent, Priority
│   └── exception/     — HookException
├── internal/
│   ├── ClassFileAnnotations — class 文件类级注解解析
│   └── TextUtil             — Java 11 字符串方法的 Java 8 等价实现
└── utils/
    ├── config/yaml/
    │   ├── annotation/ — @YamlConfig, @ConfigNode
    │   ├── core/       — YamlReader, YamlWriter, YamlDataBinder, ClassStructureScanner, FieldBinding
    │   └── exception/  — YamlParseException, BindingException
    ├── YamlConfigurationParser.java
    └── ConfigFileUtil.java
```

## 注解速览

| 注解 | Target | 说明 |
|------|--------|------|
| `@Service` | TYPE | 声明一个 Bean，`value` 指定名称 |
| `@GlobalService` | TYPE | 同 `@Service`，额外注册到跨插件 SharedContext |
| `@Autowired` | FIELD / CONSTRUCTOR / PARAMETER | 注入 Bean（本地 → SharedContext 回退），`value` 按名称，`required` 控制可选 |
| `@GlobalAutowired` | FIELD / CONSTRUCTOR / PARAMETER | 仅从 SharedContext 注入 |
| `@Configuration` | TYPE | 标记配置类 |
| `@Bean` | METHOD | 工厂方法，`name` 指定 Bean 名称 |
| `@Value` | FIELD / PARAMETER | 从 `application.properties` 注入属性值 |
| `@PostConstruct` | METHOD | 注入完成后调用 |
| `@PreDestroy` | METHOD | 容器关闭前调用 |
| `@Prototype` | TYPE | 原型作用域，每次获取新建实例 |
| `@YamlConfig` | TYPE | 标记 YAML 配置映射类，静态内部类自动映射为节点 |
| `@ConfigNode` | FIELD | 手动指定 YAML 键名，优先于自动驼峰转短横线 |

## 钩子系统

```java
// 定义事件
HookEvent event = new HookEvent();
event.setData(Map.of("player", player));

// 注册钩子
hookManager.register("player.join", e -> {
    Player p = (Player) e.getData().get("player");
    p.sendMessage("Welcome!");
}, Priority.HIGH);

// 触发
hookManager.callEvent("player.join", event);

// 取消传播
event.setCancelled(true);
```

`HookManager` 接口需由宿主插件实现并注册为 `@GlobalService`，子插件通过 `@Autowired` 获取同一实例。

## 生命周期顺序

### 启动

```
实例化（依赖优先） → 字段注入 → 工厂方法 → @PostConstruct → 注册到 SharedContext
```

`@PostConstruct` 按**实例化顺序**执行，即被依赖者先于依赖者：

```
A 依赖 B（B → A 创建顺序）
B.@PostConstruct  →  A.@PostConstruct
```

### 关闭

`context.shutdown()` 按**实例化逆序**销毁，即依赖者先于被依赖者：

```
A 依赖 B（B → A 创建顺序，逆序为 A → B）
A.@PreDestroy  →  B.@PreDestroy
```

### 循环依赖

存在 `A → B → A` 等循环依赖时，容器抛出 `CircularDependencyException`，插件启用失败。

## 从旧版本迁移

| 旧产物 | 版本 | 适用范围 |
|--------|------|---------|
| `cn.skilfully.etheros:EtherosFramework-Yosemite` | 1.2.0 | Java 17 / MC 1.17–1.20.4 |
| `cn.skilfully.etheros:EtherosFramework-ElCapitan` | 1.0.0 | Java 21 / MC 1.20.5+ |

替换为 `cn.skilfully.etheros:EtherosFramework:2.0.0`。公开 API 不变，改动只在构建依赖与坐标。以 1.8–1.16.5 服务端为目标时，插件自身仍需编译到 Java 8。

## 构建

```bash
./gradlew clean build
```

- 主源码：`--release 8`，产物全部为 major 52
- 测试：当前 JDK 语言级别，不随 jar 发布
- 依赖：仅 JUnit（测试）与 Lombok（编译期）

## 联系

QQ群：870666822
