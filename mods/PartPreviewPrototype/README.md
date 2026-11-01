# Part Preview Prototype — native Bounding Box query

## Known-good control builds

Keep these files untouched:

- `StreetLegal_Redline_native_silent_v2.exe` — confirmed working baseline.
- `StreetLegal_Redline_native_getinfo_route_probe.exe` — user-confirmed garage entry; SHA-256:
  `8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d`.

Do not use the two earlier full-thunk BBox builds:

- `StreetLegal_Redline_native_bboxcenter_query_probe_from_route_probe.exe`
- `StreetLegal_Redline_native_bboxcenter_query_probe_v2.exe`

The user reported garage-entry crashes with a car in both. With v2, a catalog category also crashed while loading its models. These builds are rejected.

## Why the full-thunk approach was rejected

The earlier patch replaced the registered `GameRef.getInfo(II)I` handler with a clone of its setup code. That clone repeated argument parsing, receiver resolution and stack preparation for every call, not just BBox queries. The new catalog failure indicates this shared path is unsafe. The argument-slot correction in v2 did not fix the runtime regression.

## Current experiment: inline dispatch hook

Use:

```text
native/patch_bboxcenter_inline_from_route_probe.py
native/bboxcenter_inline_hook.s
```

The patcher is pinned to the user-tested route-probe executable SHA-256 above. It keeps the known-good native registration trampoline and the original `GameRef.getInfo(II)I` handler.

The original handler performs its own argument parsing and receiver resolution. Only after that setup, at VA `0x0047DE25`, a short detour checks whether the first Java integer argument is the reserved query `0x584F4242` (`BBOX`).

- Non-BBox calls replay the original `test eax,eax; jz 0x0047DEF8` behavior and continue at `0x0047DE2D`.
- BBox calls read the second Java integer argument as axis 0–2 and return the selected AABB center component multiplied by 1000.
- The query is checked at `[ESP+0x10]`; the axis is at `[ESP+0x0C]`. This order follows the original parser's output pointer sequence.

The inline hook does not copy or reimplement the original parser or receiver-resolution prologue. It changes only the 129-byte code-cave region immediately after the existing 5-byte route trampoline and the 8-byte dispatch site at `0x0047DE25`. The patcher checks that all other file bytes remain unchanged.

This is a narrower design, not a confirmed runtime fix. It still modifies a shared dispatcher, so it must be tested carefully.

## BBox data layout

The queried fields are statically supported by native code:

- Resource type at `+0x4C`; value `5` is `U_MESH`.
- Bounding mode at `+0x80`; value `1` is AABB.
- AABB center float32 components at `+0x84`, `+0x88`, `+0x8C`.

The native bound-dump routine at `0x00545990` reads these offsets. The bound-mode dispatch table at `0x00545C90` maps `0=sphere`, `1=AABB`, `2=#INF`, `3=planes`.

## Build

From the directory containing the known-good route-probe EXE:

```powershell
python .\\native\\patch_bboxcenter_inline_from_route_probe.py `"D:\\Games\\SLRR\\StreetLegal_Redline_native_getinfo_route_probe.exe`" `"D:\\Games\\SLRR-test\\StreetLegal_Redline_native_bboxcenter_inline_probe.exe`"
```

The output is a separate executable. The patcher refuses to overwrite the input.

## Test sequence

1. Preserve both known-good control EXEs.
2. Use the same compiled Java scripts as the previous BBox test; no Java source changes are required for this native experiment.
3. Launch `StreetLegal_Redline_native_bboxcenter_inline_probe.exe`.
4. First test entering the garage with a car present.
5. If that works, load a save without a car and enter the catalog.
6. Open the muffler category and verify that its models finish loading.
7. Only after those checks pass, open a part preview to trigger the BBox query.
8. Retain the complete `BBOX_QUERY meshID=... type=... center_milli=...` log line.

If the garage or catalog still crashes, stop testing this build and return to the known-good route probe. A successful garage/catalog test is not yet proof that the BBox result is correct; the log line is the final check.

No automatic recentering or preview-rotation changes are included. This probe only tests access to the native mesh AABB center.
