plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val launcherAssetDir = layout.buildDirectory.dir("generated/launcherAsset")

val bundleLauncherApk = tasks.register<Copy>("bundleLauncherApk") {
    group = "build"
    description = "Кладёт release-сборку лаунчера в установщик"
    dependsOn(":app:assembleRelease")
    from(rootProject.layout.projectDirectory.file("app/build/outputs/apk/release/app-release.apk"))
    into(launcherAssetDir)
    rename { "launcher.apk" }
}

android {
    namespace = "com.launch.installer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.launch.installer"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(launcherAssetDir)

    androidResources {
        noCompress += "apk"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val compose = "1.10.3"
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

val writeLauncherVersion = tasks.register("writeLauncherVersion") {
    group = "build"
    description = "Записывает версию вложенного лаунчера"
    dependsOn(bundleLauncherApk)
    val apkFile = layout.buildDirectory.file("generated/launcherAsset/launcher.apk")
    val versionFile = layout.buildDirectory.file("generated/launcherAsset/launcher-version.txt")
    inputs.file(apkFile)
    outputs.file(versionFile)
    doLast {
        val exe = if (System.getProperty("os.name").contains("Windows", true)) "aapt.exe" else "aapt"
        val tools = android.sdkDirectory.resolve("build-tools").listFiles()
            ?.filter { it.resolve(exe).isFile }
            ?.maxByOrNull { it.name }
            ?: error("Не найден aapt в Android SDK")
        val proc = ProcessBuilder(tools.resolve(exe).absolutePath, "dump", "badging", apkFile.get().asFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        if (proc.waitFor() != 0) error(text)
        val version = Regex("versionName='([^']+)'").find(text)?.groupValues?.get(1)
            ?: error("В лаунчере нет versionName")
        val number = Regex("versionCode='([^']+)'").find(text)?.groupValues?.get(1) ?: "?"
        versionFile.get().asFile.writeText("$version\n$number\n")
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(bundleLauncherApk, writeLauncherVersion)
    }
}
