plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    replacements {
        // Stonecutter reverses these mappings for targets below 26.3.
        string(current.parsed >= "26.3") {
            replace("DataComponents.SWING_ANIMATION", "DataComponents.ATTACK_ANIMATION")
            replace("minecraft:swing_animation", "minecraft:attack_animation")
            replace("\"swing_animation\"", "\"attack_animation\"")
            replace("pose.mulPose(state.rotation())", "pose.rotate(state.rotation())")
            replace("pose.mulPose(Axis.YP.rotationDegrees(-180.0f))", "pose.rotateDegrees(Axis.YP, -180.0f)")
        }
    }
}

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
    dependsOn(":26.1:buildAndCollect", ":26.2:buildAndCollect", ":26.3:buildAndCollect")
}
