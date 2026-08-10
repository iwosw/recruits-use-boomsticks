# Сравнительный аудит `recruits_use_boomsticks`

## 1. Область и источники

Проверен рабочий снимок проекта `recruits use boomsticks` на ветке `master` с незакоммиченными изменениями. Контрольный upstream — Villager Recruits 1.15.2:

- репозиторий: `talhanation/recruits`;
- commit: `1cc10948f6e04fd146f9eb7fb84421033629dc1a` (`1.15.2`);
- временная копия upstream удалена после проверки и не является частью проекта.

Сравнение охватывает Mixins, жизненный цикл AI, выбор оружия, перезарядку/боеприпасы, strategic fire, friendly fire и release-проверки. Там, где поведение не запускалось в клиентском UI или на нескольких версиях зависимостей, это отмечено как ограничение, а не как подтверждённая совместимость.

## 2. Краткий вывод

Аддон не переписывает весь `CrossBowmanEntity`. Он добавляет отдельный server-side goal и adapter boundary для четырёх явно перечисленных предметов Medieval Boomsticks:

- `medieval_boomsticks:handgonne`;
- `medieval_boomsticks:spikedhandgonne`;
- `medieval_boomsticks:arquebus`;
- `medieval_boomsticks:arbalest`.

При отсутствии поддерживаемого предмета штатный Recruits AI остаётся основным путём. При наличии поддерживаемого предмета аддон отключает штатный crossbow goal, переключает предмет в main hand при необходимости и ведёт собственный цикл `IDLE → RELOAD → AIM → FIRE → COOLDOWN`.

Сильные стороны текущего снимка:

- физические round ball/heavy bolt потребляются целой очередью на этапе reload;
- состояние `ChargedProjectiles` синхронизируется с нативным состоянием Medieval Boomsticks;
- выстрел создаётся только на dedicated/server level и сохраняет owner recruit;
- friendly fire защищён одновременно в `AbstractArrow.canHitEntity` и Forge impact event;
- reload не отменяется появлением цели, а emergency movement имеет приоритет;
- есть 43 JUnit-теста и 16 Forge GameTests.

Главный подтверждённый release-риск: в конфиге и логировании заявлен `ARTILLERY_ADDON`, но в production registry зарегистрирован только `MedievalBoomsticksAdapter`. Реальной поддержки Artillery Addon в текущем снимке нет.

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

После чистой сборки Gradle обнаружены 8 JUnit XML-файлов:

- всего `43` теста;
- failures `0`;
- errors `0`;
- skipped `0`.

Покрыты pure policy/state, profiles, ammo access, adapter lookup, projectile policy и config. Это хорошие unit-level проверки, но они не заменяют полную игровую проверку с несколькими внешними версиями.

### Forge GameTest

Запущено:

```text
./gradlew runGameTestServer --console=plain --stacktrace
```

Результат — `BUILD SUCCESSFUL`. В `run/logs/latest.log` подтверждено:

- `16 tests are now running` (`run/logs/latest.log:66`);
- `All 16 required tests passed :)` (`run/logs/latest.log:74`);
- в лог попали Recruits `1.15.2`, Medieval Boomsticks `1.01`, GeckoLib `4.8.3`, Forge `47.4.20` (`run/logs/latest.log:34`).

GameTests фактически проверяют, в частности:

- reload до получения цели;
- продолжение reload после появления атакующего;
- замедление reload на mount;
- физический расход round balls/heavy bolts;
- spiked handgonne volley;
- native charged payload и очистку invalid payload;
- стратегический огонь и приоритет цели;
- friendly fire и owner hit;
- TNT emergency movement;
- независимость физического ammo requirement от upstream arrow setting и arbalest ballistic arc.

Лог также показывает ожидаемую нормализацию legacy state и предупреждения об очищенных invalid payload. Это не failure: все 16 GameTests завершились успешно.

## 5. Артефакт и release-проверка

Команда:

```text
./gradlew clean test build --warning-mode all --stacktrace
```

Результат: `BUILD SUCCESSFUL in 18s`, `14 actionable tasks`, compilation/test/jar/reobf прошли.

Собранный файл:

```text
build/libs/recruits_use_boomsticks-1.0.3.jar
size: 142690 bytes
sha256: b885406071dd20b159cf29ae52849ff0ebd5b476e0f0bd0a432d76d7c43c3049
```

Проверено наличие в JAR:

- `META-INF/mods.toml`;
- `recruits_use_boomsticks.mixins.json`;
- `recruits_use_boomsticks.refmap.json`;
- `icon.png`;
- `org/iwoss/recruits_use_boomsticks/gametest/BoomstickCompatibilityGameTests.class`.

В архиве: `34` class entries и `9` non-class resource entries.

`git diff --check` завершился с кодом `0`. Рабочее дерево намеренно не чистилось и не коммитилось: до создания этого отчёта в нём были пользовательские изменения `16` tracked-файлов и `2` untracked-файла. Коммит, push и переписывание истории не выполнялись.

