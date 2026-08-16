# Artillery Addon compatibility verification

## Status: EIGHTEEN FIREARM PROFILES AND FIVE THROWING WEAPONS RUNTIME-VERIFIED

All eighteen enabled firearm profiles and all five throwing weapons are proven on an unpatched
dedicated GameTest server with Artillery 1.13.4 (`8252195`). The full supported Artillery range was
rerun on the current tree on 2026-08-16: 1.11, 1.11.1, 1.12, 1.13, 1.13.1, 1.13.2, 1.13.3, and
1.13.4 each passed all 133 required tests. Artillery 1.14.0 still crashes an unpatched dedicated server on `ClientLevel`;
the development-only patch remains verification scaffolding and is not shipped.

The current dependency matrix also passed all 133 required tests at both verified corners: Forge
47.3.32 with Recruits 1.15.0, Medieval Boomsticks 1.01, GeckoLib 4.2.4, Epic Knights 8.2,
Architectury 9.0.8, and Cloth Config 11.0.99; and Forge 47.4.22 with Recruits 1.15.2, Medieval
Boomsticks 1.2, GeckoLib 4.8.4, Epic Knights 10.11, Architectury 9.2.14, and Cloth Config 12.0.109.
Both used Artillery 1.13.4. The minimum corner disables the optional Epic Knights Addon and
BetterRecruitFormations runtime stress dependencies: their own metadata requires Epic Knights 10.8
and Recruits 1.15.1 respectively, so including them would test those addons' stricter ranges instead
of this mod's declared minimums. Separate current-tree runs without Artillery also passed all 133
tests on GeckoLib 4.2.4, 4.4, and 4.4.9.

Two model defects surfaced the moment those four tests actually executed, and both were expectations
in the tests rather than in the adapters: the Tiller Gun test denied the `loaded` flag its native
ramming step writes, and the Noble Handgonne test demanded one its native Arrow branch never writes.
The bytecode settles both, and the profiles already matched it; only the assertions were stale. That
is the concrete cost of a dependency-gated test — it passes without ever checking anything.

## Historical status: thirteen verified, four gated (superseded)

The Arquebus, Matchlock Musket, Matchlock Carbine standing and `fork_rest` branches, Matchlock Pistol, Toradar Rifle, Mini Pistola, Handgonne, Taccola Handgonne iron-ball branch, Hand Cannon three-ball volley branch, Markmengonne Arrow-branch, both Harquebus branches, Windlass Crossbow, Chu Ko Nu eight-round repeater, and the whole throwing-weapon family — Francisca, Hurlbat, Throwing Cross, Javelin, and throwable cobblestone — are complete and runtime-verified when registered. Arquebus was already present in `HEAD` at `0a20856`; the later slices were added in the working tree. The Tiller Gun, Noble Handgonne, Hackbut, and Double Barrel Gonne profiles/state/AI slices remain gated in the pinned server-safe 1.11 run because that artifact does not register those items. They are now also executed against Artillery 1.14.0 through the development-only dedicated-server patch; this test-only proof does not make the unpatched 1.14 artifact server-safe or part of the release support claim.

## Completed slices

- The enabled Artillery gameplay boundary is intentionally limited to eighteen runtime-verified firearm profiles and five runtime-verified throwing weapons:

- `artillery_addon:arquebus`;
- `artillery_addon:matchlock_musket`;
- `artillery_addon:matchlock_carbine` standing branch with an empty offhand and native `fork_rest` branch with `artillery_addon:fork_rest`; other non-empty offhands are rejected;
- `artillery_addon:matchlock_pistol`;
- `artillery_addon:toradar_rifle`;
- `artillery_addon:mini_pistola`;
- `artillery_addon:tiller_gun` when the registering Artillery artifact is present;
- `artillery_addon:handgonne` when registered by the runtime artifact;
- `artillery_addon:taccola_handgonne` iron-ball branch;
- `artillery_addon:markmengonne` iron-ball branch with Arrow fallback when registered by the runtime artifact;
- `artillery_addon:bronze_handgonne` iron-ball branch, routed explicitly because upstream omits it from `#artillery:guns`;
- `artillery_addon:noble_handgonne` iron-ball and Arrow branches, preferring Iron Ball when both loading paths are available;
- `artillery_addon:harquebus` standing branch with an empty offhand and native `fork_rest` branch with `artillery_addon:fork_rest`; other non-empty offhands are rejected;
- `artillery_addon:hackbut` ordinary/no-`fork_rest` branch when registered by the runtime artifact; the native `fork_rest` branches remain disabled;
- `artillery_addon:windlass_crossbow` Arrow branch;
- `artillery_addon:chu_ko_nu` native eight-round `ammo` counter, filled one physical arrow per round exactly as the native procedure does; native firing cadence remains this project's NPC cooldown policy;
- `artillery_addon:double_barrel_gonne` first-barrel `iron_ball` branch when registered by the runtime artifact; the second barrel, multi-shot operation, and other ammunition branches remain disabled;
- `artillery_addon:francisca`, `artillery_addon:hurlbat`, `artillery_addon:throwing_cross`, `artillery_addon:javelin`, and `artillery_addon:throwable_cobblestone`, each consuming one physical held item per successful native `francisca_pro`, `hurlbatpro`, `throwing_cross_pro`, `javelin_proectile`, or `throwcobble` spawn without a reload or invented loaded marker;
- `artillery_addon:iron_ball` for the eight full-size profiles;
- `artillery_addon:small_iron_ball` for Mini Pistola and Tiller Gun;
- `artillery_addon:large_iron_ball` for Hackbut;
- `minecraft:arrow` for the Markmengonne, Noble Handgonne fallback, Windlass Crossbow, and Chu Ko Nu NPC policy branches, plus `artillery_addon:iron_ball` for the Noble and Markmengonne preferred ball branches and Bronze Handgonne;
- `artillery_addon:ironball_projectile` / `IronballProjectileEntity`;
- explicit registry-ID routing through `SupportedArtillery` and `RecruitWeaponAdapters`;
- native item state (`powder` and `stage` as NBT doubles where the native procedure uses them, plus the compatibility reload marker); Windlass Crossbow uses native `stage=4.0`/`stage=0.0` without inventing `powder`; Noble Handgonne preserves `ammo=0.0`, `loaded=true` for Iron Ball and `ammo=2.0` without a `loaded` flag for Arrow; Taccola preserves native double `ammo=0.0` alongside `stage=2.0`/`stage=3.0` and `loaded=true`/`false`; Chu Ko Nu preserves only the native double `ammo=1.0`/`ammo=0.0` boundary without inventing `stage`, `powder`, or `loaded`; and Double Barrel Gonne preserves only the first-barrel native double `barrel_one=2.0`/`rammed_one=1.0`/`loaded=1.0` boundary with the second barrel held at zero;
- one physical native ball consumed per reload, independently of Recruits' vanilla-arrow setting;
- server-side native projectile creation with the recruit as owner;
- existing recruit targeting, ownership, allied-projectile protection, cooldown, and animation cleanup;
- global and Artillery-specific compatibility kill-switches.

