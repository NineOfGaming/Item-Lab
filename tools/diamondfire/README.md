# Refreshing DiamondFire compatibility

The current baseline is Minecraft 26.3 with DiamondFire 9.0. Normal builds and
gameplay use checked-in data without accessing the network.

When refreshing the action export, update `snapshot-selection.json` to a full
commit SHA from `NineOfGaming/df-info`. Move the former current commit into
`previous_commit`. Review changed item-action signatures and update
`src/test/resources/diamondfire/component-evidence.json`; action names alone do
not establish component or field coverage. Keep historical live observations
dated and separate from the current signature review.

When the server changes Minecraft versions, update
`src/main/resources/assets/itemlab/diamondfire/server.properties`, then capture
the registry and codecs on that exact Minecraft target. For the current target:

```powershell
.\gradlew.bat :26.3:captureDiamondFireServerRegistry :26.3:verifyDiamondFireCodecs '-Pdiamondfire.codecMode=capture'
.\gradlew.bat :26.3:verifyDiamondFireCodecs '-Pdiamondfire.codecMode=report'
```

Review `codec-format-policy.json` against the generated report. Only clients at
or above the server baseline have compatibility profiles. Matching client and
server baselines must have no registry or codec differences. Remove retired
component policies and obsolete comparison fixtures, then regenerate:

```powershell
python tools/diamondfire/generate_snapshot.py --df-info 'C:/path/to/df-info'
python -m unittest discover -s tools/diamondfire -p 'test_*.py'
.\gradlew.bat buildAll
```

The refresh from 8.14 through 9.0 adds custom modifier-ID evidence for the current
Add Item Attribute action and retires `minecraft:map_color`. The remaining item
signatures still match the reviewed evidence. New dialog actions display items
without exposing additional item-component editing operations.
