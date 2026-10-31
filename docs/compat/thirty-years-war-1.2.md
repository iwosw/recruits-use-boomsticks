# Epic Knights: Thirty Years War 1.2

## Verified artifact and API

- Minecraft 1.20.1, Forge; mod ID `thirtyyearswar`, version `1.2`.
- [CurseForge file 9307196](https://www.curseforge.com/minecraft/mc-mods/epic-knights-thirty-years-war/files/9307196), project `1752267`.
- Maven: `curse.maven:epic-knights-thirty-years-war-1752267:9307196`.
- Original JAR SHA-256: `197be369ae1e1875bfa7d7e91d6763fc1e55f47dcd2b2a37b0de5812a4cbf7ed`.
- API traced in the released JAR and compared with the author's [GunItem source](https://github.com/Magistu/Epic-Knights-Thirty-Years-War/blob/1.20.1/common/src/main/java/com/magistuarmory/thirtyyearswar/item/GunItem.java).
- Native dependencies: Epic Knights >=10.11, Architectury >=9.0.8 and Cloth Config >=11.0.99.

The integration is optional (`mandatory=false`, version range `[1.2]`). Only the discovery
edge is reflective; `ThirtyYearsWarAdapter` calls the actual typed `GunItem` API after its
class is discovered. Neither upstream code nor dependency JARs are bundled.

## Firearm contracts

| Registry path in `thirtyyearswar` | Native reload ticks | Cooldown ticks | Damage | Range | Gaussian spread | Armor penetration |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `baroque_matchlock_musket` | 52 | 80 | 18 | 80 | 0.006 | 50% |
| `baroque_wheellock_pistol` | 59 | 40 | 12 | 40 | 0.018 | 25% |

These are defaults, not duplicated tuning constants. The adapter reads each native
`GunItem.config` and `getReloadTicks()` at use time, respecting the native minimum reload
durations. The musket config says 45 ticks, but `getReloadTicks()` clamps it to 52.

Both firearms spend one physical `thirtyyearswar:paper_cartridge` per completed reload,
independently of the Recruits vanilla-arrow exemption. Matchlocks additionally require
`minecraft:flint_and_steel`, which receives one point of native durability wear.
The recruit can keep a shield in the off hand; supplies come from storage or the off hand,
never from armor slots. Tools are used from the inventory without copying or replacing hands.

The NPC reload commits the cartridge and tool wear together after the reload timer.
This deliberately differs from the player's flint wear at the beginning of use: interruption
before NPC completion spends nothing. The native boolean `Charged` is the entire loaded
payload; no `ChargedProjectiles`, fake arrow, powder stage or Artillery NBT is invented.
Loaded guns need no spare cartridge or flint to fire. Save/load recovery clears only
compatibility animation markers and preserves a paid charge.

## Native shot boundary

`GunItem.performShooting(ItemStack, Level, LivingEntity)` accepts a recruit directly.
It traces a Gaussian-spread ray, caps entity detection at the nearest block, applies
native `thirtyyearswar:bullet` damage with temporary armor penetration, then plays the
native sound and effects. Glass destruction, target blocks and bells retain native behavior.
There is no persistent bullet entity. The adapter reports an instant shot with zero spawned
projectiles, so the shared AI releases its formation reservation immediately after firing.

The adapter faces the ray at the supplied aim point, clears the charge before invoking the
synchronous native shot, and wears the gun once (upstream does that only for players).
A native effect failure cannot restore a cartridge after damage may already have happened.
Out-of-range, nonfinite, unloaded, disabled and client-side requests are rejected before spending.

`ThirtyYearsWarShotEvents` cancels recruit bullet attacks against the owner, same-owner
recruits, anything riding with the shooter (its own mount included) and other entities the
shooter cannot attack. Before spending, `fire()` repeats the native ray without its spread;
if that ray's first entity is protected, the shot is refused as `INVALID_TARGET` and the
charge is kept, so a rear rank holds fire until the front rank clears. Spread can still clip
an ally; that friendly entity then blocks the native ray without taking damage.

Combat engages at `range - 3` blocks feet to feet (`SupportedThirtyYearsWar.engagementRange`):
the AI measures from the feet, while the native ray and the out-of-range refusal measure from
the eye to the target's lower third. Engaging at the full native range left a target near the
edge in an endless aim-and-refuse loop. The 40-block pistol therefore engages at 37 blocks.

Thirty Years War guns extend `CrossbowItem`. Recruits' `RecruitRangedCrossbowAttackGoal`
recognizes crossbows by `instanceof` and loads them through `CrossbowItem.setCharged`, which
writes the same native `Charged` boolean. A mixin makes its `isCrossbow` reject enabled
native guns, so that goal can neither charge one nor pull one from storage. Otherwise a
recruit without cartridges would charge the gun with an arrow (or for free when Recruits does
not require arrows) and the boomstick goal would fire a free native bullet.

Only recruit-origin native bullet damage gets the global
damage multiplier/floor and `damage.thirtyYearsWarDamageMultiplier`; player shots and
other damage types remain native. The native armor modifier is still removed after each hit.
`damage.projectilesIgnoreHurtCooldown` also applies to these instant recruit bullets,
so simultaneous formation shots can each deal damage; disabling it retains native invulnerability.

`thirtyYearsWarEnabled` and the global `enabled` switch gate AI routing and firing.
The shared reload/aim poses and Weapons out / Weapons away commands use the same adapter.
The addon's internal first-person mechanism animation is player-specific; this integration
does not patch that renderer for third-person recruit tool animations.

## Runtime profiles and verification

The development runtime includes file 9307196 by default. Use
`-PincludeThirtyYearsWar=false` to remove it completely from the runtime classpath.
It remains compile-only so both profiles can build the typed adapter.
`-Partillery=none -PincludeMedievalBoomsticks=false` exercises Thirty Years War alone.

Forge GameTests live in `ThirtyYearsWarGameTests`. Optional weapon cases are gated when
the addon is absent; a separate test checks that actual mod presence matches the selected
Gradle profile, so a missing dependency cannot masquerade as positive firearm verification.

On 2026-10-31, `gradlew test runGameTestServer --offline --console=plain` passed
171 unit tests and all 158 required Forge GameTests with Thirty Years War 1.2,
Artillery 1.16.2 and Medieval Boomsticks installed. Eleven new required GameTests
cover the integration, including an explicit optional-dependency presence check.

`gradlew runGameTestServer -PincludeThirtyYearsWar=false -Pmatrix=tyw-absent
--offline --console=plain` also passed all 158 required tests. The eleven Thirty Years War
cases are dependency-gated in that run except the presence check, which verifies that
the addon really is absent; the existing 147 tests remain active.

`gradlew runGameTestServer -Partillery=none -Pmatrix=tyw-no-artillery --offline
--console=plain` passed all 158 required tests with Thirty Years War installed and
Artillery absent. The new firearm cases execute in that profile; Artillery-specific
cases retain their existing dependency gates.

The final `test runGameTestServer build` run with `-Partillery=none
-Pmatrix=tyw-no-artillery` also completed successfully. Release artifact:
`build/libs/recruits_use_boomsticks-2.2.1.jar`, 397,713 bytes, SHA-256
`85ab21dd20395c4d787169a6e632e79057661385d24a7d6b39ae0b787d592f4b`.
The reobfuscated JAR contains the adapter and damage handler, declares Thirty Years War
as optional at exactly version 1.2, and bundles no upstream classes, dependency JARs,
or development-only Artillery mixins. The existing 2.2.1 project version was retained;
no commit, push or publication was performed.

This is dedicated-server automation evidence. A manual visual client playthrough
is not claimed. With Epic Knights 10.11 the upstream addon logs nonfatal cloak
recipe/advancement errors for missing `magistuarmory:baroque_cloak_decoration`;
both firearms still register and execute. The existing Artillery guide-book
parse warning is also nonfatal.

### Bug-fix pass (2026-10-31)

The crossbow-goal, engagement-range and line-of-fire fixes above added three required
GameTests and one unit test. `gradlew test runGameTestServer` then passed 172 unit tests
and all 161 required GameTests in the default profile, `-PincludeThirtyYearsWar=false
-Pmatrix=tyw-absent` and `-Partillery=none -Pmatrix=tyw-no-artillery`. `gradlew build`
rebuilt `build/libs/recruits_use_boomsticks-2.2.1.jar`: 400,690 bytes, SHA-256
`b1f92b732507cf6f65403ab947c7ff1d84ec8b4397df1543f6cf19f8b941a156`, still with no
upstream classes. The earlier artifact figures above are superseded.
