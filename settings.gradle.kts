pluginManagement {
    repositories {
        // Fabric 专用仓库：提供 fabric-loom 插件
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// 项目根目录名称（与模组名保持一致）
rootProject.name = "BedrockPositioningBar"