Handgonne uses one physical `iron_ball`, the native stage-two `loaded` flag, and the native Ironball projectile. The adapter deliberately collapses the player-only arrow/ramrod/match sequence into one NPC reload transaction; this does not claim full player transaction parity.

Taccola Handgonne enables only its confirmed iron-ball branch: one physical `iron_ball` is consumed, the NPC path commits `powder=1.0`, `stage=2.0`, native double `ammo=0.0`, and `loaded=true`, and firing creates one `IronballProjectileEntity` before leaving `powder=0.0`, `stage=3.0`, `ammo=0.0`, and `loaded=false`. The normal branch uses inaccuracy `9.0`; the confirmed shift branch uses `6.5`. Its other Shatter Shot, Iron Bit, and Arrow branches remain disabled, and the player-only multi-step transaction is collapsed into one NPC reload transaction.

Double Barrel Gonne enables only the first-barrel iron-ball branch: one physical `iron_ball` is consumed, the NPC path commits native double `barrel_one=2.0`, `rammed_one=1.0`, `barrel_two=0.0`, `rammed_two=0.0`, and `loaded=1.0`, and firing creates one native `IronballProjectileEntity` before clearing those markers to zero. The normal branch uses inaccuracy `9.0`; the confirmed shift branch uses `4.5`. The projectile preserves base damage `2.7`, knockback `1`, critical `true`, pierce level `1`, audible (`silent=false`) state, and pickup disallowed. The second barrel, multi-shot operation, other ammunition branches, and player-only powder/ramrod transaction remain disabled. Its two Forge GameTests are dependency-gated because the pinned 1.11 artifact does not register `double_barrel_gonne`.

Markmengonne enables its native iron-ball branch and Arrow branch. The preferred ball path consumes one physical `iron_ball`, walks powder/ball/ramming to `stage=2.0` with native double `ammo=0.0` and no invented `loaded` flag, and fires one native `IronballProjectileEntity` at base damage `2.9`, velocity `4.5`, and inaccuracy `7.0` normally or `3.3` while shifting. The Arrow fallback preserves native double `ammo=2.0` and fires one marked vanilla `Arrow`. Shatter Shot and Iron Bit remain disabled.

Bronze Handgonne enables its iron-ball branch despite being absent from the upstream guns tag. It consumes one physical `iron_ball`, reaches native `stage=2.0`, `ammo=0.0`, `loaded=true`, and fires one native `IronballProjectileEntity` at base damage `1.85`, velocity `4.5`, and inaccuracy `9.0` normally or `4.5` while shifting. Firing leaves native `stage=3.0` and `loaded=false`; its other ammunition branches remain disabled.

Matchlock Carbine enables both confirmed single-projectile branches: one physical `iron_ball` is consumed, the native stage-three load boundary its own chain reaches (`stage=1.0`, `2.0`, `3.0`) is committed without inventing a `loaded` flag, and firing creates one native `IronballProjectileEntity`. An empty offhand selects the standing branch (`velocity=6.5`, `inaccuracy=6.0`); `artillery_addon:fork_rest` selects the native rest branch (`velocity=6.5`, `inaccuracy=3.5`). Both branches preserve base damage `2.7`, knockback `1`, silent `true`, critical `false`, and `AbstractArrow.Pickup.DISALLOWED`. Any other non-empty offhand is rejected before projectile creation, matching the native procedure's branch gate.

