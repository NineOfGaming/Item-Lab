# Item Lab

Item Lab is a client-side Fabric mod for creating and editing items through an in-game menu.

The editor covers everything from names and appearance to item stats, behavior, and advanced data.  
Suggested values and live previews match the content available in Minecraft.

Saving items requires creative mode.

## Features

- Edit held items, hovered inventory items, and items inside containers, or create a new item.
- Dedicated editors for text and appearance, item stats and behavior, and special content.
- Preview items, tooltips, equipment, and other visual changes.
- Suggestions automatically include options added by mods and datapacks.
- Undo and redo changes, restore the original item or its defaults, and directly access complete item data when needed.

## Controls

| Control | Action |
| --- | --- |
| `I` | Open Item Lab for the held or hovered item |
| `Ctrl+Z` | Undo the last editor change |
| `Ctrl+Y` or `Ctrl+Shift+Z` | Redo the last undone change |

Keybinds can be changed from the Controls menu.  
`Create New Item` is also available as an unbound keybind.

When Item Lab is opened from an inventory, it returns to that inventory after closing.

Items in ordinary container slots are edited directly where possible; items selected from creative catalogue slots are copied to the destination shown by the editor.

## Requirements

- Client-side only
- Fabric
- Requires [Fabric API](https://modrinth.com/mod/fabric-api)
- Requires [ImGuiMC](https://modrinth.com/mod/imguimc)

## Configuration

Preferences are saved automatically.  
This includes text formatting behavior, component-browser filters, tooltip visibility preferences, and the equipment-preview base model.

## [DiamondFire](https://mcdiamondfire.com) support

When connected through an official DiamondFire hostname, Item Lab adds:

- A dedicated item tag editor for `hypercube:*` values stored in `minecraft:custom_data`.
- DiamondFire compatibility indicators beside item components.
- Context about components whose client format is newer than DiamondFire's Minecraft version.

---

### Planned features

- Improved raw SNBT/JSON editor controls
- Item storage
- Public API
