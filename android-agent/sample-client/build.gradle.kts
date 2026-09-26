plugins {
    id("com.android.application")
}

// Application exemple : montre comment une app du terminal (ex. paiement) se connecte à
// l'agent TMS en AIDL pour lire les infos du terminal et SES paramètres TMS.
android {
    namespace = "com.tms.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tms.sample"
        minSdk = 22
        targetSdk = 34
        versionCode = (project.findProperty("sampleVersionCode") as String?)?.toInt() ?: 1
        versionName = "1.0." + ((project.findProperty("sampleVersionCode") as String?) ?: "1")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }

    sourceSets {
        // Même contrat AIDL que l'agent (copie à faire dans une vraie application cliente)
        getByName("main").aidl.srcDirs("../app/src/main/aidl")
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
