plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

val activeProject = stonecutter.current
    ?: error("Stonecutter does not have an active project")

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes collected build outputs."
    delete(layout.buildDirectory)
}

tasks.register("runActiveClient") {
    group = "fabric"
    description = "Runs the Minecraft client for the active Stonecutter version (${activeProject.version})."
    dependsOn(":${activeProject.project}:runClient")
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds and collects jars for every supported Minecraft version."
    dependsOn(":26.1:buildAndCollect", ":26.2:buildAndCollect")
}
