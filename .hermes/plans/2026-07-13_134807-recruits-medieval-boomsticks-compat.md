# Recruits × Medieval Boomsticks Compatibility Layer — Implementation Plan

> **Git checkpoint rule:** After every completed implementation step, run the narrowest relevant verification, review the diff, and create a Git commit before starting the next step. Do not combine unrelated steps in one commit.

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Do not modify the source code or JAR files of Recruits or Medieval Boomsticks. All integration must live in this compatibility mod.

**Goal:** Build a standalone Forge 1.20.1 compatibility mod that lets Recruits crossbowmen equip, pick up, reload, aim, and fire Medieval Boomsticks weapons while preserving Recruits commands, inventory rules, targeting, ownership, and friendly-fire behavior.

**Architecture:** The mod is a third-party compatibility layer. It adds a dedicated boomstick combat goal to `CrossBowmanEntity` through Mixins, suppresses Recruits' vanilla crossbow goal only while a Medieval Boomsticks weapon is active, and delegates weapon-specific behavior to an adapter owned by this mod. It does not patch, replace, redistribute, or edit either dependency. Projectile creation is server-authoritative and implemented in the adapter rather than calling Medieval Boomsticks' player-only fire path.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.x, ForgeGradle 6, Sponge Mixin 0.8.5, Recruits 1.15.2, Medieval Boomsticks 1.01, GeckoLib 4, Forge GameTest, JUnit 5 for pure state-machine tests.

## 0. Implementation progress

The following work has been completed before the next checkpoint:

- **Step 1 — workspace preservation:** the original staged baseline and unstaged `build.gradle` experiment were preserved under `.hermes/recovery/`; no reset, clean, restore, checkout, or commit was performed during preservation.
- **Step 2 — Forge/build normalization:** the project now uses Forge `1.20.1-47.4.20`, one `recruits_use_boomsticks` identity, property expansion for `mods.toml`, Mixin refmap configuration, pinned Recruits `1.15.2`, Medieval Boomsticks `1.01`, and GeckoLib `4.8.3` dependencies.
- **Step 3 — minimal bootstrap:** the generated MDK entry point/config behavior was replaced by `RecruitsUseBoomsticks` and `config/CompatConfig`, with no common-side client imports and four compatibility config options.
- **Step 4 — resource catalog groundwork:** item tags for supported weapons, round balls, and heavy bolts were added. The Java catalog/profile layer remains to be implemented.
- **Verification completed:** `./gradlew processResources`, `./gradlew dependencies --configuration compileClasspath`, `./gradlew compileJava`, and `./gradlew test` completed successfully. The initial config test was corrected to inspect Forge defaults via `getDefault()` rather than reading an unloaded config.

The repository has not yet received the first implementation commit. The next checkpoint must commit the completed changes before continuing with the Java catalog/profile implementation.

---

## 1. Confirmed repository and dependency facts

### Current project state

- The repository started as a Forge MDK skeleton with no commits. The first implementation checkpoint is now commit `68c9279` (`build: normalize Forge project and add compat bootstrap`) and is pushed to the public repository: https://github.com/iwosw/recruits-use-boomsticks
- The active entry point is `src/main/java/org/iwoss/recruits_use_boomsticks/RecruitsUseBoomsticks.java`.
- The focused common configuration is `src/main/java/org/iwoss/recruits_use_boomsticks/config/CompatConfig.java`.
- The working `build.gradle` is normalized to one project identity and references the existing `recruits_use_boomsticks.mixins.json` resource.
- `mods.toml` placeholder expansion is restored and verified in `build/resources/main/META-INF/mods.toml`.
- The current build reaches Java compilation; the exact pinned external artifacts resolve successfully.

### Critical Recruits dependency mismatch

The currently declared CurseMaven coordinate:

```gradle
implementation fg.deobf('curse.maven:recruits-523860:4451404')
```

resolves to **Recruits 1.9.3.4 with `loaderVersion="[40,)"`**, which is from the Forge 40 generation and is not the required Forge 47 / Minecraft 1.20.1 build.

The confirmed current Minecraft 1.20.1 release is:

- Recruits `1.15.2`
- Forge loader range `[47,)`
- Mod ID `recruits`
- Published 2026-06-29
- Modrinth project: `villager-recruits`

The implementation must pin a reproducible 1.20.1 coordinate, preferably through the Modrinth Maven repository:

```gradle
maven { url = 'https://api.modrinth.com/maven' }
implementation fg.deobf('maven.modrinth:villager-recruits:1.15.2')
```

Before finalizing the dependency syntax, verify that ForgeGradle resolves this exact module and that the resulting JAR reports `version='1.15.2'` and `loaderVersion='[47,)'` in `META-INF/mods.toml`.

### Confirmed Medieval Boomsticks API and behavior

The declared Boomsticks artifact resolves to:

- Medieval Boomsticks `1.01`
- Minecraft `[1.20.1,1.21)`
- Forge `[47,)`
- Mod ID `medieval_boomsticks`

Supported ranged weapons in the initial compatibility scope:

| Registry ID | Class | Ammo | Base reload duration | Projectile speed |
|---|---|---|---:|---:|
| `medieval_boomsticks:handgonne` | `HandGonneItem` | `round_ball` | 25 ticks, modified by Boomsticks config | 8.0 |
| `medieval_boomsticks:spikedhandgonne` | `SpikedHandGonneItem` | `round_ball`, 3-shot behavior | 120 ticks, modified by Boomsticks config | 8.0 |
| `medieval_boomsticks:arquebus` | `ArquebusItem` | `round_ball` | 25 ticks, modified by Boomsticks config | 8.0 |
| `medieval_boomsticks:arbalest` | `ArbalestItem` | `heavy_bolt` | 50 ticks, modified by Boomsticks config | 1.6 |

Relevant classes:

- `com.TBK.medieval_boomsticks.common.items.RechargeItem`
- `com.TBK.medieval_boomsticks.common.items.HandGonneItem`
- `com.TBK.medieval_boomsticks.common.items.SpikedHandGonneItem`
- `com.TBK.medieval_boomsticks.common.items.ArquebusItem`
- `com.TBK.medieval_boomsticks.common.items.ArbalestItem`
- `com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile`
- `com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile`
- `com.TBK.medieval_boomsticks.common.registers.MBItems`
- `com.TBK.medieval_boomsticks.common.registers.MBSounds`

