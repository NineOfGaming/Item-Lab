# Regression checks

Before the first 26.3 build, prepare its pinned ImGuiMC source dependency:

```powershell
powershell -ExecutionPolicy Bypass -File tools/imguimc/build.ps1
```

Run the recurring checks for all supported Minecraft versions:

```powershell
.\gradlew.bat :26.1:check :26.2:check :26.3:check
```

`buildAll` also runs these checks before collecting the jars. The fixtures use
headless `main` entry points rather than a JUnit runner; the normal Gradle `test`
task does not discover them. Use `check` to run the suite.

Each verification JVM has its own temporary directory under the target's build
folder, including native library extraction, so the version checks can run in parallel.

The 26.3 renderer also has a client game test requiring a working graphics device:

```powershell
.\gradlew.bat :26.3:runClientGameTest
```

It creates an isolated world under `.gradle/client-game-test-26.3`, opens and
reopens the editor, checks its color attachment and visible frame composition,
and exercises every item-model preview mode plus the equipment preview. Captures
of the actual completed frames are saved in that directory's `screenshots` folder.
It also verifies native SDL text-input activation, ASCII/Unicode insertion,
Ctrl+A and Backspace, and input handoff to and from vanilla chat. Fabric's test
harness blocks physical SDL events, so test payloads go through the SDL backend
directly while the real native activation state and widget contents are checked.

For a targeted run, prefix one of these tasks with `:26.1:`, `:26.2:`, or `:26.3:`:

- `verifyEditorCompatibility`: component codecs, data models, generated forms,
  navigation, and JSON/SNBT syntax.
- `verifyEditorTransactions`: atomic composition, ownership conflicts, and
  non-mutating recovery against real editor buffers.
- `verifyPendingEditorCommits`: save/copy, failure retention, undo, and nested
  editing against mocked client IO. It runs in a separate JVM with Mockito's agent.
- `verifyDiamondFireCompatibility`: server policy, registry data, and runtime hints;
  also checks that older clients leave DiamondFire features disabled.
- `verifyDiamondFireCodecs`: lossless codec comparisons against the server baseline
  (only available on DiamondFire-enabled versions).

The DiamondFire fixtures are maintained data, not disposable test output: the
snapshot generator uses them to produce shipped runtime hints. Check the generator
offline with:

```powershell
python -m unittest discover -s tools/diamondfire -p 'test_*.py'
```

See [the DiamondFire refresh workflow](../../tools/diamondfire/README.md) when
updating the pinned action export or the server's Minecraft baseline.

Keep behavior regressions and deterministic generator validation here. Historical
research notes, exact label/wording assertions, pixel-size snapshots, and one-off
runtime probes do not belong in the recurring suite.
