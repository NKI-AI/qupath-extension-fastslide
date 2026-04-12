plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

repositories {
    mavenCentral()
}

qupathExtension {
    name = "qupath-extension-fastslide"
    group = "io.github.qupath"
    version = "0.1.0-SNAPSHOT"
    description = "QuPath extension for reading whole-slide images via FastSlide"
    automaticModule = "io.github.qupath.extension.fastslide"
}

dependencies {
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    implementation(files("libs/fastslide-java-0.2.2.jar"))
    runtimeOnly(files("libs/fastslide-native-0.2.2-darwin-aarch64.jar"))

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("qupath-extension-fastslide-fat")
    archiveVersion.set("0.1.0-SNAPSHOT")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    from(zipTree("libs/fastslide-java-0.2.2.jar"))
    from(zipTree("libs/fastslide-native-0.2.2-darwin-aarch64.jar"))
}