`RechargeItem` extends `CrossbowItem`. Therefore Recruits currently recognizes Boomsticks weapons as crossbows, but its standard crossbow AI does not understand Boomsticks' ammo, custom projectiles, reload timing, or animation state.

### Why the adapter must not call the normal Boomsticks firing method

Medieval Boomsticks' internal firing path sends a smoke packet by casting the shooter to `ServerPlayer`. A recruit is not a `ServerPlayer`, so calling that path directly can cause a `ClassCastException`.

The compatibility layer must therefore:

1. Reuse Boomsticks item/projectile classes and public state helpers where safe.
2. Create `RoundBallProjectile` / `HeavyBoltProjectile` directly on the logical server.
3. Set owner, position, trajectory, pickup behavior, durability, sound, and visual particles itself.
4. Never invoke a Boomsticks method that assumes the shooter is a player unless that assumption has been verified for version 1.01.

### Confirmed Recruits extension points

Recruits 1.15.2 has:

- `CrossBowmanEntity.registerGoals()` with:
  - optional built-in Musket Mod goal at priority `0`;
  - standard ranged crossbow goal at priority `0`;
  - movement-toward-target goal at priority `8`.
- `CrossBowmanEntity.wantsToPickUp(ItemStack)`.
- `CrossBowmanEntity.getAllowedItems()`.
- `CrossBowmanEntity.getWeaponType()`, which accepts `CrossbowItem`.
- `CrossBowmanEntity.getShouldRanged()`.
- `CrossBowmanEntity.getShouldStrategicFire()` and `getStrategicFirePos()`.
- command state methods inherited from `AbstractRecruitEntity`, including follow, hold-position, move-position, mount, rest, hunger, target, and owner state.
- inventory access through `getInventory().items`.

Recruits already contains a musket-specific compatibility goal, but it is hardcoded to `musketmod:*` item descriptions and cannot be reused directly for Medieval Boomsticks. It is useful as the behavior reference for command and movement semantics.

---

## 2. Scope and non-goals

### MVP scope

The first production-ready release supports **Recruits `CrossBowmanEntity`**. This is the correct initial entity because:

- it is Recruits' ranged combat unit;
- it already exposes ranged/strategic-fire command state;
- it already permits `CrossbowItem` weapons;
- Medieval Boomsticks ranged weapons inherit from `CrossbowItem`;
- adding ranged AI to every recruit subclass would change class roles and create avoidable conflicts with melee, mounted, assassin, captain, and siege AI.

“Recruits can use boomsticks” therefore means that the player's ranged recruits/crossbowmen can use them. Expansion to other recruit subclasses is a later compatibility phase after the crossbowman path is stable.

### Required behavior

A crossbowman must be able to:

1. Accept a supported Boomsticks weapon in its inventory or main hand.
2. Pick up supported Boomsticks weapons and correct ammunition.
3. Switch a supported weapon from inventory into the main hand when ranged combat begins.
4. Respect Recruits' `RangedRecruitsNeedArrowsToShoot` setting:
   - enabled: require and consume the correct Boomsticks ammo;
   - disabled: reload and fire without consuming ammo.
5. Reload over the correct weapon-specific duration.
6. Preserve loaded state across save/reload using the weapon `ItemStack` NBT.
7. Aim at valid targets and fire the correct Boomsticks projectile.
8. Use the Boomsticks projectile's native damage handling and configuration.
9. Damage the weapon once per shot/volley.
10. Play the correct firing sound and visible smoke effect.
11. Avoid attacking the owner, teammates, allied recruits, and other entities rejected by Recruits' targeting rules.
12. Continue to obey follow, hold-position, move-position, rest, mount, hunger, and strategic-fire commands.
13. Fall back to Recruits' normal crossbow AI when the main hand is not a supported Boomsticks weapon.
14. Work on a dedicated server without loading client-only Minecraft classes from common code.

### Non-goals for the first release

- No edits to the Recruits or Medieval Boomsticks source/JAR.
- No coremod bytecode transformer outside Sponge Mixin.
- No support for unrelated gun mods.
- No custom GUI.
- No new recruit entity type.
- No replacement of Recruits targeting or command systems.
- No rebalance of projectile damage; native Boomsticks config remains authoritative.
- No support for javelins, throwing knives, throwing axes, rocks, bows, or melee weapons in the boomstick ranged goal.
- No automatic conversion of arrows to round balls/heavy bolts.
- No client-side prediction of combat; the server is authoritative.
- No blanket AI injection into all `AbstractRecruitEntity` subclasses in the MVP.

---

## 3. Target source layout

```text
src/main/java/org/iwoss/recruits_use_boomsticks/
├── RecruitsUseBoomsticks.java
├── config/
│   └── CompatConfig.java
├── compat/
│   ├── BoomstickAmmoAccess.java
│   ├── BoomstickWeaponAdapter.java
│   ├── BoomstickWeaponProfile.java
│   ├── MedievalBoomsticksAdapter.java
│   └── SupportedBoomsticks.java
├── ai/
│   ├── BoomstickAttackState.java
│   └── RecruitBoomstickAttackGoal.java
├── event/
│   └── BoomstickProjectileEvents.java
├── mixin/
│   ├── CrossBowmanEntityMixin.java
│   └── RecruitRangedCrossbowAttackGoalMixin.java
└── gametest/
    └── BoomstickCompatibilityGameTests.java

src/test/java/org/iwoss/recruits_use_boomsticks/
├── ai/BoomstickAttackStateTest.java
└── compat/BoomstickWeaponProfileTest.java

src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
├── recruits_use_boomsticks.mixins.json
└── data/recruits_use_boomsticks/tags/items/
    ├── boomstick_weapons.json
    ├── round_ball_weapons.json
    ├── heavy_bolt_weapons.json
    ├── round_ball_ammo.json
    └── heavy_bolt_ammo.json
```

If Forge GameTest isolation requires a dedicated source set, prefer:

```text
src/gametest/java/org/iwoss/recruits_use_boomsticks/gametest/
```

and wire that source set into the `gameTestServer` run. Do not ship test-only helper code in the production JAR unless Forge's GameTest discovery requires it.

---

## 4. Detailed implementation tasks

### Task 1: Preserve the current dirty work before implementation

