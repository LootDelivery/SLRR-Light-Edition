# Part Preview Prototype

Experimental replacement for the part-purchase confirmation dialog in SLRR Light Edition.

## What it adds

- A large 3D viewport on the left side of the confirmation window.
- A part name, price, description and compatibility note on the right.
- Slow automatic rotation of the **part object**.
- Left-button dragging to rotate the part on yaw and pitch.
- Mouse-wheel zoom by changing camera distance.
- Automatic rotation resumes gradually after manual dragging.
- The original YES/NO result contract is preserved: YES returns `0`, NO/Escape returns `1`.

## Important status

This is a source prototype, not a runtime-verified release. The source was written against the APIs present in the private `LootDelivery/SLRR-Light-Edition` repository. It has **not** been compiled in the user's installed game or tested in a running SLRR session.

Input polling currently assumes the stock default device order used by `ControlSet.java`: device 1 is `SysMouse`; physical axes 0/1 are cursor X/Y, axis 2 is wheel, and axis 3 is left mouse button. This matches the Light Edition's stock control defaults, but must be checked on the target installation.

The preview camera uses the same `InventoryItem.localroot` that already contains the catalog part. It does not clone or reparent the part. It relies on the catalog inventory panel having already created the part's preview light.

## Source layout

- `src/java/render/osd/dialog/PartPreviewDialogBase.java` — small access bridge in the dialog package. It exposes the dialog's package-private OSD to a subclass without changing the stock `Dialog` class.
- `src/java/game/BuyPartPreviewDialog.java` — the actual preview dialog.
- `Catalog.integration.patch` — one-line replacement at the existing purchase-dialog call site.

## Build and install

Use a clean backup of the game directory. Keep compiled outputs outside the game root while building.

The compiler is [SLRR-JVM-compiler](https://github.com/LootDelivery/SLRR-JVM-compiler), not a standard Java compiler. It requires Node.js 20+.

1. Copy `src/java/render/osd/dialog/PartPreviewDialogBase.java` to:
   `GAME/system/Scripts/render/osd/dialog/src/PartPreviewDialogBase.java`
2. Compile it with the SLRR compiler, using the game root as the declaration index. Copy the resulting:
   `OUT/system/Scripts/render/osd/dialog/PartPreviewDialogBase.class`
   to:
   `GAME/system/Scripts/render/osd/dialog/PartPreviewDialogBase.class`
3. Copy `src/java/game/BuyPartPreviewDialog.java` to:
   `GAME/sl/Scripts/game/src/BuyPartPreviewDialog.java`
4. Compile it. Copy:
   `OUT/sl/Scripts/game/BuyPartPreviewDialog.class`
   to:
   `GAME/sl/Scripts/game/BuyPartPreviewDialog.class`
5. Apply `Catalog.integration.patch` to the game's `sl/Scripts/game/src/Catalog.java`. The change replaces only the constructor used by `CatalogInventory.panelLeftClick()`.
6. Compile the modified `Catalog.java` and copy the resulting `Catalog.class` output into `GAME/sl/Scripts/game/Catalog.class`.
7. Start the game and test a cheap part first. Keep the original `Catalog.java` and `Catalog.class` backups so rollback is immediate.

Example compiler invocation (PowerShell; adjust paths):

```powershell
$Game = "D:\Games\SLRR"
$Out = "$env:TEMP\SLRR-PartPreview-Build"
$Compiler = "D:\Tools\SLRR-JVM-compiler\dist\src\cli.js"

node $Compiler --root $Game --out $Out "$Game\system\Scripts\render\osd\dialog\src\PartPreviewDialogBase.java"
```

Repeat the same command for `BuyPartPreviewDialog.java` and then the patched `Catalog.java`. Clear or use a fresh output directory between build stages so stale class files are not mistaken for new output. After building each dependency, copy its class into the game root before compiling the next dependent source; this allows `ClassIndex` to see the new declaration.

## First test checklist

- Dialog opens and closes with both YES and NO.
- The part remains in the catalog after cancelling.
- The preview camera renders the clicked part, not an empty root.
- The model rotates without the camera orbiting it.
- Dragging starts only when the left button is pressed over the preview.
- Wheel zoom is one step per wheel notch and does not continue by itself.
- Rotation resumes smoothly after release.
- Purchase still deducts the same price and adds the same part to inventory.
- Closing the dialog restores the part's original transform and destroys the temporary camera and viewport.

If the mouse axes behave differently on the target build, adjust the four physical axis IDs in `animate()` after checking that build's control defaults. Do not add a DLL hook for this prototype.
