## Native getInfo routing probe — isolated registration test

The earlier BBox probe builds (v1–v3) hooked the shared dispatcher body at
`0x0047DEDD`. They all caused the garage-load crash and must no longer be used.

This probe takes a narrower route. It redirects only the JNI registration of
`java.util.resource.GameRef.getInfo(II)I` (registration code at
`0x00489666`) to a five-byte pass-through thunk at `0x007DF972`. The thunk
contains only a relative JMP to the original dispatcher at `0x0047DDF0`.
The overloaded `getInfo(ILjava.lang.String;)I` registration remains unchanged.
No shared dispatcher call site is modified; no arguments, flags, or object
memory are inspected by the thunk. The working Reflection log/dump suppression
from `native_silent_v2.exe` is retained.

Build a separate executable from the exact known-good base:

```powershell
python .\\native\\patch_bbox_center.py "D:\\Games\\SLRR\\StreetLegal_Redline_native_silent_v2.exe" "D:\\Games\\SLRR-test\\StreetLegal_Redline_native_getinfo_route_probe.exe"
```

This stage is only a routing safety test. It does not query the Bounding Box,
and the Java-side experimental marker calls have been removed. First validate
normal garage and shop loading. Only after this registration-only route is
stable should the thunk inspect the two Java integer arguments and handle the
reserved BBox query.
