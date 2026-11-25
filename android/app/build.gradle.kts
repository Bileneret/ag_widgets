import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// Read local.properties through the provider API so configuration cache is
// invalidated when the embedded ntfy topic changes.
val localProperties: Provider<Properties> = providers.fileContents(
  rootProject.layout.projectDirectory.file("local.properties"),
).asText.map { text ->
  Properties().apply { load(text.reader()) }
}

val isCommunityBuild = providers.gradleProperty("community")
  .map { it.equals("true", ignoreCase = true) }
  .getOrElse(false)

val agwTopicPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

val agwTopic: String = run {
  if (isCommunityBuild) return@run ""
  val raw = localProperties.orNull?.getProperty("agw.topic")?.trim().orEmpty()
  when {
    raw.isEmpty() -> ""
    !raw.matches(agwTopicPattern) -> {
      logger.warn("WARNING: ignoring agw.topic from local.properties (expected [A-Za-z0-9_-]{1,64})")
      ""
    }
    else -> raw
  }
}

// Defense in depth: the regex above already forbids quotes and backslashes.
val agwTopicLiteral = agwTopic.replace("\\", "\\\\").replace("\"", "\\\"")

if (agwTopic.isNotEmpty()) {
  logger.lifecycle("WARNING: personal agw.topic embedded; pass -Pcommunity=true for public builds")
}

android {
  namespace = "dev.agwidgets"
  compileSdk = 37
  defaultConfig {
    applicationId = "dev.agwidgets"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    buildConfigField("String", "AGW_TOPIC", "\"$agwTopicLiteral\"")
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
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local JVM tests
  testImplementation(libs.junit)
  testImplementation(libs.org.json)

  // Glance widget & WorkManager
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.work.runtime.ktx)
}
