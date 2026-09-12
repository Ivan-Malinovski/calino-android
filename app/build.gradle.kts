import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val repositoryVersion = providers.gradleProperty("appVersionName").orElse("0.1.0").get()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

android {
    namespace = "calino.malinov.ski.poc"
    compileSdk = 36

    defaultConfig {
        applicationId = "calino.malinov.ski.poc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = repositoryVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    // NotificationCompat, the channel helpers and the permission check. Already
    // on the classpath transitively; declared because this app now uses it
    // directly and a transitive version is not a contract.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Fold posture only. The adaptive layout rules stay ours; this reports the
    // hinge, which BoxWithConstraints cannot see.
    implementation("androidx.window:window:1.5.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.sf.biweekly:biweekly:0.6.8")
    // CommonMark plus the GFM extensions used by the web app through
    // react-markdown + remark-gfm. These artifacts are JVM-only, have no
    // Android-incompatible runtime dependencies, and commonmark-java provides
    // an Android test target for its parser.
    implementation("org.commonmark:commonmark:0.29.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.29.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.29.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.29.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.29.0")
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1") {
        exclude(group = "org.freemarker", module = "freemarker")
        exclude(group = "org.jsoup", module = "jsoup")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
    }
    // The home screen widget. Glance is not part of the Compose BOM and has to
    // carry its own version. glance-material3 is deliberately not taken: the
    // widget maps the existing CalinoPalette tokens directly, rather than
    // routing them through a second Material colour scheme.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":benchmark"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // A real org.json on the JVM: the android.jar stub throws, which would make
    // the account-persistence round trip untestable.
    testImplementation("org.json:json:20240303")
}