## 6. Риски и приоритеты

### P0 — заявлена, но не реализована Artillery compatibility

`RecruitWeaponIntegration` содержит `ARTILLERY_ADDON` (`.../RecruitWeaponIntegration.java:3-7`), а config имеет `artilleryAddonEnabled=true` (`.../CompatConfig.java:18-24`). Однако production registry регистрирует только `MedievalBoomsticksAdapter` (`.../RecruitWeaponAdapters.java:11-15`). `mods.toml` также не объявляет `artillery_addon` dependency (`.../META-INF/mods.toml:30-70`). В GameTest log прямо указано `Artillery Addon=missing` (`run/logs/latest.log:34`).

Следствие: переключатель и лог создают ложное впечатление работающей интеграции, но код не поддерживает Artillery weapons/projectiles. До отдельного adapter implementation лучше считать эту интеграцию `planned`, отключить её по умолчанию или убрать из текущего release surface.

### P1 — precedence при смешанном инвентаре

Штатный Recruits crossbow goal подавляется уже при наличии boomstick в inventory, даже если в main hand сейчас обычный crossbow (`.../RecruitRangedCrossbowAttackGoalMixin.java:26-55`). Новый goal затем пытается переключить supported weapon (`.../RecruitBoomstickAttackGoal.java:345-353`). Это намеренная политика, но она может менять ожидаемое поведение при наличии одновременно vanilla crossbow, boomstick, musket и разных запасов ammo. Нужны отдельные GameTests для матрицы:

- ordinary crossbow в main hand + boomstick в inventory;
- musketmod weapon в main hand + boomstick в inventory;
- boomstick без ammo при наличии заряженного обычного crossbow;
- отключение Medieval integration при сохранённом boomstick в inventory.

### P1 — баллистика не доказана как parity с upstream

Upstream целится в eye height через свой crossbow path, а текущий `AimPoint.shotPosition()` для entity target использует `entity.getY(1.0D / 3.0D)` (`.../RecruitBoomstickAttackGoal.java:520-524`). Для arbalest дополнительно применяется вертикальная поправка `horizontalDistance * 0.2` (`.../MedievalBoomsticksAdapter.java:334-345`), а для spiked handgonne — spread `±10°` (`.../MedievalBoomsticksAdapter.java:321-332`). Это осознанные профили, но damage/trajectory parity на дистанциях и по разным hitboxes не измерялась. Нужен отдельный gameplay/physics test matrix.

### P1 — широкая версия зависимости Epic Knights

`mods.toml` допускает `magistuarmory` от `8.2` без верхней границы (`.../META-INF/mods.toml:51-56`), тогда как фактическая smoke-проверка прошла только с текущим runtime artifact. Если API Medieval Boomsticks использует Epic Knights classes, совместимость со всеми будущими версиями не следует считать подтверждённой. Лучше либо ограничить version range, либо иметь CI matrix.

### P2 — legacy charged state может дать бесплатную загрузку

Если у предмета есть `charged=true`, но отсутствует `ChargedProjectiles`, adapter создаёт ожидаемый native payload без повторного списания ammo (`.../MedievalBoomsticksAdapter.java:67-81`). Это полезная backwards-compat нормализация, но для malformed/legacy item может означать бесплатный volley. Нужно явно решить, является ли это допустимой миграцией или требуется списание/очистка.

### P2 — предупреждения toolchain

Сборка успешна, но выдаёт:

- deprecated Gradle/Forge resolution API;
- deprecated `FMLJavaModLoadingContext.get()` и `ModLoadingContext.get()` в `RecruitsUseBoomsticks.java:21-24`;
- в dev GameTest log reference map warning (`run/logs/latest.log:12`), при этом refmap присутствует в итоговом JAR.

Это не блокирует текущий release, но станет техническим долгом при обновлении Gradle/Forge.

## 7. Рекомендованный следующий порядок

1. Перед публикацией убрать несоответствие Artillery: реализовать полноценный adapter с tests или удалить/отключить неподдержанный switch и claims.
2. Добавить precedence GameTests для mixed inventory и optional musketmod.
3. Добавить дистанционные GameTests/ручной dedicated-server smoke для trajectory, damage, owner hit и ally hit.
4. Принять решение по legacy `charged=true` без payload и зафиксировать его в migration policy.
5. После этого обновить release notes, проверить зависимые версии и только затем публиковать артефакт.

## Итог

По подтверждённому upstream commit аддон уже реализует рабочий и хорошо изолированный путь для Medieval Boomsticks: сборка, 43 JUnit и 16 GameTests проходят, JAR содержит mixin/refmap и ожидаемые классы. Готовность нельзя называть полной для всех заявленных compatibility integrations из-за отсутствующего Artillery adapter и пока не доказанной parity баллистики/смешанного precedence. Для текущего заявленного scope `Recruits 1.15.2 + Medieval Boomsticks 1.01` состояние выглядит release-candidate, но не безусловно production-ready.