Harquebus enables both confirmed single-projectile branches: one physical `iron_ball` is consumed, the native stage-three load boundary its own chain reaches (`stage=1.0`, `2.0`, `3.0`) is committed without inventing a `loaded` flag, and firing creates one native `IronballProjectileEntity`. The empty-offhand standing branch uses velocity `5.5` and inaccuracy `6.0`; `artillery_addon:fork_rest` uses the same velocity and inaccuracy `3.5`. Both preserve base damage `2.2`, knockback `1`, silent `true`, critical `false`, and pickup disallowed. Firing leaves the bytecode-confirmed native `powder=0.0`, `stage=0.0` state; other non-empty offhands are rejected before projectile creation.

Noble Handgonne enables both its native `ammo=0.0` iron-ball branch and `ammo=2.0` Arrow branch. The ball path consumes one physical `iron_ball`, walks the native powder/ball/ramming chain through stage two with `loaded=true`, and fires a native Ironball projectile. The Arrow path consumes one physical `minecraft:arrow`, jumps straight to stage two without a `loaded` flag, and fires a marked vanilla `Arrow`. Recruits prefer an iron ball when both are available. Shatter Shot and Iron Bit remain disabled.

Hackbut enables only the ordinary/no-`fork_rest` branch: one physical `large_iron_ball` is consumed, the native stage-two boundary is committed without inventing a `loaded` flag, and firing creates one native `IronballProjectileEntity` with velocity `6.5`, inaccuracy `5.5`, base damage `5.25`, critical `true`, pierce level `1`, and audible (`silent=false`) projectile state. The adapter bypasses the player-only shift gate; native `fork_rest` branches remain disabled. The pinned 1.11 artifact does not register Hackbut, so its GameTests are dependency-gated.

Windlass Crossbow enables only the confirmed Arrow branch: one physical `minecraft:arrow` is consumed, the recruit weapon is committed at native `stage=4.0` without a powder marker, and firing creates one marked vanilla `Arrow`, finishing at native `stage=0.0`. The projectile preserves base damage `2.6`, velocity `3.5`, inaccuracy `1.0`, critical `true`, knockback `1`, pierce level `0`, audible (`silent=false`) state, and `AbstractArrow.Pickup.ALLOWED`. The player-only multi-step use transaction is collapsed into one NPC reload transaction; this does not claim full player transaction parity.

Chu Ko Nu enables the full native repeater counter: `ChuKoNuShootProcedure` accepts one physical `minecraft:arrow` per round and walks the native double counter from `1.0` up to `8.0` with the lore `§7Ammo N/8`, and the recruit chain reproduces that step by step. Each shot creates one marked vanilla `Arrow` and spends exactly one native round back down to `0.0`, so the weapon stays loaded between rounds of a magazine and reloads only once the counter reaches zero. Every round preserves base damage `1.6`, velocity `2.4`, inaccuracy `0.5`, critical `true`, knockback `1`, pierce level `0`, audible (`silent=false`) state, and `AbstractArrow.Pickup.ALLOWED`. A partial magazine is refused rather than partially loaded, and counter values outside `1.0`-`8.0`, fractional values, and non-double NBT types are treated as unloaded. The interval between rounds is this project's NPC cooldown policy; native firing cadence and player-only use/finishUsingItem behavior remain disabled.

### Throwing weapons

All five Artillery throwing weapons are enabled and runtime-verified. They share one native shape, so
they share one adapter: each item exposes `finishUsingItem(ItemStack, Level, LivingEntity)`, which
delegates to its own `...PlayerFinishesUsingItemProcedure`, while the charge-based `releaseUsing`
branch is `ServerPlayer`-only and unreachable for an NPC. Every one of those procedures builds its
projectile identically — `createArrowWeaponItemStack(entity, 1, 0)`, then
`initArrowProjectile(arrow, thrower, baseDamage, silent=true, critical=false, fire=false, pickup)`,
then `setPos(x, eyeY - 0.1, z)` and `shoot(look.x, look.y, look.z, velocity, inaccuracy)` — and then
spends one held item with `shrink(1)`. Only the per-weapon numbers differ:

| Weapon | Native use window | Base damage | Velocity | Inaccuracy | Native recovery roll |
|---|---|---|---|---|---|
| `artillery_addon:francisca` | 15 | `5.5` | `1.5` | `1.9` | `0.7` |
| `artillery_addon:hurlbat` | 14 | `6.5` | `1.2` | `2.0` | `0.4` |
| `artillery_addon:throwing_cross` | 13 | `4.5` | `1.4` | `1.7` | `0.4` |
| `artillery_addon:javelin` | 40 | `2.0` | `3.5` | `2.2` | `0.75` |
| `artillery_addon:throwable_cobblestone` | 30 | `15.0` | `0.75` | `0.0` | none |

Each held item is immediately ready, so the recruit never enters firearm reload state; the combat goal
instead preserves that weapon's own uninterrupted native use window before each throw. Exactly one
item is shrunk from the held stack, and only after the registered native projectile has been accepted
by the server level. Every projectile keeps knockback `1`, silent, non-critical, non-piercing, and no
fire, with the recruit as owner. Friendly-fire and lifetime hooks claim the five native projectile
classes when owned by a recruit.