**Objective:** Avoid accidentally losing the staged MDK version or the unstaged `build.gradle` experiment.

**Files:** No source edits.

**Steps:**

1. Run:

   ```bash
   git status --short
   git diff --cached --stat
   git diff -- build.gradle
   ```

2. Save the complete staged and unstaged diffs outside tracked source if needed for recovery.
3. Do not run `git reset --hard`, `git clean`, checkout, or restore.
4. Ask for permission before creating the first commit because the repository currently has no commits and Hermes must not commit unless explicitly requested.
5. Treat the working `build.gradle` as an experiment, not as a known-good base.

**Verification:** The status still shows the same user-owned changes before Task 2 begins.

---

### Task 2: Normalize the Forge project identity and build script

**Objective:** Produce one coherent Forge 1.20.1 build using `recruits_use_boomsticks` everywhere.

**Files:**

- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `settings.gradle`
- Modify: `src/main/resources/META-INF/mods.toml`
- Modify: `src/main/resources/recruits_use_boomsticks.mixins.json`

**Steps:**

1. Base `build.gradle` on the original Forge MDK plugin structure rather than mixing `buildscript` ForgeGradle and the plugins DSL.
2. Keep a single Forge version source:

   ```properties
   minecraft_version=1.20.1
   forge_version=47.4.20
   ```

3. Restore property-driven coordinates:

   ```gradle
   group = mod_group_id
   version = mod_version

   base {
       archivesName = mod_id
   }
   ```

4. Keep Java toolchain 17 even if the host JVM is Java 21:

   ```gradle
   java {
       toolchain.languageVersion = JavaLanguageVersion.of(17)
   }
   ```

5. Configure repositories:

   ```gradle
   repositories {
       maven { url = 'https://www.cursemaven.com' }
       maven { url = 'https://api.modrinth.com/maven' }
   }
   ```

6. Pin dependencies; do not use floating versions:

   ```gradle
   minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
   annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
   implementation fg.deobf('maven.modrinth:villager-recruits:1.15.2')
   implementation fg.deobf('curse.maven:medieval-boomsticks-1123016:5832925')
   implementation fg.deobf('curse.maven:geckolib-388172:7553267')
   ```

7. Verify whether the selected Recruits artifact declares all transitive dependencies. If it does not, add only the exact missing runtime dependencies reported by Gradle; do not guess.
8. Restore `processResources` placeholder expansion for `mods.toml` and `pack.mcmeta`.
9. Configure the Mixin refmap and config with the real mod ID:

   ```gradle
   mixin {
       add sourceSets.main, "${mod_id}.refmap.json"
       config "${mod_id}.mixins.json"
   }
   ```

10. Add `MixinConfigs: recruits_use_boomsticks.mixins.json` to the JAR manifest only if the MixinGradle/Forge configuration does not already provide it.
11. Keep `finalizedBy 'reobfJar'` for the production JAR.
12. Add JUnit Platform configuration for pure Java tests:

   ```gradle
   test {
       useJUnitPlatform()
   }
   ```

13. Add JUnit 5 test dependency using a stable pinned version compatible with Gradle 8.8.
14. Ensure run configurations use `mod_id`, not `recruits_compat`.
15. Ensure `mods.toml` contains mandatory dependencies for:
    - `forge`;
    - `minecraft`;
    - `recruits` version range `[1.15.2,1.16)`;
    - `medieval_boomsticks` version range `[1.01,1.02)`;
    - GeckoLib if it is not already guaranteed by Boomsticks metadata.
16. Set dependency ordering to `AFTER` for Recruits and Medieval Boomsticks.
17. Update the mod description to explicitly state that this is an independent compatibility layer.

**Verification commands:**

```bash
./gradlew dependencies --configuration compileClasspath
./gradlew processResources
./gradlew compileJava
./gradlew build
```

**Expected:**

- Recruits resolves as `1.15.2`, not `1.9.3.4`.
- Forge is `1.20.1-47.4.20` everywhere.
- Generated `build/resources/main/META-INF/mods.toml` contains concrete values and no `${...}` placeholders.
- The JAR is named `recruits_use_boomsticks-<version>.jar`.

**If Forge Maven TLS still fails:**

1. Test the exact artifact URL with native `curl`.
2. Check Java trust store/proxy/antivirus interception.
3. Retry with Java 17 as Gradle's launcher JVM, not only as the toolchain.
4. Do not weaken TLS protocols or disable certificate validation as a permanent fix.
5. Record the environment blocker separately from code failures.

---

### Task 3: Replace the generated Forge example with a minimal mod entry point

**Objective:** Remove example blocks/items/logging and leave only compatibility initialization.

**Files:**

- Rename/delete: `src/main/java/org/iwoss/recruits_use_boomsticks/Recruits_use_boomsticks.java`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/RecruitsUseBoomsticks.java`
- Replace: `src/main/java/org/iwoss/recruits_use_boomsticks/Config.java`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/config/CompatConfig.java`

**Steps:**

1. Rename the class to Java style `RecruitsUseBoomsticks` while keeping:

   ```java
   public static final String MOD_ID = "recruits_use_boomsticks";
   ```

2. Remove all generated example content:
   - example block;
   - example item;
   - example creative tab;
   - dirt logging;
   - magic number;
   - client username logging.
3. Keep a single logger.
4. Register only the common Forge config and event handlers needed by the compatibility layer.
5. Do not import `net.minecraft.client.Minecraft` from common code.
6. Replace the generated config with focused options:

   | Key | Default | Purpose |
   |---|---:|---|
   | `enabled` | `true` | Master switch for custom AI injection |
   | `allowStrategicFire` | `true` | Permit block-position strategic fire |
   | `smokeParticles` | `true` | Emit server-synchronized safe smoke particles |
   | `debugLogging` | `false` | State transition and adapter diagnostics |

7. Do not duplicate Boomsticks damage/reload balance settings or Recruits' ammo requirement setting.
8. Log one concise startup line containing detected dependency versions.

**Tests:**

- Unit test that config defaults match the table.
- Dedicated server classloading check: no common class references `net.minecraft.client.*`.

**Verification:** `./gradlew compileJava` passes and the generated example registry objects no longer appear in the codebase.

---

### Task 4: Add item tags and a supported-weapon catalog

**Objective:** Centralize weapon/ammo identification and avoid localized description-ID string comparisons.

