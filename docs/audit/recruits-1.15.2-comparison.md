# Сравнительный аудит `recruits_use_boomsticks`

> **Статус: обновлён 2026-08-13 под текущее состояние ветки `main`.** Первая редакция была
> написана до появления Artillery-адаптеров и утверждала, что поддержки Artillery Addon в проекте
> нет; это больше не так. Разделы 2, 4, 5, 6 и «Итог» переписаны под проверенное состояние,
> сопоставление с upstream в разделе 3 перепроверено и оставлено. Полное описание Artillery-границы
> живёт в `docs/compat/artillery-addon-compatibility-verification.md`; этот документ отвечает только
> на вопрос «как аддон соотносится с upstream Recruits».

## 1. Область и источники

Проверен рабочий снимок проекта `recruits use boomsticks` на ветке `main`. Контрольный upstream — Villager Recruits 1.15.2:

- репозиторий: `talhanation/recruits`;
- commit: `1cc10948f6e04fd146f9eb7fb84421033629dc1a` (`1.15.2`);
- временная копия upstream удалена после проверки и не является частью проекта.

Сравнение охватывает Mixins, жизненный цикл AI, выбор оружия, перезарядку/боеприпасы, strategic fire, friendly fire и release-проверки. Там, где поведение не запускалось в клиентском UI или на нескольких версиях зависимостей, это отмечено как ограничение, а не как подтверждённая совместимость.

## 2. Краткий вывод

Аддон не переписывает весь `CrossBowmanEntity`. Он добавляет отдельный server-side goal и три adapter boundary, каждый из которых работает по явному списку registry ID:

- `MedievalBoomsticksAdapter` — `medieval_boomsticks:handgonne`, `spikedhandgonne`, `arquebus`, `arbalest`;
- `ArtilleryAddonAdapter` — 17 профилей огнестрела Artillery Addon: 13 подтверждены в рантайме на закреплённом 1.11, остальные 4 (`tiller_gun`, `noble_handgonne`, `hackbut`, `double_barrel_gonne`) — на 1.14.0, где эти предметы зарегистрированы;
- `ArtilleryThrowableAdapter` — 5 метательных Artillery (`francisca`, `hurlbat`, `throwing_cross`, `javelin`, `throwable_cobblestone`), все подтверждены в рантайме.

Гранаты Artillery (`clay_hand_grenade`, `iron_hand_grenade`, `fire_bomb`, `lime_bomb`) исключены по решению: их impact-цепочка вызывает `Level.explode(null, ...)`, взрыв без owner, поэтому защита союзников по ownership на него не распространяется.

При отсутствии поддерживаемого предмета штатный Recruits AI остаётся основным путём. При наличии поддерживаемого предмета аддон отключает штатный crossbow goal, переключает предмет в main hand при необходимости и ведёт собственный цикл `IDLE → RELOAD → AIM → FIRE → COOLDOWN`.

Сильные стороны текущего снимка:

- физические round ball/heavy bolt/iron ball/arrow потребляются на этапе reload, независимо от upstream-настройки `RangedRecruitsNeedArrowsToShoot`;
- состояние `ChargedProjectiles` синхронизируется с нативным состоянием Medieval Boomsticks, а нативные NBT-цепочки Artillery воспроизводятся пошагово (`ArtilleryReloadProtocol`);
- метательное оружие не получает выдуманного loaded-состояния: один предмет списывается только после того, как нативный projectile принят сервером;
- выстрел создаётся только на dedicated/server level и сохраняет owner recruit;
- friendly fire защищён одновременно в `AbstractArrow.canHitEntity` и Forge impact event;
- reload не отменяется появлением цели, а emergency movement имеет приоритет;
- 117 JUnit-тестов и 106 Forge GameTests, прогнаны 2026-08-13 (см. раздел 4).

Заявленный в конфиге `ARTILLERY_ADDON` теперь соответствует коду: `RecruitWeaponAdapters.production()` регистрирует оба Artillery-адаптера рядом с `MedievalBoomsticksAdapter`. Прежний P0 закрыт.

## 3. Сопоставление с upstream

