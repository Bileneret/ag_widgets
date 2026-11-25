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

android {
  namespace = "dev.agwidgets"
  compileSdk = 37
  defaultConfig {
    applicationId = "dev.agwidgets"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    buildConfigField("String", "AGW_TOPIC", "\"$agwTopic\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
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