**Files:**

- Create: `src/main/resources/data/recruits_use_boomsticks/tags/items/boomstick_weapons.json`
- Create: `src/main/resources/data/recruits_use_boomsticks/tags/items/round_ball_weapons.json`
- Create: `src/main/resources/data/recruits_use_boomsticks/tags/items/heavy_bolt_weapons.json`
- Create: `src/main/resources/data/recruits_use_boomsticks/tags/items/round_ball_ammo.json`
- Create: `src/main/resources/data/recruits_use_boomsticks/tags/items/heavy_bolt_ammo.json`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/compat/SupportedBoomsticks.java`

**Tag contents:**

- `boomstick_weapons`:
  - `medieval_boomsticks:handgonne`
  - `medieval_boomsticks:spikedhandgonne`
  - `medieval_boomsticks:arquebus`
  - `medieval_boomsticks:arbalest`
- `round_ball_weapons`:
  - handgonne;
  - spiked handgonne;
  - arquebus.
- `heavy_bolt_weapons`:
  - arbalest.
- `round_ball_ammo`:
  - `medieval_boomsticks:round_ball`.
- `heavy_bolt_ammo`:
  - `medieval_boomsticks:heavy_bolt`.

**Steps:**

1. Define `TagKey<Item>` constants for all tags.
2. Implement:
   - `isSupportedWeapon(ItemStack)`;
   - `isSupportedAmmo(ItemStack)`;
   - `usesRoundBall(ItemStack)`;
   - `usesHeavyBolt(ItemStack)`.
3. Use registry identity or tags; never use `getDescriptionId()`.
4. Fail closed: an unknown `RechargeItem` must not enter custom AI until a profile is defined.
5. Log an actionable warning in debug mode when a tagged weapon lacks a profile.

**Tests:**

- Data-generation or JSON validation test confirms every supported weapon appears in exactly one ammo-family tag.
- Profile test confirms each registry ID maps to one expected profile.

---

### Task 5: Define the weapon profile and adapter contract

**Objective:** Separate AI state logic from Medieval Boomsticks implementation details.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/compat/BoomstickWeaponProfile.java`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/compat/BoomstickWeaponAdapter.java`
- Test: `src/test/java/org/iwoss/recruits_use_boomsticks/compat/BoomstickWeaponProfileTest.java`

**`BoomstickWeaponProfile` fields:**

- registry ID;
- ammo family;
- base reload ticks;
- projectile count;
- projectile velocity;
- inaccuracy;
- cooldown ticks;
- whether projectiles may be picked up;
- sound selector;
- particle selector.

**Adapter contract:**

```java
boolean supports(ItemStack weapon);
BoomstickWeaponProfile profile(ItemStack weapon);
boolean isLoaded(ItemStack weapon);
void setLoaded(ItemStack weapon, boolean loaded);
void setReloading(ItemStack weapon, boolean reloading);
void setFiring(ItemStack weapon, boolean firing);
int reloadTicks(ItemStack weapon);
boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);
boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);
ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition);
```

**Rules:**

1. `fire` must only be called on the logical server.
2. `fire` must return a structured result, not throw for ordinary “cannot shoot” states.
3. The adapter owns Boomsticks-specific NBT/projectile behavior.
4. The AI goal owns target selection, navigation, and state transitions.
5. The adapter must not mutate Recruits command state.
6. Use an enum for ammo family: `ROUND_BALL`, `HEAVY_BOLT`.
7. Use a result enum/record for:
   - fired;
   - no ammo;
   - invalid weapon;
   - invalid target;
   - client-side rejected;
   - spawn failed.

**Unit tests:**

- all four profiles exist;
- round-ball and heavy-bolt profiles use the correct ammo family;
- spiked handgonne has three projectiles;
- reload and velocity values match the confirmed Boomsticks behavior.

---

### Task 6: Implement recruit inventory/ammo access

**Objective:** Safely find and consume Boomsticks ammunition from Recruits' inventory.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/compat/BoomstickAmmoAccess.java`
- Test: add focused inventory tests or GameTests.

**Steps:**

1. Read inventory through `crossBowman.getInventory().items`.
2. Select ammo based on the weapon profile, not any generic projectile.
3. `hasAmmo` behavior:
   - return `true` immediately when Recruits' `RangedRecruitsNeedArrowsToShoot` is false;
   - otherwise require at least the profile's per-reload ammo count.
4. `consumeAmmo` behavior:
   - consume nothing in unlimited mode;
   - consume one round ball for handgonne/arquebus;
   - confirm whether spiked handgonne should consume three round balls per volley by comparing actual player behavior; default to three because its native charge path loads three projectiles;
   - consume one heavy bolt for arbalest.
5. Consume atomically:
   - first calculate total available count;
   - only mutate stacks if enough ammo exists;
   - shrink across multiple stacks if necessary.
6. Remove or normalize empty stacks according to Recruits container conventions.
7. Mark inventory/container state dirty if required for synchronization.
8. Never use fixed slot `6`; scan the recruit inventory so GUI rearrangement remains safe.

**GameTests:**

- correct ammo is found in a non-default slot;
- wrong ammo is rejected;
- insufficient three-round volley consumes nothing;
- unlimited mode consumes nothing;
- consumption survives inventory sync/save.

---

### Task 7: Implement safe Medieval Boomsticks projectile firing

