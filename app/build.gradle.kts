plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.alaa.universe"
 compileSdk = 36
 defaultConfig { applicationId = "com.alaa.universe"; minSdk = 23; targetSdk = 36; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "1500m"; it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.15.1") }