| Область | Upstream Recruits 1.15.2 | Текущий аддон | Оценка |
|---|---|---|---|
| Регистрация AI | `CrossBowmanEntity` регистрирует optional musket goal и штатный `RecruitRangedCrossbowAttackGoal` на priority `0`; движение к цели — priority `8` (`upstream .../CrossBowmanEntity.java:93-100`). | Mixin в `registerGoals` добавляет combat goal на priority `0` и passive-reload goal на priority `1` (`.../CrossBowmanEntityMixin.java:18-23`). | Интеграция точечная, но priority `0` требует проверки конфликтов с musket goal. |
| Отключение штатного crossbow AI | Штатный goal работает с обычным `CrossbowItem`, ищет crossbow в main hand и использует штатную конфигурацию стрел (`upstream .../RecruitRangedCrossbowAttackGoal.java:31-62`). | Штатный goal подавляется, если поддерживаемый boomstick находится в main hand **или просто в inventory** (`.../RecruitRangedCrossbowAttackGoalMixin.java:22-55`). | Предсказуемое предпочтение boomstick, но это поведение меняется даже до фактического переключения оружия. |
| Выбор оружия | Upstream переключает предметы по predicate обычного crossbow (`upstream .../RecruitRangedCrossbowAttackGoal.java:80-91`). | Выбор делегирован `RecruitWeaponAdapters`; предметы определяются по стабильному registry ID, а не по имени/локализации (`.../SupportedBoomsticks.java:44-76`, `.../RecruitWeaponAdapters.java:30-69`). | Хорошая защита от случайного захвата чужих предметов и удобная точка расширения. |
| Pickup | Штатная логика Recruits решает, какие предметы crossbowman подбирает. | Mixin дополнительно принимает только поддержанные boomstick weapons и ammo при включённой интеграции (`.../CrossBowmanEntityMixin.java:25-40`). | Обычные предметы не затрагиваются. |
| Reload | Upstream запускает штатное состояние crossbow/musket; для crossbow количество стрел определяется `RangedRecruitsNeedArrowsToShoot` (`upstream .../RecruitRangedCrossbowAttackGoal.java:126-144`, `RecruitsServerConfig.java:303-314`). | Passive reload запускается даже без цели; появление атакующей цели не отменяет уже идущий reload (`.../RecruitBoomstickAttackGoal.java:91-129`, `:269-296`). | Поведение лучше соответствует NPC-оружию с долгим reload. |
| Ammo | В штатном crossbow пути используется vanilla projectile/`CrossbowWeapon`; upstream расходует стрелу в момент выстрела. | Round ball/heavy bolt обязательны для boomsticks независимо от upstream arrow setting; для spiked handgonne расходуется три единицы на volley (`.../BoomstickAmmoAccess.java:14-21`, `:38-78`; `.../SupportedBoomsticks.java:116-146`). | Семантика явно документирована и покрыта тестами. |
| Нативное loaded state | Штатный crossbow владеет своим vanilla payload. | Adapter валидирует `ChargedProjectiles`, нормализует legacy `charged=true` без payload и очищает повреждённый payload (`.../MedievalBoomsticksAdapter.java:67-104`). | Хорошая совместимость с нативным item state; legacy-нормализация требует отдельного баланса/решения. |
| Выстрел | Upstream использует штатный `CrossbowWeapon`, vanilla arrow, обычную vanilla ballistics и текущие правила crossbow (`upstream .../compat/musketmod/CrossbowWeapon.java`). | Adapter создаёт `RoundBallProjectile` или `HeavyBoltProjectile`, назначает owner, задаёт pickup policy, создаёт все projectiles на server level и откатывает уже созданные entities при ошибке (`.../MedievalBoomsticksAdapter.java:153-255`). | Server-safe и лучше изолировано от player-only item use; полная баллистическая parity с upstream не подтверждена. |
| Strategic fire | Upstream стреляет по strategic position, когда нет конкретной цели; при target behind cover штатная логика может сбросить цель (`upstream .../RecruitRangedCrossbowAttackGoal.java:48-58`, `:126-144`). | `findAimPoint` сначала сохраняет валидную hostile target; strategic position выбирается только если валидной цели нет (`.../RecruitBoomstickAttackGoal.java:442-492`). | Цель имеет приоритет над strategic fire; это соответствует заявленной политике аддона. |
| Движение и команды | Upstream учитывает follow/hold/wander, `needsToGetFood`, `getShouldMount` и собственные stop ranges (`upstream .../RecruitRangedCrossbowAttackGoal.java:93-124`). | Новый combat goal управляет MOVE+LOOK, подходит к цели без LOS или за пределами 45 блоков, а hold/move position проверяются отдельно (`.../RecruitBoomstickAttackGoal.java:82-84`, `:428-439`, `:495-516`). | Сценарии hold/move покрыты policy/GameTests; все upstream command gates не продублированы один-в-один. |
| Emergency movement | У upstream есть `FleeTNT` и `FleeFire` на priority `1` (`upstream .../AbstractRecruitEntity.java:332-348`); `FleeTNT` выставляет fleeing state и отводит recruit. | Combat/passive goals прекращаются при `getFleeing()` или nearby `PrimedTnt` (`.../RecruitBoomstickAttackGoal.java:411-425`). | Защита от блокировки emergency goals есть и проверена GameTest для TNT. |
| Friendly fire | Upstream обычный crossbow опирается на vanilla/Recruits target rules. | Для recruit-owned supported projectiles добавлены `AbstractArrow.canHitEntity` guard и Forge `ProjectileImpactEvent`; owner, allies и `canAttack` исключаются (`.../AbstractArrowMixin.java:25-48`, `.../BoomstickProjectileEvents.java:28-55`). | Двойной server-side guard; player-owned/неподдержанные projectiles не меняются. |
| Cleanup projectile | Штатные projectiles живут по своим правилам. | Поддержанные recruit-owned projectiles удаляются после 200 ticks, если они не должны оставаться pickup-able в земле (`.../AbstractArrowMixin.java:50-69`). | Снижает накопление entities; отдельный lifetime tuning не вынесен в config. |