**Objective:** Fire native Boomsticks projectiles from a non-player recruit without entering player-only code.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/compat/MedievalBoomsticksAdapter.java`
- Modify: adapter/profile tests
- GameTest: projectile creation and ownership

**Steps:**

1. Validate:
   - server side;
   - supported weapon;
   - alive recruit;
   - finite target coordinates;
   - weapon loaded state.
2. For firearms, instantiate:

   ```java
   new RoundBallProjectile(level, recruit, weaponStack)
   ```

3. For arbalest, instantiate:

   ```java
   new HeavyBoltProjectile(level, recruit, weaponStack)
   ```

4. Explicitly set the projectile owner to the recruit even if the constructor already does so.
5. Spawn from the recruit's eye/muzzle area, not its feet.
6. Calculate direction from projectile origin to target aim point.
7. Add the same modest vertical compensation used by Boomsticks only if a GameTest/manual test proves it is needed.
8. Apply profile velocity and inaccuracy.
9. For the spiked handgonne, spawn three projectiles with deterministic `-10°`, `0°`, `+10°` horizontal spread around the aim vector.
10. Set pickup behavior:
    - round balls: disallowed, matching Boomsticks;
    - heavy bolts: allowed, matching Boomsticks.
11. Add each projectile through `ServerLevel.addFreshEntity` and verify the return value where available.
12. Play the native firing sound:
    - handgonne/spiked handgonne → `MBSounds.HANDGONNE_SHOOT`;
    - arquebus → `MBSounds.ARQUEBUS_SHOOT`;
    - arbalest → the same crossbow-style sound used by Boomsticks.
13. Emit safe particles using `ServerLevel.sendParticles`; do not call Boomsticks' packet method that casts to `ServerPlayer`.
14. Apply native misfire probability only if it can be read safely from Boomsticks config and reproduced without player casts. If enabled:
    - consume the loaded shot exactly as native behavior does;
    - play failure sound;
    - spawn the failed projectile safely or skip projectile according to verified player behavior;
    - never damage allies through a malformed downward shot.
15. Damage the weapon once per fired volley, not once per pellet, using the recruit as the durability callback owner.
16. Set NBT animation state:
    - `recharge=false` after reload;
    - `fire=true` immediately after firing;
    - clear `fire` after a short state-machine-controlled animation window;
    - `Charged=false` after a successful shot.
17. Do not manually calculate damage. `RoundBallProjectile` and `HeavyBoltProjectile` must remain responsible for damage and armor interaction so Boomsticks config remains authoritative.
18. Catch and log unexpected adapter failures with weapon registry ID, recruit UUID/entity ID, and state; do not crash the server tick.

**GameTests:**

- firearm creates `RoundBallProjectile`;
- arbalest creates `HeavyBoltProjectile`;
- projectile owner is the recruit;
- round ball pickup is disallowed;
- heavy bolt pickup is allowed;
- spiked handgonne creates exactly three projectiles;
- one volley damages weapon once;
- firing from a recruit does not throw `ClassCastException`;
- no client-only class is referenced on a dedicated server.

---

### Task 8: Build a deterministic boomstick attack state machine

**Objective:** Make reload/aim/fire transitions explicit, testable, and save-safe.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/ai/BoomstickAttackState.java`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/ai/RecruitBoomstickAttackGoal.java`
- Test: `src/test/java/org/iwoss/recruits_use_boomsticks/ai/BoomstickAttackStateTest.java`

**States:**

```text
IDLE
ACQUIRE_WEAPON
RELOAD
AIM
FIRE
COOLDOWN
OUT_OF_AMMO
```

**Required transitions:**

```text
IDLE -> ACQUIRE_WEAPON     target/strategic fire exists, supported weapon not in hand
IDLE -> RELOAD             supported unloaded weapon in hand and ammo available
IDLE -> AIM                supported loaded weapon in hand and valid target exists
RELOAD -> AIM              reload timer complete, ammo consumed, weapon marked loaded
RELOAD -> OUT_OF_AMMO      ammo disappears before reload completion
AIM -> FIRE                target visible for configured aim window
AIM -> IDLE                target invalid/dead or ranged mode disabled
FIRE -> COOLDOWN           adapter reports success
FIRE -> OUT_OF_AMMO        adapter rejects for ammo/state reason
COOLDOWN -> RELOAD         cooldown complete and ammo available
COOLDOWN -> AIM            cooldown complete and weapon still loaded
OUT_OF_AMMO -> RELOAD      correct ammo later becomes available
ANY -> IDLE                goal stops, recruit rests/eats/mounts, mod disabled, or weapon unsupported
```

**Goal flags:**

- `MOVE`
- `LOOK`

Add `TARGET` only if tests prove it is required; target selection should remain owned by Recruits.

**`canUse` conditions:**

1. compat config enabled;
2. recruit alive;
3. recruit is in ranged mode;
4. recruit is not resting/eating/mounting;
5. valid living target exists **or** valid strategic-fire block position exists;
6. supported weapon is in main hand or inventory;
7. command/move-position constraints allow combat.

**Weapon switching:**

1. If main hand is not supported, call Recruits' public `switchMainHandItem(Predicate<ItemStack>)` with `SupportedBoomsticks::isSupportedWeapon`.
2. Do not overwrite a valid loaded Boomsticks weapon.
3. Do not delete or duplicate the previous main-hand item.
4. After switching, wait until the next tick before entering reload/aim.

**Reload logic:**

1. At reload start:
   - calculate reload ticks from the adapter/profile and Boomsticks recharge-speed config;
   - multiply by two when the recruit is a passenger only if matching Recruits musket behavior is desired and verified;
   - set `recharge=true`;
   - call `startUsingItem(MAIN_HAND)` for pose synchronization if safe.
2. Each reload tick:
   - keep looking toward the target when one exists;
   - do not consume ammo yet;
   - abort if the main-hand stack changes.
3. At completion:
   - atomically consume ammo;
   - mark loaded;
   - clear reload animation state;
   - stop/release item use;
   - transition to aim.
4. On abort:
   - clear reload animation state;
   - stop item use;
   - do not consume ammo;
   - do not mark loaded.

**Aim logic:**

1. Require line of sight for entity targets.
2. Keep the look controller aimed at target eye/body center.
3. Use 10–17 ticks of aim delay as the initial default, matching Recruits' musket cadence.
4. Reset accumulated visibility time when line of sight is lost.
5. Do not shoot through solid blocks.
6. For strategic fire, aim at block center and use the Recruits strategic-fire position.

**Movement/command logic:**

Mirror Recruits behavior without replacing command ownership:

- follow mode: stop to fire when the owner is close and target is in range; approach target only when too far;
- hold position: do not leave the allowed hold radius to chase;
- move position: do not fire when the command requires reaching a distant move position first;
- wander/normal combat: stop navigation when within ranged envelope; approach when beyond maximum range;
- mounted: either double reload duration and allow shooting, or disable in MVP based on manual test results;
- hunger/rest: yield to Recruits eating/rest goals.

**Range defaults:**

- melee handoff/stop range: reuse `crossBowman.getMeleeStartRange()`;
- preferred firearm range: approximately 12–45 blocks;
- maximum target retention: reuse Recruits' target system; do not hard-delete targets solely because this goal has a fixed number unless verified necessary.

**State cleanup in `stop()`:**

- reset visibility and cooldown counters;
- clear `recharge` and short-lived `fire` animation NBT;
- stop using item;
- stop forcing aggressive pose;
- do not clear valid Recruits target/commands;
- do not clear loaded weapon state.

**Pure unit tests:**

- every transition listed above;
- reload interruption does not consume ammo;
- no-ammo recovery;
- target loss during aim;
- weapon swap during reload;
- cooldown behavior;
- config disable while active;
- strategic-fire path without a living target.

---

### Task 9: Inject the custom goal without changing Recruits source

**Objective:** Register the goal and item pickup behavior entirely through Mixins.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/mixin/CrossBowmanEntityMixin.java`
- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/mixin/RecruitRangedCrossbowAttackGoalMixin.java`
- Modify: `src/main/resources/recruits_use_boomsticks.mixins.json`

**Mixin A — `CrossBowmanEntityMixin`:**

1. Target `com.talhanation.recruits.entities.CrossBowmanEntity`.
2. Inject at `TAIL` of `registerGoals()`.
3. Add exactly one `RecruitBoomstickAttackGoal` at priority `0` or `1` after verifying interaction with Recruits priority ordering.
4. Use an accessor/shadow only for members that are actually required and stable.
5. Inject into `wantsToPickUp(ItemStack)` at `HEAD`, cancellable:
   - supported Boomsticks weapon → return true when inventory can accept it and replacement rules allow it;
   - supported Boomsticks ammo → return true when Recruits' ammo requirement is enabled or resupply is useful;
   - otherwise leave original Recruits behavior untouched.
6. Do not overwrite `getAllowedItems`; its existing predicate already calls `wantsToPickUp`.
7. Do not overwrite `canHoldItem`; supported weapons extend `CrossbowItem` and already satisfy the ranged recruit's item rule.
8. Add a unique marker/guard if there is any risk that `registerGoals` can execute twice.

**Mixin B — `RecruitRangedCrossbowAttackGoalMixin`:**

1. Target `com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal`.
2. Inject at `HEAD` of `canUse()`, cancellable.
3. Access the goal's crossbowman field using `@Shadow` or an accessor after confirming the exact mapped field name.
4. If the crossbowman's main hand is a supported Boomsticks weapon, return `false` so the vanilla crossbow AI cannot charge/fire it incorrectly.
5. Otherwise do not cancel and preserve vanilla behavior.
6. Apply the same guard to `canContinueToUse()` only if it does not delegate to `canUse()` in the pinned Recruits version.
7. Do not disable the built-in Musket Mod goal for actual `musketmod:*` weapons.

**Mixin JSON:**

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "org.iwoss.recruits_use_boomsticks.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "recruits_use_boomsticks.refmap.json",
  "mixins": [
    "CrossBowmanEntityMixin",
    "RecruitRangedCrossbowAttackGoalMixin"
  ],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

**Verification:**

- Start a server with Mixin debug export enabled once.
- Confirm both Mixins apply exactly once.
- Confirm an ordinary vanilla crossbow still uses Recruits' original crossbow goal.
- Confirm a Boomsticks weapon activates only the custom goal.
- Treat any failed required injection as a hard startup error, because silently running the wrong AI could corrupt gameplay.

---

### Task 10: Add friendly-fire protection for recruit-owned Boomsticks projectiles

**Objective:** Prevent projectiles from damaging allies while retaining native projectile damage against enemies.

**Files:**

- Create: `src/main/java/org/iwoss/recruits_use_boomsticks/event/BoomstickProjectileEvents.java`
- Modify: `RecruitsUseBoomsticks.java` only if explicit event registration is required
- GameTest: ally/enemy projectile impact

**Steps:**

1. Subscribe to Forge's projectile impact event.
2. Filter strictly:
   - projectile is `RoundBallProjectile` or `HeavyBoltProjectile`;
   - owner is an `AbstractRecruitEntity`;
   - hit result is an entity hit.
3. For a living hit entity, use Recruits/vanilla relationship checks:
   - `owner.isAlliedTo(hit)`;
   - `owner.canAttack(hit)` or the mapped equivalent;
   - owner identity/team/UUID checks.
4. Cancel impact only for allies/entities Recruits would not attack.
5. Verify whether cancelling the Forge event lets the projectile continue. If it does not, use the smallest Mixin into the projectile hit method to ignore allied hits without discarding the projectile.
6. Do not cancel terrain impacts.
7. Do not globally change player-fired Boomsticks projectiles.
8. Add a pre-fire line-of-fire check to reduce repeated allied impacts, but keep the impact guard as the final authority.

**GameTests:**

- owner cannot be hit by its own projectile;
- allied recruit is not damaged;
- allied player is not damaged;
- hostile mob is damaged;
- enemy player/recruit follows Recruits team rules;
- player-fired projectiles remain unchanged.

---

### Task 11: Preserve animation and synchronization behavior

**Objective:** Make reload/fire state visible without relying on unsafe player-only packets.

**Files:**

- Modify: `MedievalBoomsticksAdapter.java`
- Modify: `RecruitBoomstickAttackGoal.java`
- Optional create: client-only event class only if common item rendering does not update automatically

**Steps:**

1. Use Boomsticks' existing item NBT keys through public helpers where available:
   - `Charged`;
   - `recharge`;
   - `fire`.
2. Ensure these mutations happen on the server-held `ItemStack`.
3. Trigger inventory/equipment synchronization after NBT changes if vanilla equipment packets do not propagate them automatically.
4. Avoid copying the stack during active reload, because NBT changes must affect the equipped stack.
5. Clear `fire` after a short deterministic duration.
6. Clear stale `recharge` on goal stop, entity load, or weapon swap.
7. Use server particles for smoke and rely on Boomsticks' projectile tracking for projectile visuals.
8. If GeckoLib item animation does not render in a recruit's hand, document that as a visual limitation before adding client Mixins; do not block server functionality on it.

**Manual verification:**

- another client sees reload pose/state;
- another client sees firing and projectile;
- reconnecting during/after reload does not leave the weapon permanently in recharge animation;
- saving a loaded weapon preserves loaded state.

---

### Task 12: Add GameTests for end-to-end behavior

**Objective:** Exercise the real Recruits entity, real Boomsticks item, real inventory, and real server tick loop.

**Files:**

- Create: `src/main/java/.../gametest/BoomstickCompatibilityGameTests.java` or dedicated `src/gametest/java/...`
- Modify: `build.gradle` / run configuration if a separate GameTest source set is used
- Add minimal GameTest structure templates if Forge requires them

**Required GameTests:**

1. `crossbowman_picks_up_supported_weapon`
2. `crossbowman_picks_up_round_ball_ammo`
3. `crossbowman_ignores_wrong_ammo_for_weapon`
4. `handgonne_reloads_and_fires_round_ball`
5. `arquebus_reloads_and_fires_round_ball`
6. `arbalest_reloads_and_fires_heavy_bolt`
7. `spiked_handgonne_fires_three_projectiles`
8. `ammo_is_consumed_once_per_reload`
9. `unlimited_ammo_config_does_not_consume`
10. `reload_abort_does_not_consume_ammo`
11. `loaded_state_survives_entity_save_reload`
12. `vanilla_crossbow_still_uses_original_ai`
13. `boomstick_does_not_use_original_crossbow_ai`
14. `follow_command_is_not_cleared`
15. `hold_position_is_not_cleared`
16. `strategic_fire_uses_block_position`
17. `ally_is_not_damaged`
18. `enemy_is_damaged`
19. `dedicated_server_shot_has_no_player_cast_crash`
20. `weapon_break_does_not_leave_goal_stuck`

**Test design rules:**

- Use deterministic targets and fixed positions.
- Allow enough ticks for the longest reload (spiked handgonne).
- Count entities of exact projectile type in a bounded test area.
- Assert inventory counts before/after.
- Assert target health before/after only after projectile impact.
- Clean up entities/projectiles at test end.
- Do not rely on client rendering in GameTests.

**Verification command:**

```bash
./gradlew runGameTestServer
```

**Expected:** Server exits successfully with all compatibility tests passing.

---

### Task 13: Run a manual gameplay matrix

**Objective:** Validate behavior that automated server tests cannot fully cover.

**Client/server matrix:**

| Environment | Required |
|---|---|
| Single-player integrated server | Yes |
| Dedicated Forge server | Yes |
| Two clients connected to dedicated server | Yes |

**Weapon matrix:**

| Weapon | Pickup | Reload | Ammo consumed | Correct projectile | Sound | Animation | Durability |
|---|---|---|---|---|---|---|---|
| Handgonne | Test | Test | Test | Test | Test | Test | Test |
| Spiked handgonne | Test | Test | Test ×3 | Test ×3 | Test | Test | Test once |
| Arquebus | Test | Test | Test | Test | Test | Test | Test |
| Arbalest | Test | Test | Test | Test | Test | Test | Test |

**Command matrix:**

- follow owner;
- hold position;
- move to position;
- normal wander/combat;
- strategic fire;
- stop ranged mode;
- rest;
- eat/resupply;
- mount/dismount if supported.

**Target matrix:**

- hostile vanilla mob;
- hostile player/team;
- allied player;
- allied recruit;
- owner;
- target behind a wall;
- target moving laterally;
- target at close range;
- target beyond preferred range.

**Persistence matrix:**

- save while unloaded;
- save while loaded;
- save during reload;
- weapon swap during reload;
- remove ammo during reload;
- weapon breaks on shot;
- server restart;
- client reconnect.

**Log checks:**

- no Mixin apply failures;
- no `ClassCastException` involving `ServerPlayer`;
- no repeated reflection errors;
- no duplicate goal registration;
- no item duplication/loss;
- no client classloading failure on dedicated server;
- no per-tick log spam unless debug mode is enabled.

---

### Task 14: Add compatibility failure diagnostics

**Objective:** Fail clearly when pinned external APIs change.

**Files:**

- Modify: main entry point
- Modify: adapter initialization
- Modify: Mixin configuration

**Steps:**

1. At startup, verify all required registry IDs exist.
2. Validate that supported items are instances of the expected Boomsticks base class.
3. Validate projectile constructors/classes are loadable on the common side.
4. Log dependency versions from Forge's mod list.
5. On unsupported versions:
   - rely on strict `mods.toml` ranges to stop loading;
   - provide a clear dependency error rather than attempting best-effort reflection.
6. Keep Mixins `required=true` and `defaultRequire=1`.
7. Add debug state-transition logging with rate limiting and entity ID/UUID.
8. Never log every AI tick in normal mode.

**Verification:** Temporarily test one intentionally incompatible dependency version and confirm startup fails with an actionable message, then restore pinned versions.

---

### Task 15: Packaging and release readiness

**Objective:** Produce a reproducible compatibility JAR and installation documentation.

**Files:**

- Create: `README.md`
- Create: `CHANGELOG.md`
- Optional create: `LICENSE` after the user selects a license
- Modify: `gradle.properties`
- Modify: `mods.toml`

**README contents:**

1. Purpose: compatibility layer only.
2. Supported versions:
   - Minecraft 1.20.1;
   - Forge 47.4.20;
   - Recruits 1.15.2;
   - Medieval Boomsticks 1.01;
   - required GeckoLib version.
3. Supported recruit type: Crossbowman.
4. Supported weapons and ammo table.
5. Installation instructions.
6. Server/client requirement: install on both sides unless tests prove server-only operation is sufficient.
7. Config options.
8. Known limitations.
9. Troubleshooting and debug-log instructions.
10. Explicit statement that Recruits and Medieval Boomsticks are separate projects and are not redistributed.

**Final verification commands:**

```bash
./gradlew clean
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