Native pickup mode differs between artifacts and is resolved rather than assumed. Artillery 1.11
passes `AbstractArrow.Pickup.DISALLOWED` for every throwable and discards the entity in its own
`onHitBlock` recovery roll; Artillery 1.14 passes `ALLOWED` and leaves the embedded projectile
collectible. Most classes carry a marker for that split — the 1.14 shape declares `isStuckInGround`,
the 1.11 shape declares its own `onHitBlock` — so they are answered directly. `JavelinProectileEntity`
declares neither in 1.14, and `ThrowcobbleEntity` is identical in both artifacts, so those two fall
back to an artifact-wide probe over the classes that do carry the marker, defaulting to the pinned
1.11 behaviour when none can be resolved.

The throwable cobblestone has no block-impact recovery procedure in either artifact, so its profile
records no recovery roll rather than inventing one.

Both of those resolutions are keyed on someone else's binary, so neither fails silently. A weapon
whose native projectile entity is unregistered, or whose class no longer has the expected shape,
refuses the shot and reports that refusal once per weapon; an artifact where no throwable class can
be resolved at all logs that it is falling back to the pinned 1.11 pickup behaviour. Without those
lines a repackaged artifact would look exactly like a recruit that simply never throws.

#### Reach and the wind-up

Two NPC behaviours follow from those velocities and are this project's policy rather than native
code.

A recruit closes the distance instead of throwing short. `ArtilleryAddonAdapter.aimVector` pays for
the projectile's drop with an upward arc of `g/2 * t²`, clipped at eight blocks; past the distance
where that clip bites, the lead is no longer enough and the throw lands short no matter how long the
recruit aims. `ArtilleryAddonAdapter.maxCompensatedRange` inverts that cap — reach is
`velocity * sqrt(2 * 8 / 0.05)` — and the combat goal clamps the shared 45-block combat range down to
it per weapon. A thrown cobblestone therefore reaches about 13 blocks and a Hurlbat about 21, so the
recruit walks in; a Javelin at velocity `3.5` outruns the shared range and keeps it unchanged. A
weapon may only shorten the range, never extend it, and an unusable report falls back to the shared
maximum rather than pinning a recruit in place. Firearms are unaffected: every enabled firearm
profile is fast enough to clamp back to the shared maximum.

The raised arm is the wind-up, not the carry. The throwing pose is now driven by the aim window
instead of by merely holding the weapon, so a recruit walking into range carries its Hurlbat at its
side and cocks it back only once it has a shot it can take, dropping it again on the throw. The
marker lives on the weapon stack, like the firearm reload marker, because the client renders the pose
from the held item. It deliberately does not go through vanilla `startUsingItem`: these items
implement `finishUsingItem`, so a completed vanilla use would run the native throw procedure a second
time and spend another item.

Artillery's shapeless recipe converts `magistuarmoryaddon:steel_francisca_axe` plus a steel nugget
into `artillery_addon:francisca`; the Epic Knights Addon axe remains a normal melee
`MedievalWeaponItem` and is not claimed directly.

The player-only charge-based throw and its `BowItem.getPowerForTime` scaling remain outside the
enabled NPC policy slice. None of these five items gates its `use()` behind a procedure, so nothing
is bypassed to reach the full-use branch.

### Grenades are deliberately out of scope

The grenade family — `artillery_addon:clay_hand_grenade`, `artillery_addon:iron_hand_grenade`,
`artillery_addon:fire_bomb`, and `artillery_addon:lime_bomb` — is excluded by decision rather than by
omission, and the exclusion is not expected to be revisited without a separate area-weapon design.

Their item shape is usable by an NPC: like the Francisca they expose
`finishUsingItem(ItemStack, Level, LivingEntity)`, which delegates to a per-item
`...PlayerFinishesUsingItemProcedure`, while the charge-based `releaseUsing` path is `ServerPlayer`-only.
The blocking problem is the impact chain. `ClaynadeProProjectileHitsBlockProcedure` and its siblings
play `entity.creeper.primed`, queue a 45-tick fuse through `ArtilleryAddonMod.queueServerWork`, and
then call:

```text
Level.explode(null, x, y, z, 3.0F, Level.ExplosionInteraction.NONE)   // clay
Level.explode(null, x, y, z, 4.0F, Level.ExplosionInteraction.NONE)   // iron
```

The explosion source is `null`. The projectiles themselves extend `AbstractArrow` and do carry an
owner, so the flight phase would be attributable, but the damage is dealt by an ownerless explosion.
This project's allied protection is keyed on projectile ownership and therefore cannot cover it: a
recruit would damage its own squad and its owner, and the blast could not even be distinguished from
a player's grenade or a creeper. Covering it would require matching an ownerless
`ExplosionEvent.Detonate` back to a recruit's spent projectile by position and tick window, which is
a deliberate deviation from native behaviour rather than a reproduction of it.

Two further mismatches are independent of that: the 45-tick fuse gives a recruit time to walk into
its own blast, and an area-denial weapon needs minimum-engagement-range and ground-aim targeting that
the current single-target combat goal does not model. `FireBombProProjectileHitsBlock2Procedure`
additionally applies `setSecondsOnFire(100)` over its area, which is worse for allies than the blast.

