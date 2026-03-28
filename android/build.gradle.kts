plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

group = "expo.modules.dnssd"
version = "0.1.0"

android {
  namespace = "expo.modules.dnssd"
  compileSdk = 34

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.25")
  implementation(project(":expo-modules-core"))
}