## 4. Что действительно покрыто тестами

### JUnit

После чистой сборки Gradle обнаружены 14 JUnit XML-файлов:

- всего `117` тестов;
- failures `0`;
- errors `0`;
- skipped `0`.

Покрыты pure policy/state, профили Medieval Boomsticks и Artillery, нативные loading-цепочки (`ArtilleryReloadProtocolTest` проходит каждую захваченную цепочку до её нативного loaded-состояния), профили метательного оружия (`ArtilleryThrowableAdapterTest` — окно use, damage, velocity, inaccuracy, recovery roll), ammo access, adapter lookup, projectile policy и config. Это хорошие unit-level проверки, но они не заменяют полную игровую проверку с несколькими внешними версиями.

### Forge GameTest

Запущено:

```text
./gradlew.bat runGameTestServer --console=plain
```

Результат — `BUILD SUCCESSFUL`. В `run/logs/latest.log` подтверждено:

- `106 tests are now running!`;
- `All 106 required tests passed :)`.

GameTests фактически проверяют, в частности:

- reload до получения цели, продолжение reload после появления атакующего, замедление reload на mount;
- физический расход round balls/heavy bolts/iron balls/arrows и spiked handgonne volley;
- native charged payload Medieval Boomsticks и очистку invalid payload;
- нативные многошаговые цепочки Artillery: powder/ball/ramrod со ступенями `stage`, borrow-возврат инструмента в off hand, отказ начинать цепочку без ramrod;
- магазин Chu Ko Nu на восемь патронов и отсутствие лишней перезарядки между выстрелами;
- все пять метательных: тип нативного projectile, списание ровно одного предмета, ownership, отказ по союзнику, handoff в combat goal без входа в reload;
- стратегический огонь и приоритет цели, friendly fire и owner hit, TNT emergency movement;
- оба kill-switch'а совместимости.

Оговорка по числу `106`: на закреплённом 1.11 восемь тестов (`tiller_gun`, `noble_handgonne`, `hackbut`, `double_barrel_gonne`) завершаются ранним `succeed()`, то есть реально исполняется около 98. Эти восемь прогоняются отдельно на `runGameTestServer -Partillery=1.14` с dev-only патчем — там все 106 исполняются полностью.

Лог также показывает ожидаемую нормализацию legacy state и предупреждения об очищенных invalid payload, а также апстримные ошибки разбора двух рецептов Artillery, ссылающихся на отсутствующий namespace `magistuarmoryaddon`. Это не failures: все 106 GameTests завершились успешно.

## 5. Артефакт и release-проверка

Команда:

```text
./gradlew.bat -Dnet.minecraftforge.gradle.check.certs=false clean build --console=plain
```

