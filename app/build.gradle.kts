plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.alaa.universe"
 compileSdk = 36
 defaultConfig { applicationId = "com.alaa.universe"; minSdk = 23; targetSdk = 36; versionCode = 12; versionName = "0.4.8" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "512m"; it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
}
dependencies {
 implementation("com.github.bumptech.glide:glide:4.16.0")
 implementation("androidx.media3:media3-exoplayer:1.9.2")
 implementation("androidx.media3:media3-exoplayer-hls:1.9.2")
 implementation("androidx.media3:media3-exoplayer-dash:1.9.2")
 implementation("androidx.media3:media3-ui:1.9.2")
 testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.15.1") }
