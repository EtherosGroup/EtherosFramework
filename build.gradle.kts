plugins {
    id("java")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "cn.skilfully.etheros"
version = "2.0.0"
description = "Etheros Framework (Java 8+ / Minecraft 1.8.x\u201326.2)"

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 类扫描为内置解析器（internal/ClassFileAnnotations），不依赖 ASM：
    // 无 class 文件版本墙，也不与 Minecraft 自带 ASM 冲突

    val lombok = "org.projectlombok:lombok:1.18.36"
    compileOnly(lombok)
    annotationProcessor(lombok)
    testCompileOnly(lombok)
    testAnnotationProcessor(lombok)
}

// 主源码为 Java 8 字节码（major 52），Java 8 到 25+ 服务端加载同一个 jar
// 测试代码不随 jar 发布，用当前 JDK 语言级别
tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

mavenPublishing {
    publishToMavenCentral(true)
    signAllPublications()

    coordinates("cn.skilfully.etheros", "EtherosFramework", project.version.toString())

    pom {
        name = rootProject.name
        description = project.description
        url = "https://github.com/EtherosGroup/EtherosFramework"

        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        developers {
            developer {
                id = "Etheros Group"
                name = "Etheros Group"
            }
        }

        scm {
            connection = "scm:git:https://github.com/EtherosGroup/EtherosFramework.git"
            developerConnection = "scm:git:https://github.com/EtherosGroup/EtherosFramework.git"
            url = "https://github.com/EtherosGroup/EtherosFramework"
        }
    }
}

signing {
    useGpgCmd()
}

afterEvaluate {
    tasks.matching { it.name == "generateMetadataFileForMavenPublication" }.configureEach {
        dependsOn(tasks.matching { it.name == "plainJavadocJar" })
    }
}