Результат: `BUILD SUCCESSFUL`, `14 actionable tasks: 14 executed`, compilation/test/jar/reobf прошли. Override сертификатов нужен потому, что канонический прогон останавливается на проверке сертификата `libraries.minecraft.net` в ForgeGradle.

Собранный файл:

```text
build/libs/recruits_use_boomsticks-1.0.3.jar
size: 277470 bytes
sha256: a550123641756c18a0ba19321ff6084c09361c3310a18c6e73ee011f09d59611
```

Проверено наличие в JAR:

- `META-INF/mods.toml`;
- `recruits_use_boomsticks.mixins.json`;
- `recruits_use_boomsticks.refmap.json`;
- `icon.png`;
- `org/iwoss/recruits_use_boomsticks/gametest/BoomstickCompatibilityGameTests.class`.

В архиве: `71` class entry и `11` non-class resource entries. Вложенных dependency-JAR и скопированных классов Artillery нет; `artillery_addon` вообще не объявлен в `mods.toml`, поэтому интеграция остаётся опциональной и отключается сама при отсутствии мода.

## 6. Риски и приоритеты

### P0 (закрыт) — Artillery compatibility реализована

Прежний P0 звучал так: `ARTILLERY_ADDON` заявлен в конфиге, но в production registry зарегистрирован только `MedievalBoomsticksAdapter`. Это исправлено: `RecruitWeaponAdapters.production()` регистрирует `MedievalBoomsticksAdapter`, `ArtilleryAddonAdapter` и `ArtilleryThrowableAdapter`, каждый со своими профилями, нативными цепочками загрузки и GameTests. Пункт закрыт.

### P1 (закрыт) — четыре ствола Artillery прогнаны в рантайме

`tiller_gun`, `noble_handgonne`, `hackbut` и `double_barrel_gonne` больше не зависят только от байткода. Причина падения 1.14.0 на dedicated server найдена: `GunMaker$Events` помечен голым `@Mod.EventBusSubscriber` и слушает клиентский `RenderPlayerEvent.Pre` без `@OnlyIn(CLIENT)`, поэтому Forge грузит класс на сервере и упирается в `ClientLevel`. Пакета `gun_maker` в 1.11 нет вообще — отсюда и разница между артефактами. Dev-only mixin-конфиг снимает эти два слушателя, и `runGameTestServer -Partillery=1.14` проходит все 106 тестов, причём четыре оружия исполняются по-настоящему. В релизный JAR патч не попадает.

Прогон сразу окупился: два теста из этой четвёрки оказались протухшими (Tiller Gun требовал отсутствия `loaded`, Noble Handgonne — наличия), адаптеры были правы. Гейтнутый тест проходит, ничего не проверяя, — это и есть его цена.

### P1 — клиентская часть без автоматических тестов

Позы рук (`BoomstickArmPose` и mixins на `RecruitVillagerRenderer`/human-модель), включая wind-up метательного оружия, проверялись только плейтестом: GameTest server туда не достаёт. Регрессия в рендере не будет поймана существующим набором тестов.

### P1 — precedence при смешанном инвентаре

Штатный Recruits crossbow goal подавляется уже при наличии boomstick в inventory, даже если в main hand сейчас обычный crossbow (`.../RecruitRangedCrossbowAttackGoalMixin.java:26-55`). Новый goal затем пытается переключить supported weapon (`.../RecruitBoomstickAttackGoal.java:345-353`). Это намеренная политика, но она может менять ожидаемое поведение при наличии одновременно vanilla crossbow, boomstick, musket и разных запасов ammo. Нужны отдельные GameTests для матрицы:

- ordinary crossbow в main hand + boomstick в inventory;
- musketmod weapon в main hand + boomstick в inventory;
- boomstick без ammo при наличии заряженного обычного crossbow;
- отключение Medieval integration при сохранённом boomstick в inventory.

### P1 — баллистика не доказана как parity с upstream

