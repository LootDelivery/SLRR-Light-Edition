## Native getInfo dispatcher clone — isolation test

The registration-only pass-through probe has been confirmed to enter the
garage successfully. The next step clones the original native dispatcher
instead of modifying its shared call site.

The patcher copies the complete function
`0x0047DDF0–0x0047DF3F` to executable space at `0x007DF972`, redirects only
the `java.util.resource.GameRef.getInfo(II)I` registration at `0x00489666`,
and leaves the String overload registration at `0x00489685` unchanged.
The clone's seven relative CALL instructions are recalculated to reach the
same original helper functions. Its seven absolute-address HIGHLOW relocation
entries are duplicated into a new PE base-relocation block. The original
dispatcher and all other methods remain unchanged.

This stage contains **no Bounding Box hook** and no special query handling.
The Java-side marker calls are intentionally absent. Its only purpose is to
check that the cloned dispatcher behaves like the original when used through
the (II)I registration. The Reflection log/dump writer suppression from
`native_silent_v2.exe` is retained.

Build from the exact known-good base (do not patch the previous probe):

```powershell
python .\\native\\patch_bbox_center.py "D:\\Games\\SLRR\\StreetLegal_Redline_native_silent_v2.exe" "D:\\Games\\SLRR-test\\StreetLegal_Redline_native_getinfo_clone_probe.exe"
```

First test garage and shop loading. Do not test a BBox query in this stage.
If the cloned dispatcher is stable, the next revision can add the reserved
query branch inside this private clone without affecting the String overload
or the original shared dispatcher.
