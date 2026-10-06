import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.shadow)
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

neoForge {
    version = libs.versions.neoforge.get()

    runs {
        register("client") {
            client()

            // BootstrapLauncher 处理 legacy classpath 时，若多个 jar 含有相同包（split package），
            // 会丢弃后出现 jar 中该包的全部类（防止 JVM 模块系统冲突）。
            // Compose 的 ui-desktop 与 ui-graphics/ui-text/ui-util-desktop 共享 androidx.compose.ui.* 包，
            // 导致运行时报 ClassNotFoundException（如 androidx.compose.ui.graphics.SkiaGraphicsContext）。
            // mergeModules 把这些冲突 jar 合并为同一模块，从根本上消除 split package。
            //
            // skiko 组：每个 jar 在 ModLauncher 中是独立的命名模块，而 Class.getResourceAsStream
            // （skiko 的 Library.findAndLoad 用它查找 /skiko-windows-x64.dll.sha256 与 DLL）
            // 在命名模块中只搜索本模块自己的 jar。Library 类在 skiko-awt.jar，DLL 却在
            // skiko-awt-runtime-windows-x64.jar，导致资源查找返回 null、native 库加载失败，
            // 最终抛 UnsatisfiedLinkError: Paint_nMake。合并二者为同一模块即可修复。
            systemProperty(
                "mergeModules",
                "ui-desktop-1.7.3.jar,ui-graphics-desktop-1.7.3.jar,ui-text-desktop-1.7.3.jar,ui-util-desktop-1.7.3.jar;" +
                    "lifecycle-common-jvm-2.8.5.jar,lifecycle-runtime-desktop-2.8.5.jar,lifecycle-viewmodel-desktop-2.8.5.jar;" +
                    "skiko-awt-0.8.18.jar,skiko-awt-runtime-windows-x64-0.8.18.jar",
            )
        }
    }

    mods {
        register("manosaba") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// ModDevGradle 会注册项目级仓库（NeoForged/Mojang），使 settings.gradle.kts 中的仓库声明失效，
// 因此依赖仓库必须在此声明（Compose 的 androidx 传递依赖需要 google()）。
repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    mavenCentral()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(compose.foundation)
    implementation(compose.ui)

    // MC 1.21.8 及更早版本中，非 mod 的普通 Java 库（kotlin-stdlib、compose、skiko 等）
    // 不会被自动加载进 dev 运行的类路径，必须显式加入 additionalRuntimeClasspath，
    // 否则 runClient 会在启动时报 ClassNotFoundException: kotlin.jvm.internal.Intrinsics。
    add("additionalRuntimeClasspath", compose.desktop.currentOs)
    add("additionalRuntimeClasspath", compose.foundation)
    add("additionalRuntimeClasspath", compose.ui)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", libs.versions.minecraft.get())
    inputs.property("neoforge_version", libs.versions.neoforge.get())
    filteringCharset = "UTF-8"

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "neoforge_version" to libs.versions.neoforge.get()
        )
    }
}

val targetJavaVersion = 21

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    withSourcesJar()
}

