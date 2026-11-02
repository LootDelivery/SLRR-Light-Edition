# PartPreview: nested render-child AABB centering handoff

Updated: 2026-11-02

Current branch: `feature/part-preview-live-aabb`

Current Java integration commit:
- `d577cdad5ae8dcaf86e505a5b503de0cf62bd139`

Native source/builder commits:
- `d20ad367a4473050651ed0ff41f943eadb1fc535` — native axes 500..502
- `0b4990f53dc865390118d2e050cb869dea90dd13` — restricts the new dispatch range strictly to axes 500..502
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

The dispatcher handles only axes 500..502; unused axes 489..499 continue to return AABB_INVALID.

The first implementation intentionally supports the observed fallback branch only:
- INSTANCE_RENDER +0xF4 must be zero.
- auxiliary list at +0x134 must be empty.
- the nested child resource must be type 5.
Otherwise it returns `AABB_INVALID`.

Native dispatcher builder:
`mods/PartPreviewPrototype/native/build_rendercenter_diag.py`

It requires the known-good payload-diagnostic EXE as input and verifies its SHA-256 before patching only the existing .reloc dispatcher area.

Built v1 test EXE (superseded):
- `StreetLegal_Redline_native_aabb_rendercenter_diag.exe`
- SHA-256: `8b3e85af7001e404da176b97aecb6956efef2839d980117f33b5cab7ff10428c`
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


## Follow-up v2: render-path selection and initialization guard (2026-11-02)

The user's latest spring log showed:
- the nested type-5 AABB is still valid: min `(-433,-10,-433)`, max `(433,2116,433)`, center Y about `1.053`;
- at `createPreview` time, INSTANCE_RENDER +0x90/+0x94/+0x98 are all zero, so the v1 native query returned a false-but-valid world center `(0,0,0)`;
- by animation frame 2, Java requested Y about `-0.257`, consistent with the live render scale being initialized and the AABB center being transformed;
- the observed spring path had F4=0 and auxiliary-list-empty=1.

The original native function `0x005433E0` was disassembled. Its selection/override behavior is:
1. If the auxiliary list at +0x134 is empty, it uses the first node from +0x1FC.
2. Otherwise it walks the sorted +0x1FC list until it finds the node named `bone00`.
3. It calculates the pose, then, when +0xF4 is non-null, copies seven DWORDs from F4+0x128 into INSTANCE_RENDER +0x80..+0x98. Therefore the final position/scale fields on INSTANCE_RENDER are already the F4-overridden values.

v2 updates `render_diag_dispatch.S` accordingly:
- matches the original first-node vs `bone00` selection;
- no longer rejects non-null F4;
- returns AABB_INVALID while any of the three final scale fields +0x90/+0x94/+0x98 is still zero, preventing pre-render initialization data from being mistaken for a real center;
- continues to require a single nested type-5 mesh resource.

The Java code/API did not change. Direct-mesh parts remain unchanged.

Built v2 EXE:
- `StreetLegal_Redline_native_aabb_rendercenter_v2.exe`
- SHA-256: `ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`
- ZIP SHA-256: `4be889aa88b0304da7ee0c57e4f4bff0f2c24ad197d9f1df648412cd8726ec2f`
- base EXE SHA-256 remains `38e09d0dbdd1c8d8afa1f3d0365b8a629b91e0fa8968c91ec53ccbc69bacdc88`.

Static verification passed: dispatcher code is 1700 bytes (v1: 1603), and only the existing .reloc dispatcher extension changed. Runtime validation in SLRR is still outstanding. Resource collections containing multiple mesh children remain unsupported.


## Follow-up Java stabilization (2026-11-02)

The latest runtime log from the spring confirms:
- the nested mesh AABB remains valid: local center Y about 1.053;
- native center query is invalid during initial construction because INSTANCE_RENDER scale fields +0x90/+0x94/+0x98 are still zero;
- after the render update, the preview position converges to approximately Y=-0.257 (1.053 multiplied by runtime scale about 0.244);
- the former Java path queried the previous completed world pose and then applied a new Part transform on every frame. This creates a one-frame feedback path while the user rotates the preview.

Java commits `64402df9d6582eea3fd303d7c311d785f6884e46` and `e8a4d4a69e0e92e7269d7e590b350f6495a9a139` change this (the second also resets capture state at preview creation):
1. Hold the initial orientation until a first valid native center is available (up to 30 preview frames).
2. Capture the center offset relative to Part position once.
3. Convert that captured offset from its reference orientation using rotated basis vectors, then apply the target orientation to it.
4. Stop querying the native center every frame once captured. This removes the stale-world-pose feedback loop.
5. Remove the active +0.1 X translation probe that previously ran after the tenth transform sample and restored itself two frames later.

