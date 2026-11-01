# Part Preview Prototype

Experimental 3D part-preview purchase confirmation for SLRR Light Edition.

## Files are already in game-tree locations

This branch contains the mod sources and integration change at their final paths:

- `sl/Scripts/game/src/BuyPartPreviewDialog.java`
- `sl/Scripts/game/src/Catalog.java` — already changed to open the new dialog
- `system/Scripts/render/osd/dialog/src/PartPreviewDialogBase.java`

There is no separate patch to apply and no need to manually edit `Catalog.java`.

## Features in this source prototype

- Large 3D viewport on the left; part name, price, description and compatibility on the right.
- Slow automatic rotation of the **part object**.
- Left-button drag rotates the part on yaw and pitch.
- Mouse wheel changes camera distance.
- Automatic rotation resumes gradually after dragging.
- Original purchase flow is preserved: YES returns `0`; NO/Escape returns `1`.

## Status and limitations

This is **not yet runtime-tested**. The sources have not been compiled against the user's installed game and have not been run in SLRR.

Input currently assumes the stock defaults from this branch's `system/Scripts/io/src/ControlSet.java`: mouse device 1; physical axes 0/1 for X/Y, 2 for wheel, and 3 for left button. Verify these in the target build.

The preview reuses the part and inventory root created by the catalog. It creates its own camera and viewport, and restores the part's previous transform when the dialog closes. It does not clone or re-parent the part.

The viewport rectangle and mouse hit-test coordinates are provisional. They may need adjustment after the first in-game screenshot.

## Build

Use [SLRR-JVM-compiler](https://github.com/LootDelivery/SLRR-JVM-compiler), which requires Node.js 20+. This compiler emits SLRR TUFA `.class` files; it is not `javac`.

Build in dependency order, using an output directory outside the game root:

1. Compile `system/Scripts/render/osd/dialog/src/PartPreviewDialogBase.java`. Copy the generated `PartPreviewDialogBase.class` to `GAME/system/Scripts/render/osd/dialog/`.
2. Compile `sl/Scripts/game/src/BuyPartPreviewDialog.java`. Copy the generated `BuyPartPreviewDialog.class` to `GAME/sl/Scripts/game/`.
3. Compile the already-integrated `sl/Scripts/game/src/Catalog.java`. Copy the generated `Catalog.class` to `GAME/sl/Scripts/game/`.

Compile each file with the game directory as `--root` so the compiler can index stock class declarations. For example:

```powershell
$Game = "D:\Games\SLRR"
$Out = "$env:TEMP\SLRR-PartPreview-Build"
$Compiler = "D:\Tools\SLRR-JVM-compiler\dist\src\cli.js"

node $Compiler --root $Game --out $Out "$Game\system\Scripts\render\osd\dialog\src\PartPreviewDialogBase.java"
```

After step 1, copy that class into the game tree before compiling step 2. After step 2, copy its class into the game tree before compiling step 3. This makes the new class declarations visible to the compiler. Use a fresh output directory or clear stale outputs between builds.

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
