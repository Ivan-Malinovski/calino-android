import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

val repositoryVersion = providers.gradleProperty("appVersionName").orElse("0.1.0").get()
val p = repositoryVersion.substringBefore('-').split('.').map(String::toInt)
val releaseKeystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore/release.keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use(::load)
}

android {
    namespace="calino.malinov.ski.wear"; compileSdk=36
    defaultConfig { applicationId="calino.malinov.ski"; minSdk=30; targetSdk=36; versionCode=2_000_000_000 + p[0]*1_000_000+p[1]*1_000+p[2]; versionName=repositoryVersion }
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
        getByName("debug") { applicationIdSuffix=".nativeDebug"; versionNameSuffix="-debug" }
        getByName("release") {
            if (releaseKeystoreProperties.containsKey("storeFile")) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose=true }
}

dependencies {
    implementation(project(":wear-contract"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.tiles:tiles-material:1.5.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.guava:guava:33.4.8-android")
    testImplementation("junit:junit:4.13.2")
}

tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        doFirst {
            val serial = System.getenv("ANDROID_SERIAL")
            check(serial?.startsWith("emulator-") == true) {
                "Refusing to run $path: set ANDROID_SERIAL to an emulator serial."
            }
        }
    }
}
