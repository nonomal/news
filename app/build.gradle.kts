plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

android {
    namespace = "org.vestifeed"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.vestifeed"
        minSdk = 34
        targetSdk = 37
        versionCode = 24
        versionName = "0.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            signingConfig = signingConfigs.getByName("debug")

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        disable += "GradleDependency"
    }
}

dependencies {
    // Coroutines
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.coroutines.test)

    // AndroidX
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    debugImplementation(libs.androidx.fragment.testing.manifest)
    androidTestImplementation(libs.androidx.fragment.testing)
    implementation(libs.androidx.work)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)

    // Networking
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    testImplementation(libs.okhttp.mockwebserver)

    // JSON
    implementation(libs.gson)

    // UI
    implementation(libs.material)
    implementation(libs.coil)
    implementation(libs.coil.network)

    // Parsing
    implementation(libs.jsoup)
    implementation(libs.re2j)

    // Unit tests
    testImplementation(libs.junit)

    // Instrumented tests
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
