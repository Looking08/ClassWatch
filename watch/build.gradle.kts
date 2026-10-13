plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.classwatch.watch"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.classwatch.watch"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // OPPO Watch X3 是圆形屏幕
        resourceConfigurations += setOf("zh", "en")
    }

    buildTypes {
        debug {
            // 默认
        }
        release {
            // R8 混淆 + 压缩：这设备是 32 位 CPU，debuggable 的调试挂钩
            // 和未优化的 dex 会让 Compose 每帧 150ms+，正式包能快一大截
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 方便侧载测试：直接用 debug keystore 签 release 包
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---------- 共用数据模型 ----------
    implementation(project(":shared"))

    // ---------- Compose ----------
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)

    // ---------- Wear Compose（UI 组件库，纯 Jetpack，不依赖 Wear OS 框架）----------
    // OPPO Watch X3 国行是 ColorOS Watch（Android 11），机器上没有
    // com.google.android.wearable 共享库；Wear Compose 只依赖 androidx.*，所以能用。
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material)

    // ---------- 已移除：Tile 小组件 ----------
    // androidx.wear.tiles 需要 Wear OS 的 Tile renderer，ColorOS Watch 上没有，
    // 注册了会让 adb install 直接失败（INSTALL_FAILED_MISSING_SHARED_LIBRARY）。
    // 源码备份在 android/legacy-wearos-tile/ScheduleTileService.kt
    // implementation(libs.wear.tiles)
    // implementation(libs.wear.tiles.material)

    debugImplementation(libs.compose.ui.tooling)
}
