plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.alaa.universe"
 compileSdk = 36
 defaultConfig { applicationId = "com.alaa.universe"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; minSdk = 23; targetSdk = 36; versionCode = 15; versionName = "0.4.11" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "512m"; it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
}
dependencies {
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 implementation("com.github.bumptech.glide:glide:4.16.0")
 implementation("androidx.media3:media3-exoplayer:1.9.2")
 implementation("androidx.media3:media3-exoplayer-hls:1.9.2")
 implementation("androidx.media3:media3-exoplayer-dash:1.9.2")
 implementation("androidx.media3:media3-ui:1.9.2")
 testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.15.1") }
