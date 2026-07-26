import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        // Las expect/actual class siguen marcadas como Beta pero son estables en la práctica.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Los atajos compose.* están deprecados a favor de declarar
            // androidx.compose.* directamente, pero esos artefactos no publican
            // targets de Apple y cambiarlos rompe la resolución de iOS
            // (probado). Hay que seguir con los de JetBrains hasta que AndroidX
            // los publique; los avisos de deprecación son el precio.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.code.scanner)
            implementation(libs.play.services.base)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        // Tests de integración contra SQLite real ejecutados en la JVM.
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.koin.test)
            // Los tests de interfaz corren en la JVM con Robolectric, porque en
            // este equipo no hay emulador ni dispositivo conectado.
            implementation(libs.robolectric)
            implementation(libs.junit)
            implementation(libs.compose.ui.test.junit4)
        }
    }
}

compose.resources {
    packageOfResClass = "com.pelayo.gimnasio.resources"
}

sqldelight {
    databases {
        create("GimnasioDb") {
            packageName.set("com.pelayo.gimnasio.db")
        }
    }
}

android {
    namespace = "com.pelayo.gimnasio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pelayo.gimnasio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            // Robolectric necesita los recursos de Android para inflar la UI.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Aporta la ComponentActivity vacía que los tests de Compose necesitan.
    // Tiene que ir en la configuración debug para que su manifiesto se fusione
    // con el de la app; en el sourceSet de tests no serviría.
    debugImplementation(libs.compose.ui.test.manifest)
}
