# Part Preview Prototype

Experimental 3D part-preview purchase confirmation for SLRR Light Edition.

## Folder layout

This mod is self-contained under `mods/PartPreviewPrototype/`. Its contents mirror the paths used by the game:

```text
mods/PartPreviewPrototype/
├── sl/Scripts/game/src/
│   ├── BuyPartPreviewDialog.java
│   └── Catalog.java
└── system/Scripts/render/osd/dialog/src/
    └── PartPreviewDialogBase.java
```

Copy or compile the files from this folder into the matching locations in the game tree. The repository's normal `sl/` and `system/` directories are not modified by this prototype.

## Features in this source prototype

- Large 3D viewport on the left; part name, price, description and compatibility on the right.
- Slow automatic rotation of an independent preview instance; the catalog model is not modified.
- Left-button drag uses the integrated cursor position and rotates the independent preview on yaw and pitch.
- Mouse wheel changes camera distance.
- Automatic rotation resumes gradually after dragging.
- Original purchase flow is preserved: YES returns `0`; NO/Escape returns `1`.

## Status and limitations

This is **not yet runtime-tested**. The sources have not been compiled against the user's installed game and have not been run in SLRR.

Drag position is read from `Input.cursor.getPos()` so the code uses the cursor's integrated OSD coordinates. The left-button and wheel state still use stock mouse device 1, axes 3 and 2 respectively, based on this branch's `system/Scripts/io/src/ControlSet.java`. Verify these mappings in the target build.

The preview creates a second instance from the same part type under its own `Dummy` scene root, with its own light, camera and viewport. It does not rotate, move, clone-over, or otherwise modify the catalog's original `InventoryItem_Part` instance. The temporary instance and scene root are destroyed when the dialog closes.

The viewport rectangle and mouse hit-test coordinates are provisional. They may need adjustment after the first in-game screenshot.

## Build

Use [SLRR-JVM-compiler](https://github.com/LootDelivery/SLRR-JVM-compiler), which requires Node.js 20+. This compiler emits SLRR TUFA `.class` files; it is not `javac`.

Set these paths in PowerShell:

```powershell
$Game = "D:\Games\SLRR"
$Mod = "D:\Path\To\SLRR-Light-Edition\mods\PartPreviewPrototype"
$Out = "$env:TEMP\SLRR-PartPreview-Build"
$Compiler = "D:\Tools\SLRR-JVM-compiler\dist\src\cli.js"
```

Compile in dependency order, using the game directory as `--root` so stock game classes can be indexed:

1. Compile `$Mod\system\Scripts\render\osd\dialog\src\PartPreviewDialogBase.java`; install the resulting `PartPreviewDialogBase.class` in `$Game\system\Scripts\render\osd\dialog\`.
2. Compile `$Mod\sl\Scripts\game\src\BuyPartPreviewDialog.java`; install the resulting `BuyPartPreviewDialog.class` in `$Game\sl\Scripts\game\`.
3. Compile `$Mod\sl\Scripts\game\src\Catalog.java`; install the resulting `Catalog.class` in `$Game\sl\Scripts\game\`.

Example for the first file:

```powershell
node $Compiler --root $Game --out $Out "$Mod\system\Scripts\render\osd\dialog\src\PartPreviewDialogBase.java"
```

After step 1, make the generated bridge class visible in the game tree before compiling step 2. Do the same with `BuyPartPreviewDialog.class` before compiling step 3. Use a fresh output directory or clear stale outputs between builds.

**Keep backups** of the three original/generated target `.class` files and `Catalog.java`. Do not replace the originals until the compiler succeeds.

## First in-game test

- Dialog opens and both buttons close it with the expected result.
- Preview camera shows the selected part in the left viewport.
- Model rotates while idle; camera does not orbit the model.
- Dragging only starts over the preview area.
- Wheel zoom responds once per notch and stays within limits.
- Auto-rotation resumes smoothly after releasing the mouse.
- Cancel leaves the catalog unchanged.
- Confirmed purchase deducts the same price and adds the same item.
- Closing the dialog restores the original part transform and destroys the temporary camera/viewport.
