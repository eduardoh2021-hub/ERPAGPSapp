import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma de la versión de distribución: keystore.properties (fuera de git) con storeFile, storePassword, keyAlias y keyPassword.
// Sin ese fichero, assembleRelease queda sin firmar y solo vale assembleDebug.
val firma = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// URL del ERP que propone la pantalla de acceso: -PerpUrl=… o erpUrl= en local.properties (fuera de git: el repositorio es público)
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val erpUrl = providers.gradleProperty("erpUrl").orNull ?: local.getProperty("erpUrl") ?: ""
// Versión: la fija scripts/publicar-release (-PversionName=1.2.0); el código sale de ella (1.2.0 → 10200)
val nombreVersion = providers.gradleProperty("versionName").getOrElse("1.2.0")
val codigoVersion = nombreVersion.split('.').map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

android {
    namespace = "net.actiontruck.conductor"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.actiontruck.conductor"
        minSdk = 26
        targetSdk = 35
        versionCode = codigoVersion
        versionName = nombreVersion
        buildConfigField("String", "ERP_URL", "\"$erpUrl\"")
        // «Buscar actualizaciones»: releases de este repositorio en GitHub (público)
        buildConfigField("String", "REPO_ACTUALIZACIONES", "\"${providers.gradleProperty("repoActualizaciones").getOrElse("eduardoh2021-hub/ERPAGPSapp")}\"")
    }

    signingConfigs {
        if (firma.isNotEmpty()) create("release") {
            storeFile = file(firma.getProperty("storeFile"))
            storePassword = firma.getProperty("storePassword")
            keyAlias = firma.getProperty("keyAlias")
            keyPassword = firma.getProperty("keyPassword")
        }
    }

    buildTypes {
        // El APK de pruebas se instala junto a la app de los conductores, sin sustituirla (otro identificador)
        debug {
            applicationIdSuffix = ".pruebas"
            versionNameSuffix = "-pruebas"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (firma.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    splits {
        // Un APK por arquitectura más uno universal: MapLibre trae librerías nativas de cada una
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

// -----------------------------------------------------------------------------
//  Marca: los colores salen de marca/tokens.json, copia de packages/ui/tokens.json del ERP (nada de colores escritos a mano).
//  Se generan un objeto Kotlin (Marca) para Compose y un colors.xml para el icono y el tema de arranque.
// -----------------------------------------------------------------------------
abstract class GenerarMarca : DefaultTask() {
    @get:InputFile abstract val tokens: RegularFileProperty
    @get:Input abstract val formato: Property<String>
    @get:OutputDirectory abstract val salida: DirectoryProperty

    @TaskAction
    fun generar() {
        @Suppress("UNCHECKED_CAST")
        val raiz = groovy.json.JsonSlurper().parse(tokens.get().asFile) as Map<String, Any?>
        val planos = linkedMapOf<String, String>()
        fun recorrer(prefijo: String, m: Map<*, *>) {
            for ((k, v) in m) {
                if (v !is Map<*, *>) continue
                val valor = v["value"]
                if (valor is String) planos["$prefijo$k"] = valor else recorrer("$prefijo$k.", v)
            }
        }
        recorrer("", raiz["color"] as Map<*, *>)
        fun resolver(v: String): String =
            if (v.startsWith("{color.")) resolver(planos.getValue(v.removePrefix("{color.").removeSuffix("}"))) else v
        val colores = planos.mapValues { resolver(it.value).removePrefix("#").uppercase() }.filterValues { it.matches(Regex("[0-9A-F]{6}")) }
        val dir = salida.get().asFile
        dir.deleteRecursively()
        if (formato.get() == "kotlin") {
            val nombre = { k: String -> k.split('.').mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar(Char::uppercase) }.joinToString("") }
            val f = File(dir, "net/actiontruck/conductor/marca/Marca.kt")
            f.parentFile.mkdirs()
            f.writeText(buildString {
                appendLine("// GENERADO desde marca/tokens.json por la tarea generarMarcaKotlin. No editar.")
                appendLine("package net.actiontruck.conductor.marca\n")
                appendLine("import androidx.compose.ui.graphics.Color\n")
                appendLine("object Marca {")
                colores.forEach { (k, v) -> appendLine("    val ${nombre(k)} = Color(0xFF$v)") }
                appendLine("}")
            })
        } else {
            val f = File(dir, "values/colores_marca.xml")
            f.parentFile.mkdirs()
            f.writeText(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<!-- GENERADO desde marca/tokens.json por la tarea generarMarcaRes. No editar. -->")
                appendLine("<resources>")
                colores.forEach { (k, v) -> appendLine("    <color name=\"marca_${k.replace('.', '_')}\">#$v</color>") }
                appendLine("</resources>")
            })
        }
    }
}

val tokensMarca = rootProject.layout.projectDirectory.file("marca/tokens.json")
val generarMarcaKotlin = tasks.register<GenerarMarca>("generarMarcaKotlin") {
    tokens.set(tokensMarca); formato.set("kotlin"); salida.set(layout.buildDirectory.dir("generated/marca/kotlin"))
}
val generarMarcaRes = tasks.register<GenerarMarca>("generarMarcaRes") {
    tokens.set(tokensMarca); formato.set("res"); salida.set(layout.buildDirectory.dir("generated/marca/res"))
}
androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(generarMarcaKotlin, GenerarMarca::salida)
        variant.sources.res?.addGeneratedSourceDirectory(generarMarcaRes, GenerarMarca::salida)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Mapa de las paradas: MapLibre sobre teselas OSM, el mismo motor que la web (sin servicios de mapas de pago)
    implementation("org.maplibre.gl:android-sdk:11.5.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
