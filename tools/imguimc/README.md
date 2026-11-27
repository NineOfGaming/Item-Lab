# ImGuiMC for Minecraft 26.3

The 26.3 target uses FoundryMC's unpublished port at commit
[`ee1b528a1390f4de1ed80da00b759b62c90d8ec7`](https://github.com/FoundryMC/imguimc/commit/ee1b528a1390f4de1ed80da00b759b62c90d8ec7).
It is built with the private version `2.0.5-itemlab.ee1b528.3` and bundled into the
26.3 Item Lab jar. The 26.1 and 26.2 targets use the published ImGuiMC dependency.

From the repository root, with Git and Java 25 installed:

```powershell
powershell -ExecutionPolicy Bypass -File tools/imguimc/build.ps1
.\gradlew.bat :26.3:buildAndCollect
```

The script keeps the source checkout and Maven artifacts under `.gradle/`.
It applies the checked-in `26.3.patch`: the render-target constructor needs its
color format before its depth format, and ImGui must draw before Minecraft's
swapchain blit and GPU submission. Without these fixes, opening the editor crashes
or leaves its interface invisible. It also retains asynchronous pipeline compilation
across frames; restarting it each frame drops the draw lists before compilation
can finish. The script accepts only this patch on top of
the pinned commit and refuses other tracked changes.
The SDL backend also enables native text input through Minecraft's ownership-aware
text-input manager when an ImGui field is focused, and releases only its own claim.
This restores typing while preserving vanilla chat input when focus changes.
It only publishes to the repository-local Maven directory. Sources and the
upstream LGPL license are retained in the generated source jar and dependency
artifacts. The upstream SDL backend currently leaves detached platform windows
and IME integration unfinished; those features need separate validation before use.
