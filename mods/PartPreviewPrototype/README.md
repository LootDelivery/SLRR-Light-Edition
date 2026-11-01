# Part Preview Prototype — native Bounding Box query

## Known-good baseline

Use `StreetLegal_Redline_native_silent_v2.exe` as the immutable base for all
native experiments.

- SHA-256: `a954fc724a4561e5e30eba3a2cc4be1c162636eac9db5ba7746c53e2f89638bf`
- Confirmed by the user to launch and work with their save.
- Reflection dump/log writers are suppressed; the Reflection registration
  needed by the GlassOpacity functionality remains present.

Do not overwrite this executable.

## Probe status

The registration-only pass-through test redirected only
`GameRef.getInfo(II)I` from its registration entry at `0x00489666` to a
five-byte jump thunk, which jumped straight back to the original dispatcher.
The user confirmed it entered the garage.

The later full-dispatcher clone test did **not** enter the garage and is
rejected. Do not use `StreetLegal_Redline_native_getinfo_clone_probe.exe`
or the old `patch_bbox_center.py` clone patcher.

The first BBox query thunk built from the route-probe executable is also
rejected after the user reported a garage-entry crash when a car is present.
A corrected v2 is now available; it is still an **experimental runtime probe**.

## What the query does

The patcher redirects only the native registration pointer for
`java.util.resource.GameRef.getInfo(II)I` at `0x00489666` to a private thunk at
`0x007DF972`. The String overload registration at `0x00489685` is unchanged.

The thunk repeats the original argument parsing and receiver-resolution setup.
After that, ordinary `getInfo` requests jump into the original dispatcher at
`0x0047DE25`, after its setup code. A reserved query value `0x584F4242`
(ASCII `BBOX`) is intended to be handled by the private thunk instead.

For the reserved query, the thunk obtains the resolved Java receiver's native
resource pointer, then checks:

- resource type at `+0x4C` is `5` (`U_MESH`);
- bounding mode at `+0x80` is `1` (AABB);
- requested axis is 0, 1 or 2.

The AABB center is read as three float32 values at `+0x84`, `+0x88` and
`+0x8C`. The selected value is multiplied by 1000 and returned as an integer.
Divide the logged value by 1000 to get the original model-space coordinate.

Diagnostic return values for the reserved query:

| Signed integer | Meaning |
|---:|---|
| `-2147483648` | No resolved native object |
| `-2147483647` | Resource type was not `U_MESH` |
| `-2147483646` | Resource bounding mode was not AABB |
| `-2147483645` | Axis outside 0–2 |

These sentinel values are diagnostic only; they are not valid center values.

## Static verification of the BBox fields

The offsets and mode used by the thunk are verified from native code in
the baseline executable, not inferred from a guessed C++ layout:

- `ResourceRef.type()I` at `0x0047D210` resolves the native receiver and
  returns the dword at `nativeResource + 0x4C`.
- The type-name pointer table at `0x00618E20` maps index `5` to
  `U_MESH`. Thus the thunk's `type == 5` check is confirmed.
- The native bound-dump routine at `0x00545990` reads the bound mode from
  `+0x80`, the center vector from `+0x84/+0x88/+0x8C`, and the radius data
  after that.
- The bound-mode dispatch table at `0x00545C90` maps `0=sphere`,
  `1=AABB`, `2=#INF`, `3=planes`. Thus `boundmode == 1` is confirmed
  as AABB, and the three center component offsets are confirmed.

This establishes the queried fields and mesh type statically. It does **not**
prove that the redirected native-call path is safe at runtime or that the Java
diagnostic is compiled and running in the target installation.

## Corrected argument order — v2

The original native argument parser at `0x0045D910` decodes the two Java
integer parameters into local stack slots in this order:

- first Java argument (`query`) at `[ESP+0x10]`;
- second Java argument (`axis`) at `[ESP+0x0C]`.

The first route-based BBox thunk used these two slots in the opposite order.
Consequently, `getInfo(0x584F4242, axis)` was not recognized as the reserved
query and could fall through to the stock `getInfo` dispatcher as an unknown
query. That is a concrete defect in v1; whether it fully explains the
garage-entry crash remains to be confirmed in-game.

Use the v2 patcher:

```text
native/patch_bboxcenter_query_from_route_probe_v2.py
```

It is pinned to the user's known-good route-probe executable:

- Input SHA-256: `8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d`
- Output SHA-256: `6b2d958d6b47629e8ae267793a89fd43ba1e2e240b3fcc2e054849ef190ac23d`
- Output filename: `StreetLegal_Redline_native_bboxcenter_query_probe_v2.exe`

V2 corrects only the query/axis stack-slot order in the existing 179-byte thunk.
The native registration, String overload, Reflection writer guards and all
bytes outside that thunk slot are unchanged from the known-good route probe.
The patcher refuses to overwrite its input executable.

This is a static correction, **not yet a confirmed runtime fix**.

## Java diagnostic

The updated `BuyPartPreviewDialog.java` obtains the mesh ID with
`previewPart.getMesh()`, creates a `GameRef` for that ID, loads it, reads its
resource type and performs the three reserved `getInfo(0x584F4242, axis)`
queries. It logs one line in the game log:

```
BBOX_QUERY meshID=... type=... center_milli=x,y,z
```

The updated Java source must be compiled with the normal SLRR script compiler
before testing. This Java source and the v2 native probe EXE must be used as a
matching pair.

## Test order

1. Keep `StreetLegal_Redline_native_silent_v2.exe` and the working
   `getinfo_route_probe.exe` untouched as fallbacks.
2. Compile the updated `BuyPartPreviewDialog.java` using the usual SLRR script
   build process.
3. Launch `StreetLegal_Redline_native_bboxcenter_query_probe_v2.exe`.
4. Test entering the garage with a car present.
5. If garage entry works, open the Buy Part preview to trigger the BBox query.
6. Check the game log for the `BBOX_QUERY` line and retain the complete line,
   including `meshID`, `type` and `center_milli`.

If the game crashes before opening the preview, revert to the known-good
route probe and report the exact executable filename used. Do not use v1.

No automatic recentering or preview-rotation changes are included in this
probe. Its only purpose is to establish whether the native mesh resource
exposes the expected AABB center through this path.
