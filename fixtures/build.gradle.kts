plugins { id("com.android.application") }
android {
    namespace = "tv.nelo.fixture"
    compileSdk = 35
    defaultConfig { applicationId = "tv.nelo.fixture"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
