import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasPlatformKeystore = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }
}

android {
    namespace = "com.kyuusanq3.aabooter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kyuusanq3.aabooter"
        minSdk = 26
        targetSdk = 33
        versionCode = 4
        versionName = "1.0.3"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasPlatformKeystore) {
            create("platform") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasPlatformKeystore) {
                signingConfig = signingConfigs.getByName("platform")
            }
        }
        debug {
            // Debug must never be used on-device for this app: DeveloperHeadUnitNetworkService
            // requires uid 1000 via the AOSP platform test certificate.
            if (hasPlatformKeystore) {
                signingConfig = signingConfigs.getByName("platform")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // No third-party libraries required.
}

tasks.register("requirePlatformKeystore") {
    doLast {
        if (!hasPlatformKeystore) {
            throw GradleException(
                "Platform keystore not configured. Place the AOSP platform test keystore on this " +
                    "machine, verify SHA-256 c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8, " +
                    "then create keystore.properties (gitignored) pointing at it. Do not use the debug keystore."
            )
        }
        val store = rootProject.file(keystoreProperties["storeFile"] as String)
        if (!store.isFile) {
            throw GradleException("keystore.properties storeFile does not exist: ${store.absolutePath}")
        }
    }
}

tasks.matching { it.name.matches(Regex("assemble(Release|Debug)|bundle(Release|Debug)")) }.configureEach {
    dependsOn("requirePlatformKeystore")
}
