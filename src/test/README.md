# Regression checks

Run the recurring checks for both supported Minecraft versions:

```powershell
.\gradlew.bat :26.1:check :26.2:check
```

`buildAll` also runs these checks before collecting the jars. The fixtures use
headless `main` entry points rather than a JUnit runner; the normal Gradle `test`
task does not discover them. Use `check` to run the suite.

For a targeted run, prefix one of these tasks with `:26.1:` or `:26.2:`:

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

Keep behavior regressions and deterministic generator validation here. Historical
research notes, exact label/wording assertions, pixel-size snapshots, and one-off
runtime probes do not belong in the recurring suite.
