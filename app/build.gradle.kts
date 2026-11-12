plugins { id("com.android.application") }

android {
    namespace = "bg.urokstream"
    compileSdk = 35
    defaultConfig {
        applicationId = "bg.urokstream"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.gradleProperty("buildNumber").orElse("1").get().toInt()
        versionName = "0.3.${providers.gradleProperty("buildNumber").orElse("1").get()}"
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Keep Robolectric's download lock and caches inside the project build directory.
            it.systemProperty("user.home", rootProject.layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
            it.systemProperty("catalog.path", file("src/main/assets/lessons.torrent").absolutePath)
            it.systemProperty("outline.path", file("src/main/assets/site-outline.tsv").absolutePath)
            it.systemProperty("maven.repo.local", rootProject.layout.buildDirectory.dir("robolectric-cache").get().asFile.absolutePath)
            it.systemProperty("ui.screenshots", layout.buildDirectory.dir("reports/ui").get().asFile.absolutePath)
            it.doFirst {
                rootProject.layout.buildDirectory.dir("test-home").get().asFile.mkdirs()
                rootProject.layout.buildDirectory.dir("robolectric-cache").get().asFile.mkdirs()
            }
        }
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testRuntimeOnly("org.libtorrent4j:libtorrent4j-linux:2.1.0-38")
}
