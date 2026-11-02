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
- `513..515` — `INSTANCE_RENDER.scale XYZ`, fixed-point ×1000.
- `516` — указатель на следующий узел native bone-list после первого.
- `517..518` — два DWORD имени второго узла.
- `519..534` — полная матрица второго узла, 16 DWORD float bits.
- `535` — указатель на следующий узел после второго (через тот же native helper).

**Важное ограничение по skinning:** AABB helper проходит по VertexData и читает только три координаты XYZ из position attribute. Он не применяет веса вершины и не смешивает матрицы нескольких костей. Поэтому axes `500..502` пока являются центром AABB исходных CPU-позиций, преобразованным через выбранный первый node и INSTANCE_RENDER scale; для skinned/deformed mesh это ещё не гарантированно центр видимой геометрии. Новые axes `516..535` — read-only диагностика второго узла, они пока не меняют расчёт центра.

## Фит камеры по фактической геометрии

Для каждой найденной render-компоненты Java:
1. читает transformed live-AABB center через `500..502`;
2. получает ID вложенного `RESOURCE_MESH` через axis `439`;
3. читает live local mesh AABB по осям `0..5`;
4. получает scale по осям `513..515`;
5. строит консервативный bounding-sphere radius из scaled half-diagonal.

Центр предпросмотра вычисляется по общему конверту сфер компонент. Камерный размер становится не меньше максимального расстояния от общего центра до каждой компоненты плюс её радиус. Это исправляет прежнюю проблему, когда `GII_SIZE` оставался маленьким при фактически длинной/смещённой геометрии (например, приводная цепь, ремень, пайп, сборка из нескольких мешей).

Если native render instance отсутствует, остаётся fallback: direct Part mesh AABB extent / 2 и прежний `GII_SIZE`. Это не решает отсутствие самого рендера у деталей без render instance, но не оставляет камеру на заведомо слишком короткой дистанции.

**Ограничение:** bounding sphere консервативен и может дать немного больший отступ, чем точный union AABB. Вращение render-record не меняет радиус сферы; центр по-прежнему берётся из native transformed-AABB center. Сложные render-record с собственной трансляцией требуют отдельной проверки.

Centroid удалён: нет surface-centroid handler, triangle scan или cache. Запросы 503..505 не используются. Live AABB берётся по axes 0..5; axes 6..11 — старые loader bounds.

## Java timeline и камера

`TRANSFORM_TIMELINE` записывает исходный render-record translation, native-selected node translation, INSTANCE_RENDER position/scale и native pivot (axes 510..512). Первые четыре кадра записываются всегда, последующие изменения — до кадра 44.

Камера обходит корневой `GII_RENDERINSTANCE`, прямых дочерних и дочерних на один уровень глубже. Обход выполнен обычными `ResourceRef`-циклами **без Java-массивов**: старый SLRR script parser неверно разбирает `int[]` в сигнатурах и вызовах методов.

Для каждой render-компоненты:
1. axes 500..502 дают transformed live-AABB center;
2. axis 439 даёт ID вложенного `RESOURCE_MESH`;
3. mesh axes 0..5 дают live local AABB;
4. axes 513..515 дают INSTANCE_RENDER scale;
5. сумма трёх scaled half-extents используется как консервативный bounding-sphere radius — без `Math.sqrt`, для совместимости со старым JVM.

Общие границы сфер дают центр сборки и консервативный радиус. `modelSize` увеличивается относительно `GII_SIZE`, когда фактические bounds требуют большего отступа. Если компонента ещё не готова, камера ждёт до 60 кадров; в trace выводится первый и последний опрос.

Если native render instance отсутствует, используется root-AABB fallback; для дистанции берётся не меньше половины максимального direct Part mesh extent. Отсутствие самого рендера у ремня этим не исправляется.

**Ограничение:** радиус намеренно консервативен. Центр компоненты использует native transformed-AABB center; отдельные render-record с собственной трансляцией требуют отдельной проверки.

## Сборка

Текущий диагностический EXE:
`StreetLegal_Redline_native_live_aabb_component_scale.exe`

SHA-256:
`f94756966b54bbd3728007d1f93e7e8c0cef3fc0b04e53052840e551eaa743ec`

База:
`StreetLegal_Redline_native_aabb_rendercenter_v2.exe`
SHA-256:
`ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8`

Для пересборки используется `build_bonecase_component_fit.py` и `bone_upper_query.S`.
Статическая PE/route/cave/patch-scope проверка пройдена. Runtime-проверка нового EXE в SLRR ещё не выполнена. Проверять только в отдельной копии игры.
