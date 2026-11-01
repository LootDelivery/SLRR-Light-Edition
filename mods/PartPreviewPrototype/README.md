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

The current `bboxcenter_query` build is a new **experimental runtime probe**.
It has been statically checked, but it has **not yet been tested in SLRR**.
It must pass garage/shop loading before the preview query is tested.

## What the query does

The patcher redirects only the native registration pointer for
`java.util.resource.GameRef.getInfo(II)I` at `0x00489666` to a private thunk at
`0x007DF972`. The String overload registration at `0x00489685` is unchanged.

The thunk repeats the original argument parsing and receiver-resolution setup.
After that, ordinary `getInfo` requests jump into the original dispatcher at
`0x0047DE25`, after its setup code. A reserved query value `0x584F4242`
(ASCII `BBOX`) is handled by the private thunk instead.

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

## Java diagnostic

The updated `BuyPartPreviewDialog.java` obtains the mesh ID with
`previewPart.getMesh()`, creates a `GameRef` for that ID, loads it, reads its
resource type and performs the three reserved `getInfo(0x584F4242, axis)`
queries. It logs one line in the game log:

```
BBOX_QUERY meshID=... type=... center_milli=x,y,z
```

The updated Java source must be compiled with the normal SLRR script compiler
before testing. This Java source and the native probe EXE must be used as a
matching pair.

## Build

Build from the exact known-good baseline, not from another probe:

```powershell
python .\\native\\patch_bboxcenter_query.py "D:\\Games\\SLRR\\StreetLegal_Redline_native_silent_v2.exe" "D:\\Games\\SLRR-test\\StreetLegal_Redline_native_bboxcenter_query_probe.exe"
```

The patcher verifies the base SHA-256, PE32 layout, native registration bytes
and signatures, code cave, registration relocation, and Reflection writer
guards. It refuses to overwrite the source executable.

## Test order

1. Keep `StreetLegal_Redline_native_silent_v2.exe` untouched as fallback.
2. Compile the updated `BuyPartPreviewDialog.java` using the usual SLRR script
   build process.
3. Launch `StreetLegal_Redline_native_bboxcenter_query_probe.exe`.
4. First verify the game launches and can enter the garage/shop.
5. Only after that, open the Buy Part preview to trigger the BBox diagnostic.
6. Check the game log for the `BBOX_QUERY` line.

If the game crashes before opening the preview, revert to `silent_v2` and
report the exact executable filename used. If a query line appears, retain
the complete line including `meshID`, `type` and `center_milli`.

No automatic recentering or preview-rotation changes are included in this
probe. Its only purpose is to establish whether the native mesh resource
exposes the expected AABB center through this path.
