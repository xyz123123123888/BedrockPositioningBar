// ============================================================
// BedrockPositioningBar - 构建脚本
// 目标: Minecraft 26.2 (未混淆版本), Fabric Loader 0.19.3
// 环境: Java 25, Gradle 9.x
//
// 重要说明：
// - Minecraft 26.1+ 是「未混淆版本」，必须使用插件 ID
//   net.fabricmc.fabric-loom（而非 fabric-loom-remap）。
// - 未混淆版本无需、也不能配置 mappings。
// ============================================================

plugins {
    // 未混淆版本专用插件 ID（Minecraft 26.1 及以后）
    id("net.fabricmc.fabric-loom") version "1.17.+"
}

// 从 gradle.properties 读取版本号（属性名为下划线风格）
val minecraftVersion: String = providers.gradleProperty("minecraft_version").get()
val fabricLoaderVersion: String = providers.gradleProperty("fabric_loader_version").get()
val fabricApiVersion: String = providers.gradleProperty("fabric_api_version").get()
val modVersion: String = providers.gradleProperty("mod_version").get()
val mavenGroup: String = providers.gradleProperty("maven_group").get()
val archivesBaseName: String = providers.gradleProperty("archives_base_name").get()

version = modVersion
group = mavenGroup
base { archivesName.set(archivesBaseName) }

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

dependencies {
    // 游戏本体
    minecraft("com.mojang:minecraft:$minecraftVersion")

    // 未混淆版本（26.1+）不需要 mappings

    // Fabric Loader
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Fabric API（26.2 对应稳定版）
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "minecraft_version" to minecraftVersion,
            "fabric_loader_version" to fabricLoaderVersion,
            "fabric_api_version" to fabricApiVersion
        )
    }
}

tasks.jar {
    from("LICENSE") { rename { "LICENSE_BedrockPositioningBar" } }
}
