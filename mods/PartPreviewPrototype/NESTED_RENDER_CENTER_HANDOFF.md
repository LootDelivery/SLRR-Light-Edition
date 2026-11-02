# PartPreview: nested render-child AABB centering handoff

Updated: 2026-11-02

Current branch: `feature/part-preview-live-aabb`

Current Java integration commit:
- `d577cdad5ae8dcaf86e505a5b503de0cf62bd139`

Native source/builder commits:
- `d20ad367a4473050651ed0ff41f943eadb1fc535` — native axes 500..502
- `44e0d811121ea1dda5f44607c2540f51520ddffb` — pinned EXE builder

## Goal

Center a separately created preview Part whose `Part.getMesh()` returns zero but whose `GII_RENDERINSTANCE` owns a render-child with an embedded mesh resource. Do not change source/catalog Part transforms or global pivots. Direct-mesh parts retain the existing path.

## Confirmed resource chain

For the tested suspension/spring part:

1. `Part.getMesh() == 0`.
2. `Part.getInfo(GII_RENDERINSTANCE)` returns a type-3 `INSTANCE_RENDER` child; its parent ID is the preview Part.
3. Outer render object +0xD8 embeds another render object.
4. Embedded +0x84 points to `ResourceRef type 14`, resource ID `2031642`.
5. The type-14 payload at +0x18 points to a child `ResourceRef type 5`, mesh ID `2031636`.
6. `new GameRef(2031636)` resolves as type 5 and the AABB helper reads its runtime/stored bounds.

Latest repeated AABB raw values:
- min fixed: `(-433, -10, -433)`
- max fixed: `(433, 2116, 433)`
- coordinates are in world-units multiplied by 1000
- midpoint from these integer bounds: `(0, 1053, 0)`, i.e. `(0, 1.053, 0)`
- stored root AABB queries axes 6..11 have matched live vertex-scan axes 0..5 in the tested mesh.

Do not use the formatted AABB line in isolation when it disagrees by 0.001 with the raw integer axes; the signed fixed-point axis values are authoritative.

## Confirmed transform chain

- `Part.setMatrix(position, ypr)` changes the parent Part position. In the controlled +0.1 X probe, `Part.getPos().x` read back as 0.099.
- The child `bone00` local matrix does not update synchronously with the call.
- After one render update, the child bone matrix and `INSTANCE_RENDER` position change.
- `0x00541FE0` composes the render-node transform hierarchy and stores the resulting matrix at node +0xB0.
- `0x005433E0` consumes that node matrix. In the observed fallback path, its translation is converted to render-instance position using a factor of 0.1.
- The observed tested path has `INSTANCE_RENDER+0xF4 == 0` and the auxiliary list at +0x134 empty. Native code then takes the first node of the list at +0x1FC.

Latest raw-pose independent calculation:
- P00 world center approximately `(0.052776, 0.251228, -0.011592)`.
- P01 after parent +0.1 X approximately `(0.151691, 0.251446, -0.011750)`.
- Center delta approximately `(+0.098916, +0.000218, -0.000158)`; this validates the parent translation and the matrix multiplication convention within rounding tolerance.

Matrix transform used:
`worldCenter = INSTANCE_RENDER.position + R * (INSTANCE_RENDER.scale * localAabbCenter)`

The matrix is read from the first native-selected render-list node at +0xB0. Position is read from INSTANCE_RENDER +0x84; scale from +0x90..+0x98.

## Current native query implementation

The updated `render_diag_dispatch.S` adds:
- axis 500: world-center X * 1000
- axis 501: world-center Y * 1000
- axis 502: world-center Z * 1000

It resolves the embedded type-5 resource, reads the stored root bounds through the existing AABB helper (axes 6..11), computes their midpoint, finds the native-selected first bone-list node, applies per-axis render scale and the matrix 3x3, adds the instance position, and returns signed fixed-point coordinates. It does not write to the render tree.

The first implementation intentionally supports the observed fallback branch only:
- INSTANCE_RENDER +0xF4 must be zero.
- auxiliary list at +0x134 must be empty.
- the nested child resource must be type 5.
Otherwise it returns `AABB_INVALID`.

Native dispatcher builder:
`mods/PartPreviewPrototype/native/build_rendercenter_diag.py`

It requires the known-good payload-diagnostic EXE as input and verifies its SHA-256 before patching only the existing .reloc dispatcher area.

Built test EXE:
- `StreetLegal_Redline_native_aabb_rendercenter_diag.exe`
- SHA-256: `8839dd8ff76b04767da8ddc8498b5e4cdf84efa12c225fed0dcb9af48894d668`
- base EXE SHA-256: `38e09d0dbdd1c8d8afa1f3d0365b8a629b91e0fa8968c91ec53ccbc69bacdc88`

Static PE patch verification passed. The new query has not yet been runtime-tested in SLRR.

## Current Java behavior

`BuyPartPreviewDialog.java` in commit `d577cdad5ae8dcaf86e505a5b503de0cf62bd139`:
- retains the direct-mesh AABB centering path;
- for a detected nested mesh, reads axes 500..502;
- computes the target parent position as current Part position minus the returned world center;
- applies it only to the isolated preview Part;
- uses no Java raw-float decoder.

The center is sampled from the latest completed render transform and then applied through Part.setMatrix. During continuous rotation this is updated on subsequent animation frames; no smoothing is added.

## Crash note

An earlier Java-only attempt to decode native float DWORDs and calculate the center in Java was followed by a crash while rotating the part, without an errorlog. That decoder and its call sites were removed in commit `c9db85a900fe42facaa018e2e512ea330dac8871`. Do not reintroduce it. The current implementation returns fixed-point integers from native code instead.

## Next step

Use both:
1. `StreetLegal_Redline_native_aabb_rendercenter_diag.exe` (replace the previously used payload-diagnostic EXE for this test).
2. Java `BuyPartPreviewDialog.java` from commit `d577cdad5ae8dcaf86e505a5b503de0cf62bd139`.

Test the same spring preview once. The expected trace line begins `NESTED_CENTER nativeWorld=`; the visual result should place the mesh's geometric center at the preview origin. If the native query returns `AABB_INVALID`, inspect whether F4/auxiliary-list state differs from the tested path. Once this works, test one damper before generalizing to all render-child parts.

No further Java float decoder or repeated P00/P01 calibration loop is needed.
