pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // CI 日志证据：同一份仓库列表里 AGP / Kotlin / CMP / compose-compiler / serialization 的
    // plugin marker 都能解析，唯独 com.google.devtools.ksp 的 marker 工件在 runner 上取不到。
    // KSP 的库工件（symbol-processing-gradle-plugin）在各镜像都正常提供，故直接按坐标引用，
    // 绕过 marker 工件查找。
    // v3.2.0 同型故障：com.google.gms.google-services 4.4.2 的 marker 在 runner 上同样取不到
    //（Android Build 44420426028 日志），库工件 com.google.gms:google-services 镜像正常提供，
    // 按同一先例绕过 marker。
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
            if (requested.id.id == "com.google.gms.google-services") {
                useModule("com.google.gms:google-services:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "HStudy"
include(":composeApp")