The new expected trace line is:
`NESTED_CENTER captured offset=x,y,z baseYpr=y,p,r`

For the known spring, the expected captured offset magnitude is close to 0.257 units on Y (sign depends on the render coordinate convention); this is the scale-adjusted local AABB center, not the raw 1.053 local center.

Direct-mesh handling remains on its existing AABB path. The native EXE is unchanged from v2; only the Java script changed.

## Remaining body-part limitation

The latest supplied log is still from the spring. It proves the spring's single-child type-5 path, but does not expose the resource tree of a body panel. The current discovery code sets `hasNestedMeshBounds` only when the embedded type-14 resource's +0x18 child resolves to a type-5 mesh. A body part that uses a different child type, a collection at +0x38, or another embedded render path will continue to use its original pivot.

To identify the body path without another full dump, the useful lines from one body-part preview are:
- `R11 F4 pointer` and `R11 auxiliary-list-empty`;
- `R12 embedded+0x84 resource-pointer`;
- `R13 resource-payload+0x18 child-resource`;
- `R13 nested mesh GameRef type` (if present);
- `R13 resource-payload+0x38 collection`;
- `NESTED_CENTER waiting` or `NESTED_CENTER captured`.

No body-part behavior is claimed until that resource path is observed.


## One-shot pivot verification added (2026-11-02)

Latest Java commit: `80f86b857a685a7d8807f5555ced6d50785e9450`.

The latest user-provided logs confirm that the OCA Tuning bumper is centered through the nested path and that the spring now captures a nonzero offset:
- spring captured offset: `(0.000, +0.257, 0.000)`, base Part orientation near zero;
- OCA bumper captured offset: `(+0.044, -0.400, +0.054)`, base Part orientation zero.
Both use nested type-14 -> type-5 resource chains. The bumper also has a direct `getMesh()` resource; spring `getMesh()==0`.

The user reports that the bumper now looks centered on X/Z but has a remaining downward vertical pivot offset; the spring still appears to rotate around its mounting point despite the captured offset.

Java now performs two read-only checks:
- `NESTED_CENTER_VERIFY_BASE`: on the frame after the initial centering `setMatrix` has passed through the render update;
- `NESTED_CENTER_VERIFY_ROTATED`: once after the preview has been rotated by more than 0.25 radians from the captured base orientation and the following render update has occurred.

Each line reports native world-space AABB center, Part.getPos(), and Part.getOri(). The measured center is logged only; it is never fed back into the transform. These two values will tell whether the parent translation keeps the geometric center at world origin both before and after rotation.

No native EXE change is required; continue using the v2 EXE. Runtime result of the new probes is pending.


## Nested/direct mesh identity selection (2026-11-02)

Latest user measurements:
- spring: captured offset `(0.000, +0.257, 0.000)`; native verification center is `(0,0,0)` both at base and after rotation;
- hood: captured `(0.000, -0.021, +0.074)`; verified center remains `(0,0,0)`;
- OCA bumper: captured `(+0.044, -0.400, +0.054)`; verified center remains `(0,0,0)`;
- Vstanced arm: captured `(-0.954, -0.020, -0.733)`; verified center remains within 0.001 after rotation.

The verification confirms that the selected nested AABB center is mathematically at world origin after the compensation, including after rotation. It does not by itself prove that the selected nested resource is the same mesh that produces all visible geometry for a compound Part.

The engine-belt symptom points to a resource-selection issue: the nested type-5 child discovered under the render instance may be a secondary submesh different from the Part's direct `getMesh()`. The prior Java code let any discovered nested mesh override the direct AABB, even when the IDs differed. That can shift an engine part sideways by centering the wrong submesh.

Commit `735b2f35ed10e7b0edb77407096219fea0a351c5` implements identity-aware precedence:
- if `Part.getMesh()` is zero, nested type-5 AABB remains the fallback (spring);
- if direct and nested mesh IDs match, nested runtime transform-aware center remains enabled (OCA bumper example: both IDs were `3014932`);
- if both exist but IDs differ, nested centering is disabled and the established direct-mesh AABB path is retained (expected to prevent unrelated engine submesh offsets);
- no native EXE change.

