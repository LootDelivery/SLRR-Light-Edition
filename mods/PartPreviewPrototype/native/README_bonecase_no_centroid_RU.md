# Bone00 / live AABB diagnostics — no centroid

Ветка `diagnostic/bone-case-probe` содержит read-only native probe:

- axes 489..498 — поиск точного inline-имени `Bone00` и девять элементов его 3x3 matrix;
- axes 500..502 — transform-aware центр вложенного меша в мировых координатах, signed fixed-point *1000;
- axes 294..318 — диагностика lowercase `bone00`;
- axes 273..293 — auxiliary-list state и первый render node.

## Источник AABB

Native-центр теперь получает шесть координат через AABB helper axes **0..5** — это live CPU-vertex bounds. Старые axes **6..11** читают AABB, сохранённый загрузчиком SCX; helper прямо отмечает, что эти значения не обновляются при `scaleMesh()`. Поэтому использовать axes 6..11 как актуальную границу runtime-меша было ошибкой.

После получения live AABB его midpoint по-прежнему проходит через INSTANCE_RENDER scale, выбранную native render-node matrix и INSTANCE_RENDER position. Формула world transform не менялась.

## Отложенная инициализация render-transform

В логах обеих пружин видно, что при создании матрица `bone00` ещё identity, а позже её Y-перенос становится `-2.45662`. Это не нужно прибавлять к центру повторно.

Разбор native `0x5433E0` показывает последовательность: сначала вызывается `0x541FE0`, затем native-код берёт translation-компоненты выбранной 4x4 matrix, умножает их на константу `0.1` (`0x3DCCCCCD`) и записывает в `INSTANCE_RENDER +0x84..+0x8c`. Поэтому `-2.45662 * 0.1 = -0.245662` уже находится в INSTANCE_RENDER position; центр учитывает его через позицию экземпляра.

Числа из spring-проб это подтверждают:

- VStanced: локальный AABB center Y = `1.238`; scale Y = `0.24729685`; position Y = `-0.245662`; итог `1.238 * 0.24729685 - 0.245662 = 0.06049`.
- Stock: локальный AABB center Y = `1.310`; scale Y = `0.27769747`; та же position Y = `-0.245662`; итог `1.310 * 0.27769747 - 0.245662 = 0.11812`.

Следовательно, `0.060` и `0.118` — не ошибочные локальные центры и не центроид: это уже преобразованные world-space центры. В этих двух тестах live AABB axes 0..5 и loader axes 6..11 также совпали.

В Java добавлен ограниченный `TRANSFORM_TIMELINE`: первые четыре кадра и любые последующие изменения четырёх полей (`recordTyRaw`, `bone00TyRaw`, `instancePosYRaw`, `instanceScaleYRaw)) до 45-го кадра. Он нужен, чтобы точно зафиксировать момент, когда render-instance получает окончательную трансформацию и пружина визуально смещается вниз.

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