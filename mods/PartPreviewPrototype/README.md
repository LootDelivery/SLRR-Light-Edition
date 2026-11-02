# Part Preview Prototype — live runtime AABB

This branch replaces the old three-axis AABB-center experiment with a six-axis getter that reads the current visual-mesh CPU vertex buffers.

## Java preview integration

`BuyPartPreviewDialog.java` obtains the mesh RID from the separately-created preview Part, constructs `new GameRef(previewMeshID)`, and queries:

| Query axis | Result |
|---:|---|
| 0 | Min X |
| 1 | Min Y |
| 2 | Min Z |
| 3 | Max X |
| 4 | Max Y |
| 5 | Max Z |

The native helper returns a signed integer equal to `round(coordinate * 1000)`. Java divides by `1000.0`. This deliberately avoids `Float.intBitsToFloat()`, which is absent from SLRR's bundled `java.lang.Float.class`. `0x80000000` is the invalid-result sentinel.

The preview computes the geometric center from live bounds and positions the isolated preview Part so that this center lands at the private preview root origin `(0,0,0)`. It deliberately does not preserve the part's original install/mount position: `savedPosition` belongs to the source part placement, not the camera-centered preview scene. The catalog's source Part is not modified. Camera distance continues to use the existing `GII_SIZE` convention; the AABB extent is not assumed to be interchangeable with `GII_SIZE`.

## Native route

Files:

- `native/aabb_inline_hook.S`
- `native/aabb_helper.S`
- `native/build_partpreview_aabb_experimental.py`

The builder starts from the known-good route-probe executable already included in this folder:

- Base: `StreetLegal_Redline_native_getinfo_route_probe.exe`
- Required SHA-256: `8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d`

The inline hook is placed at `0x0047DE25`, after the original `GameRef.getInfo(II)I` parser and receiver resolver have completed. It recognizes only query `0x584F4242`. Every other query replays the original `test eax,eax; jz 0x0047DEF8` behavior and continues at `0x0047DE2D`.

At this dispatch site, the parser's output-pointer order places the first Java integer argument (query) at `[ESP+0x10]` and the second argument (axis) at `[ESP+0x0C]`. The hook uses those exact slots and copies the axis to ECX before pushing call arguments.

The parser writes the first Java integer argument (query) to `[ESP+0x0C]` and the second (axis) to `[ESP+0x10]`. The hook compares and passes those slots in that order.

The original integer and string native registrations and the known-good route thunk remain unchanged. This avoids the older full-wrapper approach that copied the parser/resolver path.

The helper follows the `scaleMesh()` resource-resolution sequence, validates resource type `5`, obtains the visual root payload, and scans all groups and VertexData child wrappers. It accepts both visual-root factory variants: type 3 (`0x005F2D00`) and type 4 (`0x005F2CE8`). The VertexData wrapper vtable is `0x005F2C08`.

Confirmed VertexData descriptor fields used by the helper:

| Offset | Meaning |
|---:|---|
| `+0x04` | Vertex count |
| `+0x08` | Interleaved vertex-buffer pointer |
| `+0x0C` | Attribute mask; bit 0 means position exists |
| `+0x1C` | Position attribute byte offset |
| `+0x60` | Vertex stride |

The scan reads current CPU positions, so it can observe CPU-side vertex edits made by `scaleMesh()`. Each live axis query scans the mesh independently; there is no cache. GPU-only skinning/deformation are not covered.

For diagnostics, query axes `6..11` return the root's original loader-accumulated bounds at `+0xCC..+0xE0` (MinX/MinY/MinZ/MaxX/MaxY/MaxZ). These stored bounds are not refreshed by `scaleMesh()`. On an unmodified mesh, compare them with axes `0..5`: close values validate the scan and coordinate space; large differences indicate a vertex-layout or traversal problem.

## Build

Run from this folder in PowerShell. GNU binutils (`as`, `ld`) with ELF32/i386 support must be on `PATH`.

```powershell
python .\native\build_partpreview_aabb_experimental.py `
  .\StreetLegal_Redline_native_getinfo_route_probe.exe `
  .\StreetLegal_Redline_native_aabb_getter_axisfix.exe
```

The builder pins the input SHA-256, refuses to overwrite the input, verifies the native registration/dispatcher bytes, and requires the candidate `.text` padding to be zero. It writes a separate executable and verifies that changes are restricted to the `.text` VirtualSize field, the two code caves, and the eight-byte dispatch site.

No DLL, injected process hook, external process-memory access, or new Java native method is used. The inline detour is within the game's own existing `getInfo` routine.

## Test sequence

The stack-order build uses query at `[ESP+0x10]` and axis at `[ESP+0x0C]`. SHA-256: `28b97110524556014bfae33e620b00100ce8f3d1e3bc1df8bcf6f10abed52877`.



1. Keep the route-probe executable and original game executable untouched.
2. Build the separate `StreetLegal_Redline_native_aabb_getter_axisfix.exe`.
3. Test in a copied game installation.
4. First confirm garage entry and catalog category loading.
5. Open a part preview and inspect `PartPreviewTrace.txt` in the game's temp-save directory. Axes `0..5` are the fresh CPU-vertex bounds; axes `6..11` are the loader's stored root bounds. Compare them on an unmodified mesh.
6. Check `savedPosition`, `savedOrientation`, `center`, `rotatedCenter`, and `finalPos` in the trace. `finalPos` should equal `-rotatedCenter`; the source/install position is logged for diagnosis but is not retained in the private preview root. `T01 after setMatrix readbackPos` records the position after the two-argument Part matrix call. `T02 after GameRef.setPos readbackPos` records the position after the dedicated native `GameRef.setPos(Vector3)` call. The two results distinguish the Part-level matrix path from the base GameRef translation setter.
7. Verify that the old `GII_SIZE` camera framing remains intact, including previously invisible or oversized parts.
8. After basic tests pass, use a mesh changed by `scaleMesh()` and verify that fresh axes `0..5` change while stored axes `6..11` remain stale.

The user confirmed that the previous integrated build starts and enters the game, but some preview pivots still shift incorrectly and some parts are not visible. The current branch adds the second root type, restores legacy `GII_SIZE` camera fitting, exposes comparison/transform diagnostics, and restores the parser-derived stack argument order in the native hook. These latest changes still require runtime testing. If garage entry, catalog loading, or preview creation crashes, stop testing and restore the known-good route-probe executable. Do not replace the main game executable with this test build.

## Legacy experiments

The earlier `bboxcenter_*` patchers and `StreetLegal_Redline_native_bbox*` builds tested the old center-only path. They are legacy experiments and are not used by this integration.
