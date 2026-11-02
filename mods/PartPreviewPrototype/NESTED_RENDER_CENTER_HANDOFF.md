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


## Direct-mesh precedence correction (2026-11-02)

Latest user measurements:
- spring: captured offset `(0.000, +0.257, 0.000)`; native verification center is `(0,0,0)` both at base and after rotation;
- hood: captured `(0.000, -0.021, +0.074)`; verified center remains `(0,0,0)`;
- OCA bumper: captured `(+0.044, -0.400, +0.054)`; verified center remains `(0,0,0)`;
- Vstanced arm: captured `(-0.954, -0.020, -0.733)`; verified center remains within 0.001 after rotation.

These prove the native world-center query and fixed captured-offset rotation are numerically consistent for those render paths. They do not alone prove that every visible submesh in a compound Part uses that same render transform.

A bug was identified in Java selection: `traceRenderPayloadDiagnostics()` can discover a nested type-5 mesh even when `Part.getMesh()` already supplies a valid direct mesh AABB. Previously, `hasNestedMeshBounds` then overrode the direct AABB center unconditionally. This can shift compound engine parts (e.g. belts) using a secondary nested submesh's bounds rather than the Part's direct mesh.

Commit `135e4dacaff518f3392b43051115df59fb0bf4e6` fixes precedence:
- valid direct `Part.getMesh()` AABB always wins;
- nested render-child center is used only as a fallback when direct mesh bounds are unavailable;
- spring path (`getMesh()==0`) remains eligible for nested centering;
- no native EXE change.

Runtime retest is pending. If engine belts return to their original pivot, this confirms the precedence bug. Spring still reports a mathematically centered AABB; if its visible rotation pivot remains at the mounting point, the remaining issue is likely a mismatch between the nested resource AABB and the actual spring render transform or an additional transform in its render hierarchy, not a failure of the measured center compensation. Do not add more compensation based solely on the existing zero-center verification.
