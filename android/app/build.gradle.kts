import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
  val f = rootProject.file("local.properties")
  if (f.exists()) {
    f.inputStream().use { load(it) }
  }
}
val isCommunityBuild = providers.gradleProperty("community").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
val agwTopic: String = if (isCommunityBuild) "" else (localProperties.getProperty("agw.topic") ?: "").trim()

// Release credentials live outside the repository. Override the path with
// AGW_KEYSTORE_PROPERTIES (useful for CI); by default use the same runtime
// directory as the collector.
val keystoreProperties = Properties().apply {
  val externalPath = System.getenv("AGW_KEYSTORE_PROPERTIES")
    ?: System.getenv("LOCALAPPDATA")?.let { "$it/ag-widgets/keys/keystore.properties" }
  if (!externalPath.isNullOrBlank()) {
    val externalFile = file(externalPath)
    if (externalFile.exists()) {
      externalFile.inputStream().use { load(it) }
    }
  }
}
val releaseStoreFile = keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val hasReleaseSigning = releaseStoreFile != null

android {
  namespace = "dev.agwidgets"
  compileSdk = 37
  defaultConfig {
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0.0"
    buildConfigField("String", "AGW_TOPIC", "\"$agwTopic\"")
  }

  flavorDimensions += "product"
  productFlavors {
    create("full") {
      dimension = "product"
      applicationId = "dev.agwidgets"
      buildConfigField("boolean", "ZCODE_ENABLED", "true")
    }
    create("context") {
      dimension = "product"
      applicationId = "dev.agwidgets.context"
      buildConfigField("boolean", "ZCODE_ENABLED", "false")
    }
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = releaseStoreFile?.let { file(it) }
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)

  // Compose UI
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local JVM tests
  testImplementation(libs.junit)
  testImplementation(libs.org.json)

  // Glance widget & WorkManager
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.androidx.work.runtime.ktx)
}
