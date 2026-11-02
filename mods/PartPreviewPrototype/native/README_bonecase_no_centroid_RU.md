# Bone00 / live AABB diagnostics — no centroid

Ветка `diagnostic/bone-case-probe` содержит read-only native probe:

- axes 489..498 — поиск точного inline-имени `Bone00` и девять элементов его 3x3 matrix;
- axes 500..502 — transform-aware центр вложенного меша в мировых координатах, signed fixed-point *1000;
- axes 294..318 — диагностика lowercase `bone00`;
- axes 273..293 — auxiliary-list state и первый render node.

## Источник AABB

Native-центр теперь получает шесть координат через AABB helper axes **0..5** — это live CPU-vertex bounds. Старые axes **6..11** читают AABB, сохранённый загрузчиком SCX; helper прямо отмечает, что эти значения не обновляются при `scaleMesh()`. Поэтому использовать axes 6..11 как актуальную границу runtime-меша было ошибкой.

После получения live AABB его midpoint по-прежнему проходит через INSTANCE_RENDER scale, выбранную native render-node matrix и INSTANCE_RENDER position. Формула world transform не менялась.

## Центроид

**Surface centroid удалён.** В native dispatcher нет triangle scan или centroid cache; axes 503..505 не используются. В Java `BuyPartPreviewDialog.java` камера читает только axes 500..502 и не вызывает 503..505.

## Сборка и проверка

Сборочный источник — `render_diag_dispatch.S`; скрипт — `build_rendercenter_diag.py`. Оба обновлены под live AABB axes 0..5.

Отдельная сборка, подготовленная в текущем чате:
- `StreetLegal_Redline_native_live_aabb_bonecase_no_centroid.exe`
- SHA-256: `32dee2997a0b23a6e8e35ea038ae1864d00d167d2cda129b2a7b414f439f2c0b`

Она прошла статическую проверку PE/адресов/изменённых байтов. **Runtime-проверка в самой SLRR ещё не выполнена.** Проверять только в отдельной копии игры.

Контрольный base EXE для этого отдельного артефакта:
`StreetLegal_Redline_native_aabb_rendercenter_v2.exe`
SHA-256: `ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`

Другой воспроизводимый pipeline этой ветки использует payload base EXE с SHA-256:
`38e09d0dbdd1c8d8afa1f3d0365b8a629b91e0fa8968c91ec53ccbc69bacdc88`

Статическая проверка не заменяет runtime-тест.