import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val repositoryVersion = providers.gradleProperty("appVersionName").orElse("0.1.0").get()
val versionParts = repositoryVersion.substringBefore('-').split('.').map(String::toInt)
require(versionParts.size == 3) {
    "appVersionName must use MAJOR.MINOR.PATCH, got: $repositoryVersion"
}
val appVersionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2]

// Release signing key lives outside git at keystore/calino-release.jks so it never
// gets silently regenerated (that's what desynced the phone's installed signature
// from a freshly generated ~/.android/debug.keystore before). Missing the
// properties file is a hard error rather than falling back to an ad hoc key.
val releaseKeystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore/release.keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

android {
    namespace = "calino.malinov.ski"
    compileSdk = 36

    defaultConfig {
        applicationId = "calino.malinov.ski"
        minSdk = 26
        targetSdk = 36
        // Keep the native APK on the same monotonically increasing version-code
        // line as the web/Capacitor Android app.
        versionCode = appVersionCode
        versionName = repositoryVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    signingConfigs {
        if (releaseKeystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file("keystore/${releaseKeystoreProperties["storeFile"]}")
                storePassword = releaseKeystoreProperties["storePassword"] as String
                keyAlias = releaseKeystoreProperties["keyAlias"] as String
                keyPassword = releaseKeystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Keep the signed debug app installable beside the production
            // application instead of having one replace the other.
            applicationIdSuffix = ".nativeDebug"
            versionNameSuffix = "-debug"
            // The account type must differ too. Debug and release can be
            // installed at once, and two authenticators claiming one type
            // would leave the calendar projection unable to say which app
            // owns an account -- which is the boundary that keeps it off the
            // person's Google and Exchange rows.
            resValue("string", "calino_account_type", "calino.malinov.ski.nativeDebug")
        }
        getByName("release") {
            resValue("string", "calino_account_type", "calino.malinov.ski")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

// Instrumented tests rewrite app state and must never fan out to a person's
// attached phone. AGP's connected task targets every visible device when no
// serial is pinned, so make the safe target an enforced precondition rather
// than relying on whoever runs Gradle to remember the environment variable.
tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        doFirst {
            val serial = System.getenv("ANDROID_SERIAL")
            check(serial?.startsWith("emulator-") == true) {
                "Refusing to run $path: set ANDROID_SERIAL to an emulator serial (for example emulator-5554). " +
                    "Connected tests are forbidden on physical devices."
            }
        }
    }
}

dependencies {
    implementation(project(":wear-contract"))
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
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
    implementation("androidx.work:work-runtime-ktx:2.11.2")
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
    // The Compose device-test harness. The BOM pins ui-test-junit4 to the same
    // Compose version the app compiles against; the test manifest is what
    // provides the empty activity ComposeTestRule needs, and is debug-only.
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    // Not for view assertions -- Compose owns those. This is for pressBack()
    // (the calendar's zoom-collapse back handler) and closeSoftKeyboard().
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // A real org.json on the JVM: the android.jar stub throws, which would make
    // the account-persistence round trip untestable.
    testImplementation("org.json:json:20240303")
}
