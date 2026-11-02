# Native pivot / live AABB — no centroid

Ветка `diagnostic/bone-case-probe` теперь различает **native pivot экземпляра** и **геометрический центр меша**. Это разные координаты; их нельзя подменять друг другом.

## Что показали пружины

В обоих новых логах render-record translation Y равен raw DWORD `-1071826621`, то есть float `-2.45661998`. После первого render update движок записывает в `INSTANCE_RENDER.position.y` значение `-0.245661998` (translation × 0.1).

VStanced-пружина:
- локальный AABB Y: `[0.059, 2.417]`;
- INSTANCE_RENDER scale Y: `0.247296855`;
- native pivot Y: `-0.245662`;
- преобразованный диапазон геометрии: примерно `[-0.23107, 0.35205]`;
- преобразованный AABB midpoint: `0.06049`.

Стоковая пружина:
- локальный AABB Y: `[-0.075, 2.695]`;
- INSTANCE_RENDER scale Y: `0.277697474`;
- native pivot Y: `-0.245662`;
- преобразованный диапазон геометрии: примерно `[-0.26649, 0.50273]`;
- преобразованный AABB midpoint: `0.11812`.

Это согласуется с наблюдением, что у пружин native pivot расположен возле нижнего конца. AABB midpoint находится заметно выше и подходит как точка центрирования кадра, но не является native pivot.

## Native-функция

Проверена машинная реализация `0x5433E0`:
- при пустом auxiliary-list движок выбирает первый render node;
- иначе ищет строку `bone00` в списке;
- затем вызывает `0x541FE0`, получает matrix выбранного узла;
- translation matrix `m[12..14]` умножается на native scale factor `0.1`;
- результат записывается в `INSTANCE_RENDER +0x84..+0x8c`;
- scale записывается отдельно.

Таким образом, `INSTANCE_RENDER.position` — прямой native transform origin/pivot после инициализации, а не центр bounds.

## Диагностические оси

- `489..498` — дополнительный exact-case `Bone00` lookup и его 3×3 matrix.
- `500..502` — преобразованный midpoint live CPU-vertex AABB (геометрический центр).
- `510..512` — native `INSTANCE_RENDER.position XYZ`, fixed-point world units ×1000.

Centroid удалён: нет surface-centroid handler, triangle scan или cache. Запросы 503..505 не используются. Live AABB берётся по axes 0..5; axes 6..11 — старые loader bounds.

## Java timeline

`TRANSFORM_TIMELINE` теперь записывает XYZ:
- translation исходного render-record;
- translation native-выбранного node;
- INSTANCE_RENDER position и scale;
- native pivot (axes 510..512).

Первые четыре кадра записываются всегда, последующие изменения — до кадра 44. Камера пока продолжает использовать геометрический AABB midpoint по axes 500..502; переключать её на native pivot до проверки на других типах деталей рано.

## Центр камеры для составных деталей

Java-предпросмотр больше не ограничивается единственным `previewPart.getInfo(GII_RENDERINSTANCE)`.

При захвате центра он собирает:
- корневой `GII_RENDERINSTANCE`;
- прямых дочерних ResourceRef типа 3;
- дочерние ResourceRef типа 3 на один уровень глубже.

ID дедуплицируются. Для каждого найденного INSTANCE_RENDER запрашиваются axes 500..502 — уже преобразованный midpoint его live AABB. Камера получает середину минимального/максимального конверта этих component-centers. Для одной render-компоненты результат совпадает со старым axis 500..502; для сборки он учитывает несколько render-компонент.

**Ограничение текущего шага:** конверт строится по центрам компонент, а не по их полным min/max. Это осознанная промежуточная оценка, не точный union AABB всей сборки. В логе для каждой компоненты выводятся ID, центр или состояние PENDING. До получения валидных данных камера не устанавливает первую матрицу; опрос продолжается до 60 кадров, затем используются доступные центры либо прежний root-AABB fallback.

## Сборка

Новый диагностический EXE:
`StreetLegal_Redline_native_live_aabb_nativepivot_no_centroid.exe`

SHA-256:
`9a3b5f669a0b9c0b5bc0ef5645790b39c3b5e9bf6f3d24c91b0af06e591daab3`

База:
`StreetLegal_Redline_native_aabb_rendercenter_v2.exe`
SHA-256:
`ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`

Статическая PE/route/cave/patch-scope проверка пройдена. Runtime-проверка этого EXE в SLRR ещё не выполнена. Проверять только в отдельной копии игры.