Upstream целится в eye height через свой crossbow path, а текущий `AimPoint.shotPosition()` для entity target использует `entity.getY(1.0D / 3.0D)` (`.../RecruitBoomstickAttackGoal.java:652-656`). Поверх этой точки Artillery-адаптер добавляет компенсацию падения `g/2 * t²` с потолком в восемь блоков (`ArtilleryAddonAdapter.aimVector`), а дальность каждого оружия обрезается обратной функцией этого потолка (`maxCompensatedRange` + `BoomstickCombatPolicy.clampCombatRange`). Для arbalest применяется вертикальная поправка `horizontalDistance * 0.2`, для spiked handgonne — spread `±10°`. Компенсация падения сверялась с замером в игре, но damage/trajectory parity на разных дистанциях и hitboxes не измерялась. Нужен отдельный gameplay/physics test matrix.

### P1 — широкая версия зависимости Epic Knights

`mods.toml` допускает `magistuarmory` от `8.2` без верхней границы (`.../META-INF/mods.toml:51-56`), тогда как фактическая smoke-проверка прошла только с текущим runtime artifact. Если API Medieval Boomsticks использует Epic Knights classes, совместимость со всеми будущими версиями не следует считать подтверждённой. Лучше либо ограничить version range, либо иметь CI matrix.

### P2 — привязка к именам классов Artillery

`SupportedArtilleryThrowables` держит имена нативных entity-классов строкой (`net.mcreator.artilleryaddon.entity.*`), а режим подбора снаряда резолвится рефлексией по объявленным методам (`isStuckInGround` в 1.14 против собственного `onHitBlock` в 1.11). Переупаковка апстрима сломает распознавание. Граница fail closed — оружие просто перестанет использоваться, — и с 2026-08-13 оба нерезолвящихся случая пишут предупреждение в лог по одному разу (`ArtilleryThrowableAdapter.reportProjectileFailure`, fallback в `artifactUsesVanillaGroundPickup`), так что это больше не тихий отказ. Устойчивее было бы опознавать классы по registry ID entity-типа, но нативные различия в поведении подбора всё равно требуют проверки формы класса.

### P2 — legacy charged state может дать бесплатную загрузку

Если у предмета есть `charged=true`, но отсутствует `ChargedProjectiles`, adapter создаёт ожидаемый native payload без повторного списания ammo (`.../MedievalBoomsticksAdapter.java:67-81`). Это полезная backwards-compat нормализация, но для malformed/legacy item может означать бесплатный volley. Нужно явно решить, является ли это допустимой миграцией или требуется списание/очистка.

### P2 — предупреждения toolchain

Сборка успешна, но выдаёт:

- deprecated Gradle/Forge resolution API;
- deprecated `FMLJavaModLoadingContext.get()` и `ModLoadingContext.get()` в `RecruitsUseBoomsticks.java:21-24`;
- в dev GameTest log reference map warning (`run/logs/latest.log:12`), при этом refmap присутствует в итоговом JAR.

Это не блокирует текущий release, но станет техническим долгом при обновлении Gradle/Forge.

## 7. Рекомендованный следующий порядок

1. Добавить precedence GameTests для mixed inventory и optional musketmod.
2. Добавить дистанционные GameTests/ручной dedicated-server smoke для trajectory, damage, owner hit и ally hit.
3. Принять решение по legacy `charged=true` без payload и зафиксировать его в migration policy.
4. Отправить апстриму баг по `GunMaker$Events` (`value = Dist.CLIENT` на `@Mod.EventBusSubscriber`), чтобы 1.14 стартовал на сервере без чужих патчей.
5. Ограничить version range `magistuarmory` или завести CI matrix.
6. После этого обновить release notes, проверить зависимые версии и только затем публиковать артефакт.

## Итог

Аддон реализует рабочий и хорошо изолированный путь для Medieval Boomsticks и для Artillery Addon: сборка, 117 JUnit и 106 GameTests проходят, JAR содержит mixin/refmap и ожидаемые классы, обе интеграции отключаются своими kill-switch'ами и сами уходят в no-op при отсутствии мода. Прежний P0 (заявленная, но отсутствующая Artillery-интеграция) закрыт.

Полной готовность назвать всё ещё нельзя: клиентские позы держатся на плейтесте, parity баллистики и матрица precedence при смешанном инвентаре не измерялись, а Artillery 1.14 на dedicated server держится на dev-патче чужого бага и потому не заявляется как поддерживаемая конфигурация. Для заявленного scope — `Recruits 1.15.2 + Medieval Boomsticks 1.01` плюс опциональный Artillery Addon 1.11 — состояние release-candidate: публиковать можно, но перечисленные границы должны оставаться в release notes, а не исчезать из них.
