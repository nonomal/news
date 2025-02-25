import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.squareup.sqldelight")
    id("com.google.devtools.ksp") version "1.6.21-1.0.5"
}

val signingPropertiesFile = rootProject.file("signing.properties")

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 26
        targetSdk = 31
        versionCode = 20
        versionName = "0.3.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", "news-$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    signingConfigs {
        if (signingPropertiesFile.exists()) {
            create("release") {
                val signingProperties = Properties()
                signingProperties.load(FileInputStream(signingPropertiesFile))
                storeFile = File(signingProperties["playKeystoreFile"] as String)
                storePassword = signingProperties["playKeystorePassword"] as String
                keyAlias = signingProperties["playKeyAlias"] as String
                keyPassword = signingProperties["playKeyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        if (signingPropertiesFile.exists()) {
            release {
                val signingProperties = Properties()
                signingProperties.load(FileInputStream(signingPropertiesFile))
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    sourceSets {
        getByName("debug") {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
        }
    }
}

sqldelight {
    database("Database") {
        sourceFolders = listOf("sqldelight")
        packageName = "db"
        deriveSchemaFromMigrations = true
    }
}

dependencies {
    // Kotlin extensions
    // Simplifies non-blocking programming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1")

    // Android extensions
    implementation("androidx.core:core-ktx:1.7.0")
    val fragmentVer = "1.4.1"
    implementation("androidx.fragment:fragment-ktx:$fragmentVer")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    val navVer = "2.4.2"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVer")
    implementation("androidx.navigation:navigation-ui-ktx:$navVer")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.browser:browser:1.4.0")
    val workVer = "2.7.1"
    implementation("androidx.work:work-runtime-ktx:$workVer")

    // Feed parser
    // Used in standalone mode
    implementation("co.appreactor:feedk:0.2.3")

    // Retrofit turns HTTP APIs into Java interfaces
    // Used to communicate with remote backends
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Modern HTTP client
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.9.3"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    testImplementation("com.squareup.okhttp3:mockwebserver")

    // SQLDelight generates typesafe kotlin APIs from SQL statements
    val sqlDelightVer = "1.5.3"
    implementation("com.squareup.sqldelight:coroutines-extensions:$sqlDelightVer")
    implementation("com.squareup.sqldelight:android-driver:$sqlDelightVer")

    implementation("com.google.android.material:material:1.6.0")
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("io.insert-koin:koin-android:3.2.0")
    implementation("io.insert-koin:koin-annotations:1.0.0-beta-2")
    ksp("io.insert-koin:koin-ksp-compiler:1.0.0-beta-2")
    implementation("org.jsoup:jsoup:1.14.3")

    // Common testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.12.3")

    debugImplementation("androidx.fragment:fragment-testing:$fragmentVer")
    testImplementation("com.squareup.sqldelight:sqlite-driver:$sqlDelightVer")

    // Core instrumented testing dependencies
    androidTestImplementation(kotlin("test"))
    androidTestImplementation("androidx.test:core:1.4.0")

    // AndroidJUnitRunner and JUnit Rules
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")

    // Assertions
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.ext:truth:1.4.0")
    androidTestImplementation("com.google.truth:truth:1.1.3")

    // Espresso dependencies
    val espressoVer = "3.4.0"
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVer")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espressoVer")
    androidTestImplementation("androidx.test.espresso:espresso-intents:$espressoVer")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:$espressoVer")
    androidTestImplementation("androidx.test.espresso:espresso-web:$espressoVer")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:$espressoVer")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:$espressoVer")

    androidTestImplementation("androidx.work:work-testing:$workVer")
}