Runtime retest is pending. This is intentionally narrower than disabling nested centering for every direct-mesh Part, because the OCA bumper demonstrated that a matching nested/direct resource can have a useful render transform.

If the spring's visible rotation still appears to pivot at its mounting point while the native center remains exactly zero, the remaining mismatch is in the relationship between the selected type-5 AABB and the actual visible spring render transform (or in what is visually perceived as the pivot). Do not add further positional correction based solely on the current zero-center logs.

## Follow-up: fix nested mesh identity tracking (2026-11-02)

Java commit `e61710463a03e4b9d232d13c92f00d9c96f0eda0` fixes a bug in the identity-aware selection introduced in commit `735b2f35ed10e7b0edb77407096219fea0a351c5`.

The nested type-5 mesh ID was read into the local variable `nestedMeshID` and used to construct `nestedMeshRef`, but the class field `nestedMeshResourceID` was never assigned. Consequently, it remained zero and the later comparison treated every valid direct-mesh Part as though its nested mesh ID differed from the direct mesh ID.

The fix stores `nestedMeshRef.id()` in `nestedMeshResourceID` only after the nested type-5 AABB has passed validation, and emits a `nested mesh identity stored=` trace line.

Expected selection after this fix:
- OCA bumper: if the resolved nested ID is `3014932`, matching the direct mesh ID, nested transform-aware centering is enabled again. This should restore the path that previously centered the bumper on X/Z; the latest downward offset occurred while the nested path was incorrectly skipped.
- Nissan SR20 chain: the real nested ID will now be visible. If it equals direct ID `1351684`, the nested world-center path is selected. If it differs, the direct-mesh path remains selected; further diagnosis is then required because the reported lateral pivot offset persisted while nested centering was skipped.
- Camaro SS belt: the supplied GD12..GD61 raw mesh-group dump does not identify why the part is invisible in the preview. The render-resource chain and the direct mesh/AABB status are still unknown; do not infer a cause from those pointer values alone.
- Springs: the base and rotated native checks still report worldCenter approximately zero, but this verifies only the selected nested mesh AABB center, not necessarily the perceived visible rotation pivot of every spring variant.

Only `BuyPartPreviewDialog.java` changed in this fix. No native dispatcher source or EXE replacement is required. Runtime validation of the corrected ID selection is pending.

## Runtime retest after identity tracking fix (2026-11-02)

Java commit `e61710463a03e4b9d232d13c92f00d9c96f0eda0` produced the following:

### Nissan SR20 chain

- Direct Part mesh ID: `1351684`.
- Nested type-5 mesh ID: `1351684` (identity match confirmed).
- AABB min: `(-1.033, 1.452, -1.279)`.
- AABB max: `(1.069, 5.385, -1.151)`.
- AABB center: approximately `(0.018, 3.418, -1.215)`.
- Captured runtime center offset: `(0.010, 1.917, -0.682)`.
- Verification after compensation: world center `(0,0,0)); after yaw, within about 0.001.

The local AABB center transformed by the observed runtime scale (about 0.56) is consistent with the captured offset. The data therefore supports the current native AABB calculation. It does not explain the user's visual impression that the rotation pivot remains laterally offset. One possibility is that the AABB midpoint is not the visually perceived center of this elongated, very thin mesh (Z extent only 0.128); this is not yet established.

### OCA bumper

- Direct Part mesh ID and nested type-5 mesh ID both equal `3014932`.
- AABB center: `(0,-0.447,0)`.
- Captured runtime offset: `(0.044,-0.400,0.054)`.
- Verification after compensation: world center `(0,0,0)); after rotation, within about 0.001.
- User still perceives the pivot low on Y while X/Z appear acceptable.

There is a trace inconsistency to resolve before changing the centering formula: the log reports `savedPosition=(0.557,-0.012,-0.006)`, while the verified final Part position is almost exactly the negative captured offset `(-0.043,0.400,-0.054)`. The current Java source combines savedPosition and the rotated nested offset when constructing the requested parent position, so those reported values do not appear to agree with the source-level calculation. Do not change the math until the exact requested and actual positions are logged at the setMatrix call.

### One-shot transform diagnostic

Commit `26322f51ca4d476c0e5198b29a3e006881a1bc6e` adds:
- capture log fields `nativeWorldCenter` and `currentPartPos`;
- one-time `NESTED_CENTER_APPLY` trace with `savedPosition`, computed `pivot`, `requestedPartPos`, and immediate `actualPartPos` after `setMatrix`.

