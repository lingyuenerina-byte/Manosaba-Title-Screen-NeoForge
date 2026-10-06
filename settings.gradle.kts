pluginManagement {
    resolutionStrategy {
        // Gradle Plugin Portal（plugins.gradle.org）在当前网络环境无法直连（连接超时可复现），
        // 而 shadow 插件的 marker 仅发布在 Portal 上，会导致插件解析失败。
        // 将插件 ID 直接映射到 Maven Central 上的实现模块坐标（mavenCentral() 已覆盖）。
        eachPlugin {
            if (requested.id.id == "com.gradleup.shadow") {
                useModule("com.gradleup.shadow:shadow-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 注意：ModDevGradle 会注册项目级仓库，默认 PREFER_PROJECT 模式下此处声明会被忽略，
    // 实际生效的依赖仓库见 build.gradle.kts 的 repositories 块。
    repositories {
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
    }
}

rootProject.name = "Manosaba"
