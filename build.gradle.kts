import java.util.Properties

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    `maven-publish`
}

abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val agentJar: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf("-javaagent:${agentJar.asPath}")
}

val mockitoAgent = configurations.create("mockitoAgent")

val modVersion = project.property("mod.version") as String
val modArchivesName = project.property("mod.archives_name") as String
val targetMinecraftVersion = sc.current.version
val diamondFireServerVersion = Properties().apply {
    rootProject.file("src/main/resources/assets/itemlab/diamondfire/server.properties").inputStream().use { load(it) }
}.getProperty("minecraft_version")

fun minecraftVersionParts(version: String): List<Int> {
    require(version.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) { "Invalid Minecraft release: $version" }
    val parts = version.split('.').map(String::toInt)
    return List(3) { parts.getOrElse(it) { 0 } }
}

val diamondFireEnabled = minecraftVersionParts(targetMinecraftVersion)
    .zip(minecraftVersionParts(diamondFireServerVersion))
    .firstOrNull { (client, server) -> client != server }
    ?.let { (client, server) -> client > server } ?: true
val minecraftVersionConstraint = project.property("mod.minecraft_constraint").toString()
val fabricLoaderVersion = project.property("deps.fabric_loader").toString()
val fabricApiVersion = project.property("deps.fabric_api").toString()
val adventureFabricVersion = project.property("deps.adventure_fabric").toString()
val imguiMcVersion = project.property("deps.imguimc").toString()

version = "$modVersion+$targetMinecraftVersion"
group = project.property("mod.group") as String

base {
    archivesName = modArchivesName
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }

    exclusiveContent {
        forRepository {
            maven {
                name = "DevAuth"
                url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
            }
        }
        filter {
            includeGroup("me.djtheredstoner")
        }
    }

    exclusiveContent {
        forRepository {
            maven {
                name = "RyanHCode Maven"
                url = uri("https://maven.ryanhcode.dev/releases")
            }
        }
        filter {
            includeGroup("foundry.imguimc")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$targetMinecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(include("net.kyori:adventure-platform-fabric:$adventureFabricVersion")!!)

    implementation("foundry.imguimc:imguimc-fabric-$targetMinecraftVersion:$imguiMcVersion")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

    testImplementation("org.mockito:mockito-core:5.24.0")
    mockitoAgent("org.mockito:mockito-core:5.24.0") { isTransitive = false }
}

loom {
    runs.configureEach {
        runDirectory.set(rootProject.layout.projectDirectory.dir("run"))
    }

    runs.named("client") {
        displayName.set("Minecraft Client $targetMinecraftVersion")
        appendProjectPathToDisplayName.set(false)
        generateRunConfig.set(true)
        preferGradleTask.set(true)
    }
}

val targetJavaVersion = 25

tasks.processResources {
    if (!diamondFireEnabled) exclude("assets/itemlab/diamondfire/*.json")
    val resourceProperties = mapOf(
        "version" to "$modVersion+$targetMinecraftVersion",
        "minecraft_version" to targetMinecraftVersion,
        "minecraft_version_constraint" to minecraftVersionConstraint,
        "loader_version" to fabricLoaderVersion,
        "fabric_version" to fabricApiVersion,
        "imguimc_version" to imguiMcVersion
    )
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(resourceProperties)
    }
}

tasks.processTestResources {
    if (!diamondFireEnabled) exclude("diamondfire/**")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = targetJavaVersion
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withSourcesJar()
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests.set(false)
}

fun registerVerificationCheck(
    name: String,
    mainClassName: String,
    descriptionText: String,
    configure: JavaExec.() -> Unit = {}
): TaskProvider<JavaExec> = tasks.register<JavaExec>(name) {
    group = "verification"
    description = descriptionText
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.nineofgaming.itemlab.$mainClassName")
    workingDir(layout.buildDirectory.dir("verification/$name"))
    doFirst { workingDir.mkdirs() }
    configure()
}

val verifyEditorCompatibility = registerVerificationCheck(
    "verifyEditorCompatibility",
    "editor.EditorRegressionSuite",
    "Checks version-specific codecs, generated forms, navigation, and syntax editing."
) {
    args("compatibility")
    systemProperty("itemlab.expectedMinecraftVersion", targetMinecraftVersion)
}

if (targetMinecraftVersion == diamondFireServerVersion) {
    tasks.register<JavaExec>("captureDiamondFireServerRegistry") {
        group = "diamondfire"
        description = "Regenerates the DiamondFire server registry using Minecraft $diamondFireServerVersion."
        dependsOn(tasks.testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("dev.nineofgaming.itemlab.editor.component.DiamondFireRegistrySnapshot")
        workingDir(layout.buildDirectory.dir("diamondfire-registry"))
        doFirst { workingDir.mkdirs() }
        args(rootProject.file("src/main/resources/assets/itemlab/diamondfire/server-registry.json").absolutePath)
        if (providers.gradleProperty("diamondfire.checkRegistry").orNull == "true") {
            args("--check")
        }
    }
}

val verifyDiamondFireCompatibility = registerVerificationCheck(
    "verifyDiamondFireCompatibility",
    "editor.diamondfire.DiamondFireCompatibilityCheck",
    if (diamondFireEnabled) "Checks offline DiamondFire data and support badges."
    else "Checks that DiamondFire features are disabled on this older client."
) {
    workingDir(layout.buildDirectory.dir("diamondfire-compatibility"))
}

val diamondFireCodecMode = providers.gradleProperty("diamondfire.codecMode").orElse("verify")
if (diamondFireEnabled) registerVerificationCheck(
    "verifyDiamondFireCodecs",
    "editor.component.DiamondFireCodecCheck",
    "Compares codec outcomes with the independent $diamondFireServerVersion corpus."
) {
    workingDir(layout.buildDirectory.dir("diamondfire-codecs"))
    args(
        diamondFireCodecMode.get(),
        rootProject.file("src/test/resources/diamondfire/codec-baseline.json").absolutePath
    )
    args(
        rootProject.file(
            if (diamondFireCodecMode.get() == "capture") "src/test/resources/diamondfire/codec-cases.json"
            else "src/test/resources/diamondfire/codec-comparison-$targetMinecraftVersion.json"
        ).absolutePath
    )
}

val verifyEditorTransactions = registerVerificationCheck(
    "verifyEditorTransactions",
    "editor.EditorRegressionSuite",
    "Checks pending-edit composition, conflicts, and non-mutating editor recovery."
) {
    args("transactions")
}

val verifyPendingEditorCommits = registerVerificationCheck(
    "verifyPendingEditorCommits",
    "editor.PendingEditorCommitCheck",
    "Checks real editor commits and save/copy writes against an isolated mock client."
) {
    jvmArgumentProviders.add(objects.newInstance<MockitoAgentProvider>().apply {
        agentJar.from(mockitoAgent)
    })
}

tasks.check {
    dependsOn(
        verifyEditorCompatibility,
        verifyEditorTransactions,
        verifyPendingEditorCommits,
        verifyDiamondFireCompatibility
    )
    if (diamondFireEnabled) dependsOn("verifyDiamondFireCodecs")
}

tasks.jar {
    from(rootProject.file("LICENSE.txt")) {
        rename("LICENSE\\.txt", "LICENSE_$modArchivesName.txt")
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds this Minecraft version and collects its jars in the root build directory."
    dependsOn(tasks.build)
    from(tasks.jar.flatMap { it.archiveFile })
    from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = modArchivesName
            from(components["java"])
        }
    }
}
