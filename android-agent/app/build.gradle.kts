plugins {
    id("com.android.application")
}

import java.util.Properties

val tmsServerUrl: String = (project.findProperty("tmsServerUrl") as String?) ?: "http://10.0.2.2:8095"
val tmsEnrollmentKey: String = (project.findProperty("tmsEnrollmentKey") as String?) ?: ""

// Version de l'agent : -PagentVersion=1.2.3 -> versionCode 10203x, x = variante (0 universal, 1 newland,
// 2 pax, 3 sunmi) : toujours croissant, et unique par variante dans le dépôt du TMS.
// Publier les APK dans le dépôt du TMS suffit : les terminaux se mettent à jour automatiquement.
val agentVersion: String = (project.findProperty("agentVersion") as String?) ?: "1.0.4"
val agentVersionCode: Int = agentVersion.split(".").let { p ->
    require(p.size == 3 && p.all { it.toIntOrNull() in 0..99 }) { "agentVersion attendu au format X.Y.Z (0-99) : $agentVersion" }
    p[0].toInt() * 10000 + p[1].toInt() * 100 + p[2].toInt()
}

// Signature S2M : lue depuis android-agent/keystore.properties (local, jamais versionné).
// Modèle : keystore.properties.example. Sans ce fichier, la clé debug Android est utilisée.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.tms.agent"
    compileSdk = 34

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("s2m") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1 + v2 : comme les APK S2M passés ensuite dans Certificate Management (Newland)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.tms.agent"
        // Android 5.1 : couvre les parcs PAX A920 / Newland N910 / Sunmi P1 les plus anciens
        minSdk = 22
        targetSdk = 34
        versionCode = agentVersionCode
        versionName = agentVersion

        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$tmsServerUrl\"")
        buildConfigField("String", "DEFAULT_ENROLLMENT_KEY", "\"$tmsEnrollmentKey\"")
    }

    // Une variante par SDK constructeur : chaque marque exige de toute façon son propre APK signé.
    //  - universal : aucun SDK propriétaire (API Android standard), pour tout terminal
    //  - newland   : MESDK Newland (reboot, n° de série / firmware officiels)
    //  - pax       : NeptuneLite DAL PAX (install / désinstall silencieux, reboot, infos terminal)
    //  - sunmi     : PayLib Sunmi (reboot, n° de série / firmware officiels)
    flavorDimensions += "brand"
    productFlavors {
        create("universal") {
            dimension = "brand"
            versionCode = agentVersionCode * 10
        }
        create("newland") {
            dimension = "brand"
            versionCode = agentVersionCode * 10 + 1
            versionNameSuffix = "-newland"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
        create("pax") {
            dimension = "brand"
            versionCode = agentVersionCode * 10 + 2
            versionNameSuffix = "-pax"
        }
        create("sunmi") {
            dimension = "brand"
            versionCode = agentVersionCode * 10 + 3
            versionNameSuffix = "-sunmi"
        }
    }

    // Chaque variante embarque l'implémentation SDK de sa marque, et la version
    // "sans SDK" (src/stubs/<marque>) des autres constructeurs.
    sourceSets {
        getByName("universal").java.srcDirs("src/stubs/newland/java", "src/stubs/pax/java", "src/stubs/sunmi/java")
        getByName("newland").java.srcDirs("src/stubs/pax/java", "src/stubs/sunmi/java")
        getByName("pax").java.srcDirs("src/stubs/newland/java", "src/stubs/sunmi/java")
        getByName("sunmi").java.srcDirs("src/stubs/newland/java", "src/stubs/pax/java")
    }

    buildTypes {
        debug {
            manifestPlaceholders["cleartext"] = "true"
            signingConfigs.findByName("s2m")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("s2m")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // HTTPS obligatoire en production
            manifestPlaceholders["cleartext"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    // Aligne kotlin-stdlib / -jdk7 / -jdk8 tirés transitivement (sinon "Duplicate class kotlin.*")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // SDK propriétaire Newland (fourni par Newland, non publié sur Maven)
    "newlandImplementation"(files("libs/newland/MESDK-3.10.81-RELEASE.aar"))
    // SDK propriétaire PAX (NeptuneLite DAL, Java pur)
    "paxImplementation"(files("libs/pax/NeptuneLiteApi_V3.27.00_20211103.jar"))
    // SDK Sunmi (PayLib : liaison au service système com.sunmi.pay.hardware_v3)
    "sunmiImplementation"(files("libs/sunmi/PayLib-release-2.0.17.aar"))

    testImplementation("junit:junit:4.13.2")
}