Only the Java preview script changed in this diagnostic commit. No EXE update is required. Use the exact new Java source and report only:
- `NESTED_CENTER captured ...`
- `NESTED_CENTER_APPLY ...`
- `NESTED_CENTER_VERIFY_BASE ...`
- `NESTED_CENTER_VERIFY_ROTATED ...`

The full GD/raw pointer dump is no longer needed for these two known resource paths.

## Latest transform trace interpretation (2026-11-02)

The user's exact `NESTED_CENTER_APPLY` logs resolve the earlier apparent requested-vs-actual position mismatch.

For OCA:
- saved Part position: `(0.557,-0.012,-0.006)`
- requested position passed to `setMatrix`: `(-0.601,0.413,-0.047)`
- immediately read-back Part position: `(-0.043,0.400,-0.054)`

The difference `actualPartPos - requestedPartPos` is approximately `(0.558,-0.013,-0.007)`, matching savedPosition within about 0.001 on each axis. This strongly indicates that `Part.setMatrix` applies the supplied translation relative to the Part's original/base translation, rather than treating it as an absolute world-space position. Thus the existing Java subtraction of savedPosition is intentional; do not remove savedPosition from the nested centering formula.

The actual OCA Part position is approximately the negative captured local center:
`(-0.043,0.400,-0.054)` versus captured offset `(0.044,-0.400,0.054)`.
The native verification then reports worldCenter `(0,0,0)` at base and after rotation.

For SR20, savedPosition is zero, so requested and actual positions nearly match:
- requested `(-0.010,-1.917,0.682)`
- actual `(-0.009,-1.917,0.682)`
- native center remains zero at base and after rotation.

### Conclusion and next investigation

The current nested AABB centering and transform compensation are numerically consistent for both known meshes. The user's remaining visual impression (OCA pivot low; SR20 belt pivot laterally offset) is not explained by a failed translation or rotation compensation.

Do not add arbitrary positional offsets or change the current formula. The next useful investigation is whether the AABB midpoint is the desired visual/functional center for these particular meshes, or whether the visible geometry contains disconnected/weighted features that make the bounding-box midpoint visually misleading. Any alternative center definition (for example, an area-weighted triangle centroid) needs independent validation against the visible mesh data before implementation. This is a different measurement problem from the already-validated world-space AABB center.

## Experimental indexed-triangle surface centroid (2026-11-02)

The AABB center and transform compensation are numerically validated, but the user still perceives the pivot as visually offset on some parts. This experiment adds a second center definition without replacing the known-good AABB path.

### Runtime mesh layout used

The existing native `aabb_helper.S` exposes the runtime mesh group/child structures:
- root visual mesh: group array at `+0x48`, group count at `+0x4C`;
- group: child array at `+0x04`, child count at `+0x08`;
- VertexData wrapper vtable: `0x005F2C08`;
- VertexData descriptor pointer: wrapper `+0x1C`;
- FaceIndices descriptor pointer: wrapper `+0x20`;
- VertexData: vertex count `+0x04`, vertex buffer `+0x08`, position mask `+0x0C`, position offset `+0x1C`, stride `+0x60`;
- FaceIndices: index count `+0x00`, index buffer `+0x04`, format `+0x08`, stride `+0x0C`.

The SR20 and OCA diagnostic dumps confirm `FaceIndices format=2`, `stride=2`, with index counts 816 and 1434. SCX V4 format notes independently describe type-5 FaceIndices as a count followed by 16-bit indices. This experiment therefore supports the observed U16 triangle-list path only. Other index formats and skin-specific variants are not guessed.

### Calculation

For each indexed triangle:
- `e1 = v1-v0`, `e2 = v2-v0`;
- `weight = length(cross(e1,e2))` (twice the triangle area; the common factor 1/2 cancels);
- `triangleCenter = (v0+v1+v2)/3`.

The local surface centroid is:
`sum(weight * triangleCenter) / sum(weight)`.

All valid VertexData/FaceIndices pairs are accumulated across every root group. Degenerate triangles are ignored. Invalid indices, NaN/Inf positions, unsupported index format/stride, absent descriptors, or uninitialized render scale return `AABB_INVALID` rather than a partial result.

The local centroid is transformed using the same INSTANCE_RENDER per-axis scale, native-selected render-node matrix, and INSTANCE_RENDER position used by axes 500..502.

### New native axes

- 500..502 remain the validated transform-aware AABB world center.
- 503 = surface-centroid world X * 1000.
- 504 = surface-centroid world Y * 1000.
- 505 = surface-centroid world Z * 1000.

