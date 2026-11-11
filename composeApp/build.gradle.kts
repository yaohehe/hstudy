import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // iOS 26 SDK 上部分 API（UNMutableNotificationContent 的 readwrite 属性、
        // UIModalPresentationStyle 常量）无法从 Kotlin 侧调用，改由 ObjC inline 桥接完成。
        iosTarget.compilations.getByName("main").cinterops.create("hstudyIosBridge") {
            defFile(project.file("src/iosMain/nativeInterop/cinterop/hstudyIosBridge.def"))
            packageName("com.hstudy.app.iosbridge")
        }
    }

    sourceSets {
        // 与原 app/build.gradle.kts 的 -opt-in 保持一致（Compose 实验性 API）
        all {
            languageSettings {
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.coil.network.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // runTest：LanguagePreferenceTest 需要真实文件系统 + 协程调度器跑 DataStore 往返
            // （spec §8.5 / Task 5 Ruling 7）。计划 Step 5 预留的加依赖分支，实测首次运行
            // 确实因 kotlinx.coroutines.test 不在 classpath 上编译失败。
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.hstudy.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hstudy.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 30100
        versionName = "3.1.0"
    }
    // 发布签名：keystore 与口令只经环境变量注入（CI secrets / 本地导出），绝不入库。
    // 未设置时 release 变体照旧产出未签名 APK，无密钥环境仍可构建。
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() && !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // setProperty 是项目级属性，release 变体同样生效；跟随 versionName 避免版本漂移
            setProperty("archivesBaseName", "HStudy-v${defaultConfig.versionName}")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}