No code change is needed to hold this boundary. `SupportedArtillery.profileFor` and
`SupportedArtilleryThrowables.profileFor` are explicit allowlists that return `Optional.empty()` for
every other identifier, so a grenade carried by a recruit is treated as an ordinary item: it is never
selected as a weapon, never enters reload state, and never reaches an adapter.

The other Artillery weapon profiles remain catalog/reference data only and are not enabled.

## Multi-step native loading chains

`ArquebusRightclickProcedure` and `MatchlockRifleRightclickProcedure` were disassembled to recover the real loading transaction rather than only its end state. Both use the same three-step chain:

| Step | Required component | Native result | Step sound | Native lore |
|---|---|---|---|---|
| 1 | tag `minecraft:powder_flask` | `powder=1.0`, `stage=0.0` | none | `Needs shot` |
| 2 | `artillery_addon:iron_ball` | `stage=1.0` | `artillery_addon:arquebus_ball` | `Needs to be rammed` |
| 3 | tag `artillery:ramrod` | `stage=2.0` | `artillery_addon:arquebus_ramming` | `Ready to fire` |

The bytecode also settles who pays for each step: the flask and the ramrod are damaged through `ItemStack.hurt(1, ...)` and removed only when that damage breaks them, while the ball is spent outright with `shrink(1)`. Firing then damages the weapon itself, which this project already reproduced.

One deviation is forced and is recorded here deliberately. The native procedures read the tool from the main hand and write weapon state to the **off hand**, so a player holds the gun in the off hand while cycling tools in the main hand. A recruit must keep its weapon in the main hand for Recruits' own weapon selection, targeting, and animation, so this project mirrors the hands: the weapon stays in the main hand and each tool is shown in the off hand. Everything else — required component, consumption or durability cost, native stage values, step sounds, and display lore — is reproduced exactly.

Two implementation boundaries were discovered through runtime failures rather than assumed:

- a recruit's equipment slots are backed by its own inventory slots (`AbstractInventoryEntity.getInventorySlotIndex`), so the borrowed tool is **moved** between inventory slots instead of copied into the equipment slot; a display copy duplicated the item;
- a chain that has already spent components must be allowed to finish. The ball is gone by the middle step, so a plain inventory ammo check abandoned a half-loaded weapon at `stage=1.0`.

The Handgonne chain was captured the same way from `TestgunRightclickedProcedure`. It uses the same three steps with the `hand_cannon_*` sounds, an audible powder step the matchlocks lack, native `stage=0.0`/`powder=1.0`, then `stage=1.0` with the double `ammo=0.0` ball marker, then `stage=2.0` with `loaded=true` committed by the ramming step. Two further facts came out of that disassembly: the native `MATCH` step is guarded by `stage==2.0`, so it ignites an already loaded weapon and belongs to the firing path rather than the loading chain, and the native Arrow branch skips ramming entirely by jumping straight to `stage=2.0`.

Every remaining enabled weapon was then disassembled the same way, so all eighteen gameplay profiles now walk a captured chain. The per-weapon table is in `docs/compat/artillery-addon-1.14.0-api.md`; the loading branches are identical in the 1.14.0 and the pinned 1.11 artifacts, only jump offsets differ. What that pass corrected, against the binary:

- **the Carbine and the Harquebus load one stage higher than the rest of the matchlock family.** Their chains run `stage=1.0`, `2.0`, `3.0` and their firing branches check `stage==3.0`; this project previously modelled them at `stage=2.0`;
- **several ramming branches are bare-handed.** `CarbineRightclickProcedure` and `HarquebusRightclick2Procedure` compare the main hand against `ItemStack.EMPTY.getItem()` and accept nothing else; Handgonne, Taccola, Hand Cannon, and Matchlock Pistol test the bare hand first and only then the `artillery:ramrod` tag; Tiller Gun accepts a bare hand or the powder flask. A recruit therefore never spends a ramrod the native code would not have spent;
- **Chu Ko Nu's native magazine is eight rounds, not three.** `ChuKoNuShootProcedure` both fires and reloads: it accepts one `minecraft:arrow` per round, walks `ammo` from `1.0` to `8.0`, and writes the lore `§7Ammo N/8`. The recruit chain is one arrow per step, which is the native transaction rather than the previous one-shot policy;
- **the Windlass Crossbow cocks in four steps.** `WindlassCrossbowPlayerFinishesUsingItem2Procedure` walks `stage` `1.0`, `2.0`, `3.0` bare-handed with `item.crossbow.loading_start`/`loading_middle`, then takes the arrow at `stage=4.0` with `loading_end`. The native procedure only requires the arrow in the inventory and spends it on the shot; the recruit path spends it at the last cocking step, which remains a documented NPC policy;
- **one native volley costs one ball.** `HandcannonRightclickProcedure` shrinks a single `IRON_BALL` before spawning its three projectiles, so reload cost now comes from the chain (`ArtilleryReloadProtocol.ammoConsumed`) instead of from `projectileCount`;
- **two sounds and two markers were wrong.** The Matchlock Musket's ball step plays `hand_cannon_load_ball`, not `arquebus_ball`; the Arquebus powder step is audible; the Tiller Gun ramming step writes `loaded=true`; and the Noble Handgonne Arrow branch never writes a `loaded` flag, so the profile no longer claims one;
- **`ToccolaRightclickedProcedure` has no powder-flask guard on its first step at all.** The recruit path still requires a flask, which is stricter than the native branch and keeps the transaction recognisable.

