plugins {
    application
}

group = "pixelgen"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

application {
    mainClass = "pixelgen.Cli"
}

tasks.named<JavaExec>("run") {
    // Relative asset paths given to the CLI resolve against the project folder.
    workingDir = projectDir
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Exports assets/ into the Godot project named in pixelgen.conf; the same as `pixel-gen export`.
// Gradle skips it (UP-TO-DATE) while assets/, palettes/, pixelgen.conf and the tool are unchanged.
// It can't see files deleted in the Godot project; `pixel-gen export` notices those itself.
tasks.register<JavaExec>("exportAssets") {
    group = "pixel-gen"
    description = "Exports changed sprites and tilesets into the Godot project set in pixelgen.conf."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "pixelgen.Cli"
    args("export")
    workingDir = projectDir
    inputs.files(fileTree("assets"), fileTree("palettes"), file("pixelgen.conf"))
        .withPropertyName("sources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(layout.buildDirectory.file("pixelgen/export.state"))
}