Then inspect the artifact:

```bash
unzip -l build/libs/recruits_use_boomsticks-*.jar
unzip -p build/libs/recruits_use_boomsticks-*.jar META-INF/mods.toml
unzip -p build/libs/recruits_use_boomsticks-*.jar recruits_use_boomsticks.mixins.json
```

**Expected artifact requirements:**

- no example block/item classes;
- correct mod ID and version;
- concrete `mods.toml` values;
- correct Mixin config and refmap;
- no bundled Recruits/Boomsticks JARs;
- no decompiled dependency source;
- no test structures unless intentionally included;
- reobfuscated production classes;
- deterministic clean build.

---

## 5. Acceptance criteria

The feature is complete only when all criteria below are true:

- [ ] The project builds from a clean checkout with Java 17 and Forge 47.4.20.
- [ ] The dependency graph contains Recruits 1.15.2, not the obsolete 1.9.3.4 artifact.
- [ ] Recruits and Medieval Boomsticks source/JAR files remain unmodified.
- [ ] The compatibility mod contains no copied/decompiled dependency code.
- [ ] Crossbowmen can pick up all four supported weapons.
- [ ] Crossbowmen can pick up and distinguish round balls and heavy bolts.
- [ ] Correct ammo is required/consumed when Recruits' ammo setting is enabled.
- [ ] Unlimited-ammo mode works when that setting is disabled.
- [ ] Reload duration follows weapon type and Boomsticks recharge-speed config.
- [ ] Reload interruption never consumes or duplicates ammo.
- [ ] Firearms spawn native `RoundBallProjectile` entities.
- [ ] Arbalest spawns native `HeavyBoltProjectile` entities.
- [ ] Spiked handgonne produces the verified native volley count.
- [ ] Projectile owner is the firing recruit.
- [ ] Native Boomsticks projectile damage/config behavior is preserved.
- [ ] No player-only Boomsticks fire path is called for a recruit.
- [ ] No `ServerPlayer` cast crash occurs.
- [ ] Vanilla crossbows still use Recruits' original AI.
- [ ] Actual `musketmod` weapons still use Recruits' existing musket integration when that mod is installed.
- [ ] Follow/hold/move/strategic-fire commands remain functional.
- [ ] Owners and allies are protected from recruit-fired projectiles.
- [ ] Enemy targets are damaged normally.
- [ ] Loaded state survives save/reload.
- [ ] Dedicated server startup and combat work without client classloading errors.
- [ ] All unit tests, GameTests, and manual matrix checks pass.