One more implementation boundary came from a runtime failure: a consumed component must be split one item at a time into the off hand. Moving a whole arrow stack in and out of the off hand each step lost one arrow per Chu Ko Nu magazine.

Two presentation defects were found by playtesting rather than by tests:

- recruits fired visibly into the ground. Shots aimed straight at a point a third up the target's height — below the recruit's own eyes — with no lead, while every projectile here extends `AbstractArrow` and falls at 0.05 blocks per tick squared. Shots now add the drop expected over the projectile's flight time, which is self-calibrating across the very different muzzle velocities in the catalog and matched the measured drop within a few centimetres in-game;
- `RecruitVillagerRenderer.getArmPose` and its human-model counterpart reach `CROSSBOW_HOLD` only when `stack.is(Items.CROSSBOW)` **and** the stack is charged, or when `IWeapon.isMusketModWeapon` matches — and that helper compares `ItemStack.getDescriptionId()` against a hardcoded list of Musket Mod identifiers. A client-side mixin on both renderers therefore supplies integration-specific poses. Artillery remains shouldered when loaded and uses the charging pose during reload. Medieval Boomsticks is deliberately different: the recruit keeps `ArmPose.ITEM` while empty or reloading, uses a lowered model-arm charge motion, and selects `CROSSBOW_HOLD` only from the native `Charged` state. Firing clears that state and lowers it again. Binary inspection also found that every Boomsticks `GeoItemRenderer.actuallyRender` calls `handleAnimations` only when `ItemDisplayContext.firstPerson()` and resets its bones in third person. A renderer mixin temporarily selects the animation branch only while `recharge=true`; the hand transform was already applied before that boundary, so placement is unchanged while native `handgonne.reload`, `arquebus.reload`, and `arbalest.reload` clips become visible. The off-hand item render is separately given the right-hand display transform so stowed and drawn Boomsticks share one orientation.

Every enabled gameplay weapon now has a captured chain. The single-transaction NPC reload remains in the adapter only as the documented fallback for a profile without one.

## Verification checkpoint record

The Matchlock Musket, Matchlock Carbine, Matchlock Pistol, Toradar Rifle, Mini Pistola, Tiller Gun, Handgonne, Bronze Handgonne, Taccola Handgonne iron-ball branch, Noble Handgonne iron-ball/Arrow branches, Markmengonne iron-ball/Arrow branches, both Harquebus branches, Windlass Crossbow, Chu Ko Nu, and Double Barrel Gonne first-barrel iron-ball branch passes all reuse the server-safe Artillery adapter/state/AI boundary and preserve their own confirmed ammunition, native state, projectile, and launch properties. The Matchlock Carbine and Harquebus passes cover both the empty-offhand standing branch and the `fork_rest` offhand branch, including rejection of unrelated non-empty offhands. The Hackbut pass adds only the ordinary/no-`fork_rest` branch with physical `large_iron_ball`, native stage-two state without a loaded flag, one native Ironball projectile, velocity `6.5`, inaccuracy `5.5`, base damage `5.25`, critical `true`, pierce level `1`, and silent `false`; its player-only shift gate is bypassed for the NPC path and its fork-rest branches remain disabled. The Double Barrel Gonne pass adds only the first-barrel branch with native double `barrel_one`/`rammed_one`/`loaded` state, one physical `iron_ball`, and one critical/piercing/audible native Ironball projectile. The Chu Ko Nu pass covers the full native repeater counter: eight physical arrows committed one round at a time, one round and one marked vanilla Arrow per shot back down to `0.0`, a refused shot on an empty magazine, and a combat-goal run proving no second reload occurs between rounds of the same magazine. Each slice has focused profile/state coverage and Forge GameTests.

The throwing-weapon pass uses a separate self-consuming throwable adapter rather than pretending the
items are loaded firearms. All five weapons walk one shared native branch, so each has one direct
GameTest validating its native projectile type and full-use profile, one-item atomic spend, recruit
ownership, and allied-hit rejection, plus one combat GameTest proving the same item is selected and
thrown by `RecruitBoomstickAttackGoal` without entering passive reload and only after that weapon's
own native use window has elapsed. The pinned 1.11 artifact registers all five items and all five
projectile entities, so none of these ten tests is dependency-gated.

The following evidence combines the 2.0.0 release verification with the latest regression pass:

