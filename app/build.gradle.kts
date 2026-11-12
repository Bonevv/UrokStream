plugins { id("com.android.application") }

android {
    namespace = "bg.urokstream"
    compileSdk = 35
    defaultConfig {
        applicationId = "bg.urokstream"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.gradleProperty("buildNumber").orElse("1").get().toInt()
        versionName = "0.1.${providers.gradleProperty("buildNumber").orElse("1").get()}"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES") }
    testOptions {
        unitTests.all { it.systemProperty("catalog.path", file("src/main/assets/lessons.torrent").absolutePath) }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-38")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.libtorrent4j:libtorrent4j-linux:2.1.0-38")
}