---

## 6. Risks and mitigations

### Risk: external APIs are not stable

Recruits classes and Boomsticks methods are implementation APIs, not guaranteed compatibility APIs.

**Mitigation:** Pin exact versions, use strict dependency ranges, isolate references in Mixins/adapters, and fail startup clearly when a required injection changes.

### Risk: two priority-0 ranged goals compete

Boomsticks weapons inherit `CrossbowItem`, so Recruits' vanilla crossbow goal may also activate.

**Mitigation:** Explicitly suppress only the vanilla crossbow goal while a supported Boomsticks weapon is equipped. Never disable it globally.

### Risk: unsafe Boomsticks player assumptions

The native firing path casts the shooter to `ServerPlayer` for smoke packets.

**Mitigation:** Spawn native projectile entities directly and use safe server particles/sounds. Add a dedicated GameTest for non-player firing.

### Risk: inventory duplication or ammo loss

Reload spans many ticks and inventory may change during it.

**Mitigation:** Consume ammo atomically only at reload completion, revalidate the exact equipped stack, and abort cleanly on weapon/ammo changes.

### Risk: friendly fire through native projectile hit logic

Boomsticks projectiles own damage handling and may not know Recruits alliances.

**Mitigation:** Add a narrowly filtered projectile-impact guard for recruit-owned Boomsticks projectiles.