- focused Artillery unit tests: `./gradlew.bat test --tests '*Artillery*' --rerun-tasks --console=plain` — `BUILD SUCCESSFUL`;
- full unit-test result: no failures, errors, or skips, including the native reload-chain, formation-fire coordination, target-facing, reload-animation clock, inventory-safety, and damage-policy contracts;
- real Forge GameTest server without Artillery Addon: `-Partillery=none` starts without the optional dependency or the development-only patch and passes all `119/119` required tests, proving the advertised optional-install boundary;
- multi-step loading coverage: the Arquebus chain test asserts the native powder and ball stages are actually passed through, that a tool is visible in the off hand while the chain runs, that the weapon ends loaded with the off hand restored, and that the flask and ramrod are neither consumed nor duplicated; a second test proves a missing ramrod refuses the chain instead of starting one it cannot finish;
- real Forge GameTest server with the default unpatched Artillery 1.13.4 artifact: `133 tests are now running!` followed by `All 133 required tests passed :)`; all eighteen firearm profiles and all five throwing weapons are registered and execute;
- earlier matrix evidence remains recorded for Artillery 1.11 through 1.13.4 and the development-only patched 1.14 run; the unpatched 1.14 server boundary is unchanged;
- the residual Hand Cannon combat flake was fixed at both random boundaries: the test resets the recruit RNG before each manually driven goal tick so the native misfire branch has a deterministic roll, and its still-attackable target receives a large health pool so projectiles from concurrently running GameTests cannot kill it before the firing phase. Two consecutive combined-stress runs passed `113/113` after the fix, followed by final no-Artillery, pinned-1.11, combined-stress, and patched-1.14 passes;
- the Arbalest ballistic test no longer requires one exact random-inaccuracy magnitude: it asserts the stable native-style contract that a long-range shot has positive upward velocity;
- dependency resolution: `curse.maven:epic-knights-artillery-addon-1307540:8252195_mapped_official_1.20.1` on the default `runtimeClasspath`;
- clean release build: `./gradlew.bat clean build --rerun-tasks --console=plain` — `BUILD SUCCESSFUL`;
- reproducible release packaging: two JAR runs produced byte-identical `recruits_use_boomsticks-2.0.0.jar` artifacts. The final artifact is `309030` bytes with SHA-256 `77F260615BEA9E3BECF9A912E13C0A72E0B680B638C1787F12242934C151B3C6`. It reports version `2.0.0` in both `mods.toml` and the manifest, declares the optional Artillery range `[1.11,1.14)`, contains no nested dependency JARs, and contains no development Artillery patch entries;
- `git diff --check` passed.

The implementation pass did not commit or push. The untracked stale audit was left in place and was not used as implementation evidence.

## Verification commands

Run from the repository root:

```text
./gradlew.bat test --rerun-tasks --console=plain
./gradlew.bat runGameTestServer -Partillery=none --rerun-tasks --console=plain
./gradlew.bat runGameTestServer --rerun-tasks --console=plain
./gradlew.bat runGameTestServer -Pstress=all --rerun-tasks --console=plain
./gradlew.bat runGameTestServer "-Partillery=1.14" --rerun-tasks --console=plain
```

The unit-test task completed successfully with no failures. The default unpatched Artillery 1.13.4 profile and the no-Artillery profile both completed the current 119 required GameTests. Older stress, version-range, and patched-1.14 runs used smaller then-current matrices and remain historical evidence rather than a claim that those exact commands were repeated after the latest regression tests were added:

```text
133 tests are now running!
All 133 required tests passed :)
```

The GameTest coverage includes Arquebus, Matchlock Musket, Matchlock Carbine standing and `fork_rest` branches, Matchlock Pistol, Toradar Rifle, Mini Pistola, Handgonne, Taccola Handgonne iron-ball branch, Markmengonne, both Harquebus branches, Windlass Crossbow, Chu Ko Nu, the Double Barrel Gonne first-barrel policy, and all five throwing weapons: pickup, native state where applicable, exact physical-ammunition or held-weapon consumption, server-side firing, native/marked projectile ownership, friendly-fire filtering, combat-goal integration, and both compatibility switches. The throwing-weapon tests execute against the real pinned native item/entity registry and prove one-item spend, projectile properties, ownership, allied-hit rejection, no invented reload, and combat-goal handoff for the Francisca, Hurlbat, Throwing Cross, Javelin, and throwable cobblestone alike. The Chu Ko Nu combat test is driven on real server ticks rather than in a single tick, because the inter-round cooldown is measured against level game time; it counts rounds off the native magazine counter rather than off live projectiles, because a recruit may pick its own fired arrows back up. Tiller Gun, Noble Handgonne, Hackbut, and Double Barrel Gonne tests are dependency-gated against the pinned artifact and executed against 1.14.0. Throwable pickup mode is asserted from whatever the installed artifact declares rather than pinned to one release, so the same test proves `DISALLOWED` under 1.11 and `ALLOWED` under 1.14.

## Running the gated tests

Artillery 1.14.0 registers all seventeen guns, but upstream it cannot start a dedicated server. The
cause is one class:

```text
RuntimeException: Attempted to load class net/minecraft/client/multiplayer/ClientLevel
        for invalid dist DEDICATED_SERVER
    at AutomaticEventSubscriber.inject(AutomaticEventSubscriber.java:48)
    Failed to register automatic subscribers. ModID: artillery_addon
```

`net.mcreator.artilleryaddon.gun_maker.GunMaker$Events` carries a bare `@Mod.EventBusSubscriber` —
no `Dist` filter — while declaring `onEventTriggered(RenderPlayerEvent.Pre)` and `onClientTick`,
neither marked `@OnlyIn(Dist.CLIENT)`, unlike its own `onClientMouseTick`, which is. FML therefore
loads the class on a dedicated server and Forge's dist cleaner refuses what verifying it drags in.
The `gun_maker` package does not exist at all in the 1.11 artifact, which is exactly why that one
boots a server. The upstream fix is one annotation.