The dispatcher axis ceiling is extended from 502 to 505. The old AABB routine entry redirects to a small gate: requests 500..502 replay the displaced original prologue and continue through the unchanged AABB routine; requests 503..505 enter the new centroid routine.

Native source and builder added:
- `native/render_mesh_centroid.S`
- `native/build_surface_centroid_diag.py`

### Java preview selection

For nested-mesh previews, Java queries both AABB (500..502) and surface centroid (503..505) once when capturing the initial offset. If all three surface-centroid coordinates are valid, it selects the centroid. If not, it falls back to the existing AABB center. The chosen mode is logged as `selected=surface-centroid` or `selected=aabb-fallback`.

Base and rotated verification logs report both centers separately:
- `aabbWorldCenter`
- `surfaceWorldCenter`
- `selectedWorldCenter`

The direct-mesh path is unchanged. Existing `savedPosition` compensation is unchanged.

### Test EXE

Built test file: `StreetLegal_Redline_native_surface_centroid_diag.exe`

- Base EXE SHA-256: `ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`
- Test EXE SHA-256: `32f5e75e4a3bf08ccfdfc67288e3dac6e136f9282ca14a9bc0ed319209a2bc6a`
- Test ZIP SHA-256: `74fe494fc52390e85c759c1cc3e2c838f84f6ff4f1874dfb880e6923efbe8578`

Static PE verification passed. Only the dispatcher axis limit, the existing AABB handler entry, and the new executable `.reloc` code cave changed. This native build has not yet been runtime-tested in SLRR.

Runtime test should use a copied game installation, this new EXE, and the updated Java source. First check OCA bumper and Nissan SR20 belt. Compare the visual result with the AABB fallback and review only the four `NESTED_CENTER` capture/apply/base/rotated lines; the full raw pointer dump is unnecessary. If startup or preview crashes, revert immediately to `StreetLegal_Redline_native_aabb_rendercenter_v2.exe`.

Native source and builder are committed:
- [render_mesh_centroid.S](https://github.com/LootDelivery/SLRR-Light-Edition/blob/ba7060afff4c726686527f473a46e153318ee839/mods/PartPreviewPrototype/native/render_mesh_centroid.S)
- [build_surface_centroid_diag.py](https://github.com/LootDelivery/SLRR-Light-Edition/blob/ba7060afff4c726686527f473a46e153318ee839/mods/PartPreviewPrototype/native/build_surface_centroid_diag.py)

The centroid extension performs one full mesh scan for axis 503 and stores the resulting world X/Y/Z triple in its own writable `.reloc` scratch fields. The immediately following axis 504/505 calls for the same INSTANCE_RENDER handle read Y/Z from that scratch cache rather than rescanning the triangles. Axis 500..502 calls and invalid requests clear the pending cache sequence. No game object/resource fields are written by the query; only the extension's own scratch area is updated.

Current Java integration (surface-centroid selection + AABB fallback + dual verification):
- [BuyPartPreviewDialog.java](https://github.com/LootDelivery/SLRR-Light-Edition/blob/eec34b7184eb339efcbe999668603955559f71d8/mods/PartPreviewPrototype/sl/Scripts/game/src/BuyPartPreviewDialog.java)
- Commit: `eec34b7184eb339efcbe999668603955559f71d8`

Capture and verification traces now print both AABB and surface-centroid coordinates in world units, not raw fixed-point integers.

## SLRR script compiler syntax correction (2026-11-02)

The user reported `!syntax error` at line 817 in `BuyPartPreviewDialog.java` after the first surface-centroid integration. The added code used Java's conditional (`?:`) operator eight times. The SLRR script compiler rejected the first occurrence.

Commit `8354bbeb8fc103790e8285c04f4b439916a5a3f7` replaces all eight conditional expressions with explicit `if` assignments and integer trace flags. No native code, center-selection policy, transform math, or fallback behavior changed. A text scan of the updated file confirms there are no remaining question-mark/conditional-operator occurrences.

Updated Java:
https://github.com/LootDelivery/SLRR-Light-Edition/blob/8354bbeb8fc103790e8285c04f4b439916a5a3f7/mods/PartPreviewPrototype/sl/Scripts/game/src/BuyPartPreviewDialog.java

The file was inspected around the original error line and around both center-selection blocks. This is a source-level syntax correction; the SLRR compiler has not yet been run against this commit.