tasks.jar {
    // 瘦 jar（仅模组自身类与资源，不含 Compose/Skiko/Kotlin 依赖）保留为 -thin 后缀，
    // 供开发调试与依赖排查使用；实际发布安装请使用下方的 shadowJar 完整版。
    archiveClassifier.set("thin")
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// 发布用完整版 jar：把 Compose / Skiko 运行时原地展开合并进模组 jar
// （kotlin / kotlinx 不展开，交由 KotlinForForge 提供，原因见筛选逻辑注释）。
//
// 背景：compose.desktop / skiko 是普通 Java 库，NeoForge 不会自动加载它们
// （上面 additionalRuntimeClasspath 只作用于 dev 运行类路径），所以导出的 jar 必须自带
// 这些类，否则游戏内渲染标题屏时抛 NoClassDefFoundError（androidx/compose/runtime/SnapshotStateKt）。
//
// 采用展开合并而非 jar-in-jar，同时解决两个已知问题：
//  1. split package：ui-desktop / ui-graphics-desktop 等 jar 共享 androidx.compose.ui.* 包，
//     展开后类在单一命名空间，等价于 dev 环境 mergeModules 的效果；
//  2. skiko native 加载：skiko 的 Library.findAndLoad 依赖 Class.getResourceAsStream 查找 DLL 资源，
//     要求 Library 类与 DLL 资源位于同一 jar 内，展开后满足。
//
// 精确筛选 Compose / Skiko 构件：
// ModDevGradle 会把 Minecraft + NeoForge 的合并产物（全部游戏类、assets、data、jarjar 等）
// 以“无模块坐标的文件依赖”形式放进 runtimeClasspath，Shadow 的 dependencies { include(...) }
// 模块过滤器对它无效，会把整个 Minecraft 打进 fat jar。因此改为在解析后的依赖图里按模块
// 坐标筛选，只注入 androidx.* / org.jetbrains(.*) 组的构件文件；模组自身类与资源由
// ShadowJar 默认的 sourceSets.main.output 提供。
//
// 剔除 kotlin / kotlinx：由运行环境的 KotlinForForge（KFF）提供。若展开进本 jar，manosaba
// 模块将导出 kotlin.* 包，与 KFF 的 kotlin.stdlib 模块产生包导出冲突——任何同时引用两者的
// Kotlin mod（整合包中的 maidsoulkitchen 等）都会触发 java.lang.module.ResolutionException
// （Modules manosaba and kotlin.stdlib export package kotlin.jvm ...），游戏无法启动。
// KFF 5.12 已提供 kotlin-stdlib(-jdk7/-jdk8)、kotlin-reflect、kotlinx-coroutines(-core-jvm/-jdk8)、
// kotlinx-serialization，覆盖 Compose / Skiko 引用的 kotlinx/coroutines/*（flow、channels 等
// 全部落在 coroutines-core 内）。
//
// 其余 kotlinx 包均无需保留（经 javap 反编译核实）：
//  - kotlinx/atomicfu：Compose 编译时已由 atomicfu 编译插件完成转换，运行时字节码全部使用
//    java.util.concurrent.atomic（AtomicInt/AtomicLong/AtomicReference/GlobalSnapshotManager 等
//    13 个引用类均为 FieldUpdater + Object 锁实现），@Metadata 中的 kotlinx/atomicfu 字符串
//    仅为编译期元数据，运行时不会解析；
//  - kotlinx/collections/immutable：Compose runtime 自带 shade 副本
//    androidx/compose/runtime/external/kotlinx/collections/immutable/（117 条目），无需外部库。
val composeRuntimeFiles: Provider<List<File>> = configurations.named("runtimeClasspath").flatMap { cfg ->
    cfg.incoming.artifacts.resolvedArtifacts.map { resolved ->
        resolved
            .filter { artifact ->
                val id = artifact.id.componentIdentifier
                if (id !is ModuleComponentIdentifier) return@filter false
                val group = id.group
                val providedByKff = group == "org.jetbrains.kotlin" || group.startsWith("org.jetbrains.kotlin.") ||
                    group == "org.jetbrains.kotlinx" || group.startsWith("org.jetbrains.kotlinx.")
                !providedByKff &&
                    (group.startsWith("androidx.") || group == "org.jetbrains" || group.startsWith("org.jetbrains."))
            }
            .map { it.file }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    // 完整版直接占用无后缀主文件名（Manosaba-<版本>.jar）。
    archiveClassifier.set("")

    // 覆盖 Shadow 默认的 runtimeClasspath 收集（其中夹带 MDG 合并游戏 jar），改用手动筛选结果。
    configurations = emptyList()
    from(composeRuntimeFiles)

    // 多个依赖 jar 各自携带 META-INF/services（kotlinx-coroutines 等），必须按文件合并而非覆盖。
    mergeServiceFiles()

    // 展开后的类在单一模块内加载：签名文件与模块描述符会干扰 ModLauncher 的模块系统。
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class", "META-INF/versions/**/module-info.class")

    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