`recruits_use_boomsticks.artillery-dev.mixins.json` removes those two listeners through a Mixin
config plugin, which lets the test server start:

```text
./gradlew.bat runGameTestServer "-Partillery=1.14" --console=plain
```

Its boundaries are deliberate. `onPlayerTick` is kept, because it runs on both dists and Artillery's
own 1.14 Handgonne, Noble Gonne, and Bronze Gonne procedures call into `GunMaker`. The config is
registered for development runs only and is absent from the release jar's `MixinConfigs` manifest
entry, and the plugin additionally refuses to apply outside a dev dedicated server. Shipping it
would make this project answerable for Artillery's server stability, which it cannot be: this
removes the first client-only leak in that artifact, not every one it may contain.

One 1.14-only difference is recorded rather than modelled: `TestgunRightclickedProcedure` also
writes `GunMaker$GunUtils.IS_AIMING` onto the stack, a marker the 1.11 Handgonne chain has no
equivalent for. It is aiming display state and does not affect the loading transaction.

## Test-environment limitations

The executable validation uses the pinned Artillery Addon 1.11 artifact available to the ForgeGradle userdev run. Its data contains two recipes that reference IDs from a missing companion namespace:

- `artillery_addon:throwing_francisca_recipe` -> `magistuarmoryaddon:steel_francisca_axe`;
- `artillery_addon:firelancerecipe_0` -> `magistuarmoryaddon:steel_lance`.

Forge reports those upstream recipe parsing errors while continuing to load the test server. The
Francisca item and projectile are nevertheless registered and their runtime GameTests execute. Epic
Knights: Addon is optional and is not forced into the harness merely to satisfy the conversion
recipe: its available 1.20.1 artifacts contain separate upstream tag/smithing errors unrelated to
this adapter. The registered Artillery GameTests pass, while the Tiller Gun, Noble Handgonne,
Hackbut, and Double Barrel Gonne tests are skipped by their explicit registry prerequisites because
this artifact predates those items. This environment must not be described as a clean Artillery
datapack run; dependency JARs are not modified and unrelated recipes are not silently removed.

The 1.14.0 artifact (`8325660`) is resolvable in this checkout and was confirmed to contain `TillerGunItem`, `NobleHandgonneItem`, `HackbutItem`, and `DoubleBarrelGonneItem`, with all seventeen guns listed in `data/artillery/tags/items/guns.json` and the `iron_ball`, `small_iron_ball`, `large_iron_ball`, and `fork_rest` ammunition items present. `build.gradle` exposes it as the opt-in `-Partillery=1.14` runtime file. Client runs use the artifact directly; dedicated GameTests require this project's development-only patch and therefore do not establish an unpatched server-support claim.

The unpatched Artillery 1.14 artifact is not claimed as dedicated-server validated. A direct `runGameTestServer` attempt with `8325660_mapped_official_1.20.1` failed before the test server started: Forge reported `Attempted to load class net/minecraft/client/multiplayer/ClientLevel for invalid dist DEDICATED_SERVER` while registering Artillery automatic subscribers. The development-only patch permits compatibility GameTests but is absent from the release JAR. The release server-side gameplay claim therefore remains pinned to the known-loadable 1.11 artifact.

## Compatibility stress profiles

The optional `-Pstress` profiles add runtime-only mods to isolated run directories. They do not enter
the release JAR or `mods.toml`:

| Profile | Added runtime mods | Dedicated GameTest result |
|---|---|---|
| `baseline` | none | pinned Artillery 1.11, `113/113` |
| `musket` | Musket Mod | pinned Artillery 1.11, `113/113` |
| `combat` | Better Combat, Player Animator, Epic Knights Addon | pinned Artillery 1.11, `113/113` |
| `optimization` | ModernFix, FerriteCore, AI Improvements | pinned Artillery 1.11, `113/113` |
| `all` | all seven mods above | pinned Artillery 1.11, `113/113`; Artillery 1.14 with the development-only patch, `113/113` |

The Musket Mod run found one real goal conflict. Recruits installs
`RecruitRangedMusketAttackGoal` at the same priority as `RecruitBoomstickAttackGoal`; when the recruit
held a Musket Mod musket and had a supported boomstick in storage, the musket goal claimed the goal
flags before the compatibility goal could draw the boomstick. `RecruitRangedMusketAttackGoalMixin`
now makes that goal yield only while global compatibility is enabled and a supported enabled
boomstick is available. `boomstickTakesPrecedenceOverInstalledMusketModWeapon` executes only when
Musket Mod is installed and proves the goal handoff.

The combined Artillery 1.14 client reached a fully loaded main menu in 15.282 seconds with the normal
client Mixins, Better Combat, Player Animator, and both compatibility adapters loaded. The interactive
Gradle task was then ended by the smoke-test timeout; no crash report was produced. Artillery 1.14
reported its own invalid oil/wall-gun/ribauldequin model data, and Epic Knights Addon reported two
missing template tags plus two legacy smithing recipes. The patched dedicated run also reported
Artillery's invalid guide-book function. These upstream resource errors did not prevent startup or
the `113/113` GameTest result, but the run must not be described as a clean datapack/resource load.
