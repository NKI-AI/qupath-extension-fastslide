plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

repositories {
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}

// TODO: Configure your extension here (please change the defaults!)
qupathExtension {
    name = "qupath-extension-fastslide"
    group = "io.github.qupath"
    version = "0.1.0-SNAPSHOT"
    description = "A simple QuPath extension"
    automaticModule = "io.github.qupath.extension.fastslide"
}

// TODO: Define your dependencies here
dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    implementation(":libfastslide_java")
    // If you aren't using Groovy, this can be removed
    shadow(libs.bundles.groovy)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("qupath-extension-fastslide-fat")
    archiveVersion.set("0.1.0-SNAPSHOT")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    from(zipTree("libs/libfastslide_java.jar"))

    from("native") {
        into("native")
    }
}