### Risk: GeckoLib animation state does not synchronize for mob-held item stacks

NBT changes may not trigger the desired rendering on remote clients.

**Mitigation:** Keep combat server-correct first, synchronize equipment updates, test with two clients, and add client-only rendering hooks only if necessary.

### Risk: current build cannot download Forge dependencies

TLS currently blocks ForgeSPI download before compilation.

**Mitigation:** Resolve the environment/trust-store issue before interpreting compilation results. Never claim code works based solely on static inspection.

### Risk: latest Recruits version moves quickly

Recruits 1.15.2 was published shortly before this plan.

**Mitigation:** Pin 1.15.2 for the first release. Test newer versions in separate branches before widening `mods.toml` ranges.

---

## 7. Open decisions to resolve during implementation

These decisions require actual gameplay/API verification rather than guessing:

1. **Spiked handgonne ammo cost:** confirm whether native behavior consumes three round balls for a three-projectile volley.
2. **Mounted firing:** decide whether crossbowmen may reload/fire while mounted, and whether reload time doubles.
3. **Misfire behavior:** determine whether to reproduce Boomsticks' configured misfire probability for AI shooters.
4. **Heavy bolt pickup:** verify that allowing pickup does not create an ammo duplication loop with Recruits auto-pickup.
5. **Strategic fire:** confirm desired support for boomsticks; default plan supports it behind config.
6. **Client installation requirement:** test whether server-only installation is possible; assume both client and server until proven otherwise because item animation and Mixins may be loaded on both.
7. **Version range policy:** keep exact/strict ranges for the first release, widen only after regression testing.
8. **Expansion beyond crossbowmen:** after MVP, decide whether specific ranged-capable classes such as mounted ranged units should receive the same goal. Do not inject into all recruits by default.

---

## 8. Recommended implementation sequence and review gates

Implement in this order:

1. Build/dependency repair.
2. Minimal mod entry point.
3. Tags and weapon profiles.
4. Pure state-machine tests.
5. Inventory/ammo adapter.
6. Safe projectile adapter.
7. Custom AI goal.
8. Mixins and vanilla-goal suppression.
9. Friendly-fire guard.
10. GameTests.
11. Dedicated-server test.
12. Two-client manual test.
13. Documentation and packaging.

After every stage:

1. Run the narrowest relevant test.
2. Run `compileJava`.
3. Review for accidental dependency source changes.
4. Review dedicated-server safety.
5. Review inventory mutation for duplication/loss.
6. Create a logical commit only if the user has explicitly authorized commits.

Suggested commit boundaries, if authorized:

```text
build: normalize Forge and dependency configuration
refactor: replace generated MDK example with compat bootstrap
feat: add Medieval Boomsticks weapon profiles and ammo access
feat: add safe recruit boomstick projectile adapter
feat: add recruit boomstick ranged combat goal
feat: inject boomstick compatibility into Recruits crossbowmen
fix: prevent friendly fire from recruit boomstick projectiles
test: add boomstick compatibility GameTests
docs: document supported versions and weapons
```

---

## 9. Definition of done

“Build succeeds” alone is not enough. The compatibility layer is done only after a clean JAR has been built, GameTests pass, a dedicated server fires every supported weapon without exceptions, a second client sees the combat, ammo/persistence tests pass, allies are not damaged, and vanilla Recruits crossbow behavior remains unchanged for non-Boomsticks weapons.
