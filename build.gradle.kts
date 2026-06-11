plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

val fastslideVersion = "0.7.4"

// One classifier per supported platform; matches the GitHub Release assets
// produced by NKI-AI/fastslide (fastslide-native-<version>-<os>-<arch>.jar).
// windows-aarch64 is not published (no usable build toolchain yet).
val fastslideNativeClassifiers = listOf(
    "linux-x86_64", "linux-aarch64",
    "darwin-x86_64", "darwin-aarch64",
    "windows-x86_64", "windows-aarch64",
)

repositories {
    mavenCentral()

    // FastSlide Java artifacts are published as GitHub Release assets on
    // NKI-AI/fastslide and consumed anonymously (no token) through this
    // ivy/url repository. The release tag equals the bare version, so the
    // [revision] token resolves directly to the asset path.
    //
    // Override the base URL for offline testing against a locally staged
    // release (see the FastSlide repo's tools/publish_java_artifacts.py):
    //   ./gradlew fatJar -PfastslideRepoUrl=file:///tmp/fastslide-release
    ivy {
        url = uri(
            providers.gradleProperty("fastslideRepoUrl")
                .getOrElse("https://github.com/NKI-AI/fastslide/releases/download")
        )
        patternLayout {
            artifact("[revision]/[module]-[revision](-[classifier]).[ext]")
        }
        metadataSources { artifact() }
        content { includeGroup("dev.aifo") }
    }
}

qupathExtension {
    name = "qupath-extension-fastslide"
    group = "io.github.qupath"
    version = "0.1.3"
    description = "QuPath extension for reading whole-slide images via FastSlide"
    automaticModule = "io.github.qupath.extension.fastslide"
}

dependencies {
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // Platform-independent FastSlide Java API (contains the classes the
    // extension compiles against).
    implementation("dev.aifo:fastslide-java:$fastslideVersion")

    // Per-platform native libraries, bundled into the fat JAR at runtime. Each
    // classifier JAR carries its shared library under META-INF/native/<os>-<arch>/.
    fastslideNativeClassifiers.forEach {
        runtimeOnly("dev.aifo:fastslide-native:$fastslideVersion:$it")
    }

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("qupath-extension-fastslide-fat")
    // Track the extension version so the released asset name stays in sync with
    // the catalog's main_url (no more hardcoded version to forget to bump).
    archiveVersion.set(provider { project.version.toString() })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    // Bundle the FastSlide wrapper + every native classifier JAR resolved on the
    // runtime classpath (QuPath itself provides the `shadow` dependencies).
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("fastslide-java-") || it.name.startsWith("fastslide-native-") }
            .map { zipTree(it) }
    })
}
