plugins {
    id("com.android.application")
}

val tmsServerUrl: String = (project.findProperty("tmsServerUrl") as String?) ?: "http://10.0.2.2:8095"
val tmsEnrollmentKey: String = (project.findProperty("tmsEnrollmentKey") as String?) ?: ""

android {
    namespace = "com.tms.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tms.agent"
        // Android 5.1 : couvre les parcs PAX A920 / Newland N910 / Sunmi P1 les plus anciens
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

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
        }
        create("newland") {
            dimension = "brand"
            versionNameSuffix = "-newland"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
        create("pax") {
            dimension = "brand"
            versionNameSuffix = "-pax"
        }
        create("sunmi") {
            dimension = "brand"
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
        }
        release {
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
