# Bone00 / nested AABB diagnostics — no centroid

Ветка `diagnostic/bone-case-probe` содержит read-only native probe:

- axes 489..498 — поиск точного inline-имени `Bone00` и девять элементов его 3x3 matrix;
- axes 500..502 — transform-aware AABB center, signed fixed-point *1000;
- axes 294..318 — существующая диагностика lowercase `bone00`;
- axes 273..293 — auxiliary-list state и первый render node.

**Surface centroid удалён.** В Java нет запросов 503..505; в диагностическом EXE нет centroid dispatcher, triangle scan или cache. Камерный центр продолжает браться только из 500..502.

Текущий контрольный EXE:
`StreetLegal_Redline_native_aabb_rendercenter_v2.exe`
SHA-256: `ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`

Для воспроизводимой сборки full dispatcher из исходников этой папки нужен ранее проверенный payload base EXE с SHA-256:
`38e09d0dbdd1c8d8afa1f3d0365b8a629b91e0fa8968c91ec53ccbc69bacdc88`

Сборочный скрипт `build_rendercenter_diag.py` проверяет SHA, сигнатуру detour, хэш старого dispatcher и доступный zero-filled .reloc cave. Он не применяет centroid-код.

Важная семантика: если native query возвращает `0x80000000`, SLRR может некорректно преобразовать этот signed int в строку. Java-логгер этой ветки теперь явно пишет `INVALID` для bone lookup sentinel.

Статическая проверка EXE не заменяет runtime-проверку в SLRR. Новый EXE из чата пока не проверен в самой игре.
