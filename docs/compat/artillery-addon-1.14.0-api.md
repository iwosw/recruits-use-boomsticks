# Epic Knights: Artilleries and Firearms 1.14.0 binary API report

This report records the compatibility boundary inspected for Recruits Use Boomsticks. It is based on the exact resolved binary artifact and `javap`; it does not contain decompiled upstream source.

> Runtime update (2026-08-16): eighteen enabled firearm profiles and five throwing weapons execute on the unpatched Artillery 1.13.4 dedicated server. Bronze Handgonne is the eighteenth explicit profile; upstream registers it but omits it from `#artillery:guns`. Artillery 1.14 itself remains server-unsafe upstream.

## Compatibility status

- [x] The Arquebus, Matchlock Musket, Matchlock Carbine standing and `fork_rest` branches, Matchlock Pistol, Toradar Rifle, Mini Pistola, Handgonne, Bronze Handgonne, Taccola Handgonne iron-ball branch, Markmengonne iron-ball/Arrow branches, Harquebus standing and `fork_rest` branches, Windlass Crossbow Arrow-branch, Chu Ko Nu eight-round repeater, and Double Barrel Gonne first-barrel iron-ball executable slices are implemented with their documented ammunition and confirmed native/vanilla projectile paths through Recruits crossbowman AI.
- [x] The Chu Ko Nu repeater branch is implemented and runtime-verified against the pinned server-safe artifact: one physical `minecraft:arrow` per magazine round up to the native eight, the counter walked back down with one marked vanilla `Arrow` per round, and no reload between rounds of the same magazine.
- [x] The Tiller Gun profile/state/AI slice is implemented and binary-confirmed against this 1.14.0 artifact; dedicated-server runtime proof remains pending because the pinned server-safe 1.11 artifact predates Tiller and this 1.14.0 artifact has an upstream client-only server-load failure.
- [x] The Handgonne profile/state/AI slice is implemented against the pinned artifact: physical `iron_ball`, native `IronballProjectileEntity`, stage-two `loaded=true` state, and the confirmed normal/shift launch branches.
- [x] Markmengonne supports both confirmed single-projectile branches used by recruits. Its preferred iron-ball branch uses physical `iron_ball`, native double `ammo=0.0`, stage-two state without a `loaded` flag, and a native `IronballProjectileEntity`; its Arrow fallback preserves the existing physical `minecraft:arrow`, `ammo=2.0`, and marked vanilla `Arrow` path.
- [x] Bronze Handgonne is explicitly supported despite its omission from the upstream guns tag: physical `iron_ball`, native double `ammo=0.0`, stage-two `loaded=true`, and one native `IronballProjectileEntity`.
- [x] Noble Handgonne supports both confirmed single-projectile branches used by recruits. Its Arrow branch uses a physical `minecraft:arrow`, native stage-two `ammo=2.0` state without a `loaded` flag, and a marked vanilla `Arrow`. Its iron-ball branch uses a physical `iron_ball`, the native powder/ball/ramming chain with `ammo=0.0` and `loaded=true`, and the native `IronballProjectileEntity`. A recruit selects the iron-ball branch when both ammunition types are present; the Arrow branch remains available when no ball is present.
- [x] The Harquebus standing and `fork_rest` branches are implemented and runtime-verified against the pinned server-safe artifact: physical `iron_ball`, the native stage-three load its own chain reaches (`stage=1.0`, `2.0`, `3.0`) without inventing a `loaded` flag, one native `IronballProjectileEntity`, normal inaccuracy `6.0`, rest inaccuracy `3.5`, and native stage-zero fired cleanup. Other Harquebus branches remain disabled.
- [x] The Hackbut ordinary/no-`fork_rest` branch is implemented and binary-confirmed against this 1.14.0 artifact: physical `large_iron_ball`, native stage-two load without inventing a `loaded` flag, and one native `IronballProjectileEntity`. Runtime proof remains gated because the pinned server-safe 1.11 artifact does not register Hackbut.
- [x] The Taccola Handgonne iron-ball branch is implemented and runtime-verified against the pinned server-safe 1.11 artifact: physical `iron_ball`, native double `ammo=0.0`, stage-two `loaded=true` load, stage-three fired cleanup, and one native `IronballProjectileEntity`. Its Shatter Shot, Iron Bit, and Arrow branches remain disabled.
- [x] The whole throwing-weapon family is implemented and runtime-verified against the pinned server-safe 1.11 artifact: `francisca`, `hurlbat`, `throwing_cross`, `javelin`, and `throwable_cobblestone`. Each spends one physical held item, spawns its own native `FranciscaProEntity`, `HurlbatproEntity`, `ThrowingCrossProEntity`, `JavelinProectileEntity`, or `ThrowcobbleEntity`, and reproduces its own confirmed full-use branch — use window `15`/`14`/`13`/`40`/`30` ticks, base damage `5.5`/`6.5`/`4.5`/`2.0`/`15.0`, velocity `1.5`/`1.2`/`1.4`/`3.5`/`0.75`, inaccuracy `1.9`/`2.0`/`1.7`/`2.2`/`0.0` — with knockback `1`, silent, non-critical, non-piercing, no fire, Recruits combat AI handoff, and allied-projectile protection. Native pickup differs per artifact (1.11 `DISALLOWED` with its own recovery roll, 1.14 `ALLOWED`) and is resolved from the binary shape rather than assumed.
- [ ] The full Artillery catalog is not claimed as complete; the other profiles and transaction families remain catalog/reference data only. Double Barrel Gonne's second barrel, multi-shot operation, other ammunition branches, and player reload parity remain outside the enabled policy slice. Chu Ko Nu's player reload parity and native firing cadence also remain outside the enabled policy slice. The throwing weapons' player-only charge-based `releaseUsing` branch and its `BowItem.getPowerForTime` scaling remain disabled.
- [ ] The grenade family (`clay_hand_grenade`, `iron_hand_grenade`, `fire_bomb`, `lime_bomb`) is excluded by decision, not by omission. Their impact chain queues a 45-tick fuse and then calls `Level.explode(null, x, y, z, 3.0F/4.0F, ExplosionInteraction.NONE)`; the ownerless explosion carries no attribution, so this project's ownership-keyed allied protection cannot cover its damage and a recruit would kill its own squad and owner. Area-denial targeting is also outside the current single-target combat goal. See `artillery-addon-compatibility-verification.md` for the full rationale.

## Artifact identity

| Property | Verified value |
|---|---|
| CurseForge project / file | `1307540` / `8325660` |
| Gradle coordinate | `curse.maven:epic-knights-artillery-addon-1307540:8325660` |
| Release JAR | `epic-knights-artillery-addon-1307540-8325660.jar` |
| Release JAR SHA-1 | `a55ace05bc3ca11caaec41f3a8e7882ccb2950b1` |
| Release JAR size | 3,642,122 bytes |
| ForgeGradle variant | `8325660_mapped_official_1.20.1` |
| Mapped JAR SHA-1 | `eea3a63045f2528535ff4d7be1aa49dcdbed5ac3` |
| Mod ID / version | `artillery_addon` / `1.14.0` |
| Display name | `EK:Artillery Addon` |
| License | All Rights Reserved |
| Loader | JavaFML `[47,)` |
| Minecraft range | `[1.20.1]` |

ForgeGradle's mapped artifact has an adjacent `.jar.input` containing:

```text
names=f5fa2554c58505bbb05836b9b987b46853742a0f
orig=a55ace05bc3ca11caaec41f3a8e7882ccb2950b1
```

The `orig` value equals the release JAR SHA-1, so the inspected mapped JAR is linked to the pinned CurseForge file.

## Dependency contract

The upstream `META-INF/mods.toml` declares only Minecraft 1.20.1 and does not declare Epic Knights, Better Combat, Architectury, or Cloth Config. Resource inspection nevertheless found:

- hard recipe references to `magistuarmory:pole`;
- one Better Combat weapon-attribute reference (`bettercombat:lance`);
- four `magistuarmoryaddon` model-parent references under an `item - Copy` asset directory;
- the normal `minecraft`, `forge`, and `artillery_addon` namespaces.

Recruits Use Boomsticks already declares Epic Knights (`magistuarmory`) as mandatory, which covers the hard recipe reference. Better Combat is not made mandatory because the reference is compatibility metadata rather than a recipe result. Architectury and Cloth Config remain runtime dependencies of the pinned Epic Knights installation, not direct Artillery metadata requirements.

The Artillery shapeless recipe `throwing_francisca_recipe` references
`magistuarmoryaddon:steel_francisca_axe` plus `#forge:nuggets/steel` and returns one
`artillery_addon:francisca`. Epic Knights: Addon is optional for this project's runtime adapter but
required if that native conversion recipe should load and be craftable. The original
`steel_francisca_axe` is a normal `MedievalWeaponItem`; it is not itself claimed as throwable.

The project does not link Artillery classes at compile time. The isolated adapter is exercised in the Forge runtime with the current server-safe 1.13.4 file (`8252195`), whose registry/NBT contract matches this report and which registers all eighteen enabled firearm profiles. The upstream tag still lists only seventeen because it omits `bronze_handgonne`. Earlier 1.11 runs gated Tiller Gun, Noble Handgonne, Hackbut, and Double Barrel Gonne because those items did not yet exist in that artifact; 1.13.4 now executes them on an unpatched dedicated server. The 1.14 artifact still fails dedicated-server loading because its automatic subscriber attempts to load the client-only `ClientLevel` class, so 1.14 is not advertised as a supported server dependency.

## Gun tags

`data/forge/tags/items/guns.json` delegates to optional tag `#artillery:guns`. The complete `data/artillery/tags/items/guns.json` list in 1.14.0 is:

1. `artillery_addon:windlass_crossbow`
2. `artillery_addon:noble_handgonne`
3. `artillery_addon:mini_pistola`
4. `artillery_addon:double_barrel_gonne`
5. `artillery_addon:hackbut`
6. `artillery_addon:matchlock_carbine`
7. `artillery_addon:matchlock_musket`
8. `artillery_addon:taccola_handgonne`
9. `artillery_addon:arquebus`
10. `artillery_addon:toradar_rifle`
11. `artillery_addon:matchlock_pistol`
12. `artillery_addon:chu_ko_nu`
13. `artillery_addon:hand_cannon`
14. `artillery_addon:handgonne`
15. `artillery_addon:harquebus`
16. `artillery_addon:tiller_gun`
17. `artillery_addon:markmengonne`

The tag mixes single-shot, repeating, multi-projectile, and mounted/rest-dependent transaction families. It is evidence for discovery, not a safe support allowlist.
`artillery_addon:bronze_handgonne` is registered and craftable but absent from this tag; compatibility therefore routes it by explicit registry ID.

## Executable profile class map

| Registry ID | Item class | Native right-click procedure | Projectile observed in procedure |
|---|---|---|---|
| `artillery_addon:handgonne` | `item.HandgonneItem` | `procedures.TestgunRightclickedProcedure` | native `IronballProjectileEntity` for the ball branch; vanilla `Arrow` for other branches |
| `artillery_addon:taccola_handgonne` | `item.TaccolaHandgonneItem` | `procedures.ToccolaRightclickedProcedure` | native `IronballProjectileEntity` for the iron-ball branch; other ammunition branches remain outside this slice |
| `artillery_addon:noble_handgonne` | `item.NobleHandgonneItem` | `procedures.NobleGonneRightclickedProcedure` | native `IronballProjectileEntity` for `ammo=0.0`; vanilla `Arrow` for `ammo=2.0`; Shatter Shot and Iron Bit remain outside this slice |
| `artillery_addon:arquebus` | `item.ArquebusItem` | `procedures.ArquebusRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:harquebus` | `item.HarquebusItem` | `procedures.HarquebusRightclick2Procedure` | `IronballProjectileEntity` |
| `artillery_addon:hackbut` | `item.HackbutItem` | `procedures.HackbutMatchlockRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:matchlock_musket` | `item.MatchlockMusketItem` | `procedures.MatchlockRifleRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:matchlock_carbine` | `item.MatchlockCarbineItem` | `procedures.CarbineRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:matchlock_pistol` | `item.MatchlockPistolItem` | `procedures.MatchlockPistolRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:toradar_rifle` | `item.ToradarRifleItem` | `procedures.ToradarRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:mini_pistola` | `item.MiniPistolaItem` | `procedures.MinipistolaRightclickedProcedure` | `IronballProjectileEntity` |
| `artillery_addon:tiller_gun` | `item.TillerGunItem` | `procedures.TillergunRightclickedProcedure` | `IronballProjectileEntity` |
| `artillery_addon:markmengonne` | `item.MarkmengonneItem` | `procedures.MarkmenRightclickProcedure` | native `IronballProjectileEntity` for `ammo=0.0`; vanilla `Arrow` for `ammo=2.0` |
| `artillery_addon:bronze_handgonne` | `item.BronzeHandgonneItem` | `procedures.BronzegunneRightclickedProcedure` | native `IronballProjectileEntity` for the enabled iron-ball branch |
| `artillery_addon:windlass_crossbow` | `item.WindlassCrossbowItem` | `procedures.Windlass3RightclickedProcedure` | vanilla `Arrow` |
| `artillery_addon:chu_ko_nu` | `item.ChuKoNuItem` | `procedures.ChuKoNuShootProcedure` | vanilla `Arrow` |
| `artillery_addon:double_barrel_gonne` | `item.DoubleBarrelGonneItem` | `procedures.DoubleBarrelGonneRightclickedProcedure` / `procedures.DbIronballProcedure` | native `IronballProjectileEntity` for the first-barrel iron-ball branch |

All class names above are under `net.mcreator.artilleryaddon`. Registration access is exposed through:

- `init.ArtilleryAddonModItems`: `HANDGONNE`, `BRONZE_HANDGONNE`, `TACCOLA_HANDGONNE`, `ARQUEBUS`, `HARQUEBUS`, `HACKBUT`, `MATCHLOCK_MUSKET`, `MATCHLOCK_PISTOL`, `TORADAR_RIFLE`, `MINI_PISTOLA`, `TILLER_GUN`, `MARKMENGONNE`, `WINDLASS_CROSSBOW`, `FRANCISCA`, `IRON_BALL`, `SMALL_IRON_BALL`, `LARGE_IRON_BALL`, and `FORK_REST`;
- `init.ArtilleryAddonModEntities`: `IRONBALL_PROJECTILE`, `IRON_BIT_PROJECTILE`, and `FRANCISCA_PRO`;
- `init.ArtilleryAddonModSounds`: loading/firing/impact sound registry objects including `HAND_CANNON_LOAD_BALL`, `HAND_CANNON_LOADING_POWDER`, `HAND_CANNON_FIREING`, `ARQUEBUS_BALL`, `ARQUEBUS_RAMMING`, `ARQUEBUS_FIRING`, `BALL_IMPACT`, and `BALL_HITS`;
- `init.ArtilleryAddonModParticleTypes`: `GUNSMOKE`.

`IronBitProjectileEntity` is registered and relevant to other ammunition families. Noble Handgonne's Shatter Shot and Iron Bit branches remain outside the enabled iron-ball/Arrow slice.

## Throwing-weapon executable profiles

| Registry ID | Item class | Native full-use procedure | Projectile |
|---|---|---|---|
| `artillery_addon:francisca` | `item.FranciscaItem` | `procedures.FranciscaPlayerFinishesUseingProcedure` | native `FranciscaProEntity` (`artillery_addon:francisca_pro`) |
| `artillery_addon:hurlbat` | `item.HurlbatItem` | `procedures.HurlbatPlayerFinishesUsingItemProcedure` | native `HurlbatproEntity` (`artillery_addon:hurlbatpro`) |
| `artillery_addon:throwing_cross` | `item.ThrowingCrossItem` | `procedures.ThrowingCrossPlayerFinshesUseingProcedure` | native `ThrowingCrossProEntity` (`artillery_addon:throwing_cross_pro`) |
| `artillery_addon:javelin` | `item.JavelinItem` | `procedures.JavelinPlayerFinishesUsingItemProcedure` | native `JavelinProectileEntity` (`artillery_addon:javelin_proectile`) |
| `artillery_addon:throwable_cobblestone` | `item.ThrowableCobblestoneItem` | `procedures.ThrowableCobblestonePlayerFinishesUsingItemProcedure` | native `ThrowcobbleEntity` (`artillery_addon:throwcobble`) |

Every one of these items uses `UseAnim.SPEAR`, and every full-use procedure has the same body:

```text
createArrowWeaponItemStack(entity, knockback=1, pierce=0)
initArrowProjectile(arrow, thrower, baseDamage, silent=true, critical=false, fire=false, pickup)
setPos(x, eyeY - 0.1, z)
Projectile.shoot(look.x, look.y, look.z, velocity, inaccuracy)
Level.addFreshEntity(...)
mainHandItem.shrink(1)
```

Only the constants differ:

| Weapon | Use duration | Base damage | Velocity | Inaccuracy | Native recovery procedure |
|---|---|---|---|---|---|
| `francisca` | 15 | `5.5` | `1.5` | `1.9` | `FranciscaProjectileProcedure`, roll `0.7` |
| `hurlbat` | 14 | `6.5` | `1.2` | `2.0` | `HurlbatproProjectileHitsProcedure`, roll `0.4` |
| `throwing_cross` | 13 | `4.5` | `1.4` | `1.7` | `ThrowingCrossProjectileProcedure`, roll `0.4` |
| `javelin` | 40 | `2.0` | `3.5` | `2.2` | `JavelinProectileHitsBlockProcedure`, roll `0.75` |
| `throwable_cobblestone` | 30 | `15.0` | `0.75` | `0.0` | none |

Each recovery procedure rolls `Math.random()` against its constant and, on success, creates one
`ItemEntity` of the thrown item with a pickup delay. All five projectile classes extend
`AbstractArrow` and implement `ItemSupplier`, so the thrown item is also the pickup item.

Native pickup mode is artifact-dependent. The pinned 1.11 procedures pass `DISALLOWED` for all five
and the entities discard themselves in their own `onHitBlock`; the 1.14 procedures pass `ALLOWED` and
leave vanilla in-ground pickup active. `FranciscaProEntity`, `HurlbatproEntity`, and
`ThrowingCrossProEntity` declare `isStuckInGround` only in 1.14 and `onHitBlock` only in 1.11, so
their shape is directly observable. `JavelinProectileEntity` declares `onHitBlock` only in 1.11, and
`ThrowcobbleEntity` is identical in both artifacts; those fall back to an artifact-wide probe over
the marker classes.

The item `use`/`finishUsingItem` path is player-oriented, and the early-release branch explicitly
requires `ServerPlayer`. Recruit compatibility therefore does not invoke either entry point. It
constructs the registered native projectile directly on the logical server, applies the confirmed
full-use profile, preserves that weapon's own native uninterrupted use window in the recruit combat
goal, and shrinks one held item only after `addFreshEntity` succeeds. No loaded NBT or separate
ammunition is invented. The generic generated `shoot(LivingEntity, LivingEntity)` helpers have
different constants and are not the executable item full-use boundary, so they are not used.

## Relevant signatures

Each item exposes a vanilla player-only entry point:

```text
use(Level, Player, InteractionHand) -> InteractionResultHolder<ItemStack>
```

The native procedures expose a broader-looking but unsafe entry point:

```text
execute(LevelAccessor, double, double, double, Entity, ItemStack) -> void
```

The inspected native projectile classes extend `AbstractArrow` and expose constructors for `(EntityType, Level)`, `(EntityType, x, y, z, Level)`, and `(EntityType, LivingEntity, Level)`. They also expose static `shoot` overloads. The position constructor is the one used by the inspected executable procedures; owner and launch properties are then assigned explicitly.

`IronballProjectileEntity` owns hit/tick behavior through:

- `onHitEntity(EntityHitResult)`;
- `onHitBlock(BlockHitResult)`;
- `tick()`;
- `getPickupItem()`.

The upstream procedure creates the native entity with `ArtilleryAddonModEntities.IRONBALL_PROJECTILE`, then configures its `AbstractArrow` state. Compatibility code must preserve that projectile class where its hit behavior is required.

## NBT and loading protocol evidence

The listed procedures use the legacy staged loading protocol rather than presenting a stable firearm API:

- powder flask tag: `minecraft:powder_flask`;
- powder amount key: `powder`;
- weapon/loading key: `stage`;
- `powder` and `stage` are stored as NBT doubles by the inspected firearm procedures; the Arquebus loaded/fired boundary is `powder=1.0, stage=2.0` / `powder=0.0, stage=3.0`, Harquebus uses `powder=1.0, stage=2.0` / `powder=0.0, stage=0.0`, and Mini Pistola uses `powder=1.0, stage=1.0` / `powder=0.0, stage=3.0`; Windlass Crossbow uses the native `stage=4.0` loaded and `stage=0.0` fired boundaries without a powder marker, and Chu Ko Nu uses no staged marker at all, tracking its magazine solely in the native `ammo` counter;
- additional strings seen in some procedures: `ammo` and `loaded`; the Markmengonne Arrow branch uses `ammo=2.0` as an NBT double at stage `2.0` without a native `loaded` flag, Noble Handgonne's enabled branch uses `ammo=2.0` at stage `2.0` without a `loaded` flag, Taccola's iron-ball branch uses native double `ammo=0.0` with `loaded=true` at stage `2.0` and `loaded=false` at stage `3.0`, Chu Ko Nu's native procedure walks `ammo` from `1.0` to `8.0` as doubles while loading and spends one round per shot back down to `ammo=0.0`, and Double Barrel Gonne's first-barrel branch uses native double `barrel_one=2.0`, `rammed_one=1.0`, `barrel_two=0.0`, `rammed_two=0.0`, and `loaded=1.0` at the NPC loaded boundary, then clears those values to zero after firing;
- inventory loading components: `ArtilleryAddonModItems.IRON_BALL` for the full-size ball profiles including Taccola, `ArtilleryAddonModItems.SMALL_IRON_BALL` for Mini Pistola/Tiller Gun, `ArtilleryAddonModItems.LARGE_IRON_BALL` for Hackbut, and vanilla `Items.ARROW` for the Markmengonne, Noble Handgonne, Windlass Crossbow, and explicitly documented Chu Ko Nu NPC policy slices, the last of which counts eight arrows for one magazine;
- transient display lore is rewritten to text equivalent to “Needs powder”, “Needs shot”, “Empty”, or a loaded state.

The procedures mutate flask and weapon NBT, inventory, durability, lore, sounds, particles, projectile insertion, and cooldown in one large transaction. There is no rollback API and no public immutable weapon profile. Exact stage values, component ownership, launch constants, durability, misfire probability, and recovery timing must be captured per weapon before implementation; these are deliberately deferred to the catalog/transaction checkpoints rather than inferred from translated lore.

### Handgonne executable profile

`TestgunRightclickedProcedure` accepts an `IRON_BALL`, advances the weapon through stage `1.0`, then accepts an `ARROW` and reaches stage `2.0`/`ammo=2.0`; a ramrod/match path then sets `loaded=true`. Its native ironball firing branch uses the `IronballProjectileEntity` with base damage `1.85`, knockback `1`, silent `true`, pickup disallowed, velocity `4.5`, and inaccuracy `9.0` normally or `6.5` while shifting. The recruit adapter intentionally consumes one physical `iron_ball` and commits the confirmed stage-two loaded boundary without invoking the player-only arrow, ramrod, match, or powder-flask transaction. This is a policy boundary, not full player transaction parity.

### Taccola Handgonne executable iron-ball branch

`ToccolaRightclickedProcedure` has multiple ammunition branches. This compatibility slice enables only the confirmed `IRON_BALL` branch: one physical `iron_ball` is consumed and the recruit weapon is committed at `powder=1.0`, `stage=2.0`, native double `ammo=0.0`, and `loaded=true`. Firing creates one `IronballProjectileEntity` with base damage `1.85`, knockback `1`, silent `true`, pickup disallowed, velocity `4.5`, and inaccuracy `9.0` normally or `6.5` while shifting; it finishes at `powder=0.0`, `stage=3.0`, `ammo=0.0`, and `loaded=false`. The recruit adapter collapses the player-only multi-step loading transaction into one NPC reload transaction. Taccola's Shatter Shot, Iron Bit, and Arrow branches remain disabled.

### Double Barrel Gonne executable first-barrel iron-ball branch

`DoubleBarrelGonneRightclickedProcedure` dispatches the confirmed iron-ball path through `DbIronballProcedure`. This compatibility slice enables only the first barrel: one physical `IRON_BALL` is consumed and the NPC weapon is committed at native double `barrel_one=2.0`, `rammed_one=1.0`, `barrel_two=0.0`, `rammed_two=0.0`, and `loaded=1.0`. Firing creates one native `IronballProjectileEntity` and clears those first-barrel markers to zero. The projectile preserves velocity `4.5`, inaccuracy `9.0` normally or `4.5` while shifting, base damage `2.7`, knockback `1`, critical `true`, pierce level `1`, audible (`silent=false`) state, and pickup disallowed. The native misfire boundary uses a random range of `1.0` to `105.0` against item damage; the NPC adapter preserves that rejection boundary without invoking player-only powder, match, or ramrod procedures. The second barrel, two-shot operation, Shatter Shot, Arrow, and other ammunition branches remain disabled.

### Noble Handgonne executable iron-ball and Arrow branches

`NobleGonneRightclickedProcedure` has multiple native ammunition branches. The iron-ball branch consumes one physical `iron_ball`, walks powder at stage `0.0`, ball at stage `1.0` with double `ammo=0.0`, and bare-hand/ramrod ramming at stage `2.0` with `loaded=true`. It fires one native `IronballProjectileEntity`, finishes at stage `3.0` with `loaded=false`, and preserves base damage `2.7`, critical `true`, velocity `4.5`, and inaccuracy `9.0` normally or `6.5` while shifting. Its native misfire roll is `1.0..210.0` against item damage. The Arrow branch consumes one `minecraft:arrow` and jumps straight to `stage=2.0`, `ammo=2.0` without a `loaded` flag; it fires the existing marked vanilla `Arrow` profile. When both ammunition types are available, recruits prefer the ordinary iron ball. Shatter Shot and Iron Bit remain disabled.

### Markmengonne executable iron-ball and Arrow branches

`MarkmenRightclickProcedure` has multiple ammunition/transaction branches. The iron-ball branch consumes one physical `iron_ball`, walks powder at stage zero, ball at stage one with native double `ammo=0.0`, and vanilla-stick/ramrod ramming to stage two without writing a `loaded` flag. It fires one native `IronballProjectileEntity` with base damage `2.9`, knockback `1`, silent `true`, pickup disallowed, velocity `4.5`, and inaccuracy `7.0` normally or `3.3` while shifting; its native misfire roll is `1.0..130.0` against item damage plus `5`. The Arrow fallback consumes one `minecraft:arrow`, jumps directly to `stage=2.0` with `ammo=2.0`, and fires the existing marked vanilla `Arrow` profile. Recruits prefer Iron Ball when both paths are available. Shatter Shot and Iron Bit remain disabled.

### Bronze Handgonne executable iron-ball branch

`BronzegunneRightclickedProcedure` is registered separately from `handgonne` and is omitted from the upstream guns tag. Its enabled branch consumes one physical `iron_ball`, writes native double `ammo=0.0`, and reaches `stage=2.0` with `loaded=true` through powder, ball, and vanilla-stick/ramrod steps. Firing creates one native `IronballProjectileEntity`, leaves `stage=3.0`, `powder=0.0`, and `loaded=false`, and preserves base damage `1.85`, knockback `1`, silent `true`, pickup disallowed, velocity `4.5`, and inaccuracy `9.0` normally or `4.5` while shifting. Its native misfire roll is `1.0..89.0` against item damage plus `5`. Shatter Shot, Iron Bit, and Arrow branches remain disabled.

### Windlass Crossbow executable Arrow branch

The inspected Windlass procedures use staged item variants for the player-only cocking transaction. `Windlass3RightclickedProcedure` is the confirmed firing boundary: it consumes one vanilla `Items.ARROW`, creates one vanilla `Arrow`, and writes native `stage=0.0` after firing. The loaded boundary is native `stage=4.0`; no `powder`, `ammo`, or `loaded` marker is part of this branch. The recruit adapter intentionally collapses the player-only multi-step cocking/use sequence into one server-safe reload transaction while preserving the confirmed boundaries. Its marked Arrow uses base damage `2.6`, knockback `1`, silent `false`, pickup allowed, critical `true`, no fire, pierce level `0`, velocity `3.5`, and inaccuracy `1.0`.

### Chu Ko Nu bounded NPC policy branch

`ChuKoNuShootProcedure` is the only inspected Chu Ko Nu class that reads or writes the `ammo` key. Its firing branch requires `ammo > 0.0`, creates one vanilla `Arrow`, and walks the counter down one round at a time; its loading branch accepts one `minecraft:arrow` per round and walks the same counter up from `1.0` to the native capacity `8.0`, writing the lore `§7Ammo N/8`. The recruit chain reproduces that loading branch step by step and never invokes the player-only `use`/`finishUsingItem` path.

Each shot creates one marked vanilla `Arrow` and spends exactly one native round, so the weapon stays loaded at `ammo=2.0` and `ammo=1.0` and only unloads at `ammo=0.0`. Every round preserves base damage `1.6`, knockback `1`, pierce level `0`, audible (`silent=false`) state, critical `true`, pickup allowed, velocity `2.4`, and inaccuracy `0.5`.

A recruit only starts the chain with the full eight arrows in its inventory, then commits them one round at a time. The interval between rounds of one magazine is this project's NPC cooldown policy, not a bytecode-confirmed native cadence — the inspected procedure exposes no NPC-usable timing. Native counter values outside `1.0`-`8.0`, fractional values, and non-double NBT types are rejected as unloaded.

### Mini Pistola executable profile

`MinipistolaRightclickedProcedure` recognizes `SMALL_IRON_BALL` when the weapon is at stage `0.0` and writes stage `1.0`. Its native firing branch accepts `MATCH` with stage `1.0`, creates one `IronballProjectileEntity`, and finishes at powder `0.0`, stage `3.0`, with `loaded=false`. The recruit adapter intentionally bypasses the player-only powder-flask and match transaction while preserving the confirmed staged boundary and consuming exactly one physical `small_iron_ball`.

The inspected projectile branches use velocity `3.3`; the ground branch uses inaccuracy `10.0`, the passenger branch `11.0`, base damage `1.0`, silent `true`, knockback `1`, no fire, no critical flag, and `AbstractArrow.Pickup.DISALLOWED`. The native firing sound is the registry ID `artillery_addon:hand_cannon_fireing`.

### Matchlock Carbine executable standing and `fork_rest` branches

`CarbineRightclickProcedure` accepts the native staged load boundary with one `IRON_BALL`: its chain runs `stage=1.0`, `2.0`, `3.0`, so the recruit adapter commits `powder=1.0`, `stage=3.0` and consumes one physical ball, then firing clears `powder=0.0` without inventing a native `loaded` flag. The native procedure has two confirmed single-projectile launch branches. An empty offhand uses velocity `6.5` and inaccuracy `6.0`; an offhand `artillery_addon:fork_rest` uses velocity `6.5` and inaccuracy `3.5`. Both create one `IronballProjectileEntity` with base damage `2.7`, knockback `1`, silent `true`, critical `false`, and pickup disallowed. A different non-empty offhand exits before projectile creation; the recruit adapter preserves that gate instead of silently selecting the standing branch.

### Harquebus executable standing and `fork_rest` branches

`HarquebusRightclick2Procedure` has separate ordinary and `fork_rest` launch branches, each creating exactly one `IronballProjectileEntity`. Its loading chain runs `stage=1.0`, `2.0`, `3.0`, so the compatibility path consumes one physical `IRON_BALL` and commits `powder=1.0`, `stage=3.0` without inventing a native `loaded` flag. Firing finishes at the bytecode-confirmed `powder=0.0`, `stage=0.0` boundary and uses base damage `2.2`, knockback `1`, silent `true`, pickup disallowed, no fire, no critical flag, velocity `5.5`, and inaccuracy `6.0` standing or `3.5` with the exact `artillery_addon:fork_rest` offhand item. Other Harquebus branches remain outside the enabled boundary.

### Hackbut executable ordinary branch

`HackbutMatchlockRightclickProcedure` uses `LARGE_IRON_BALL` as its physical ammunition and advances the weapon to `powder=1.0`, `stage=2.0`; firing finishes at `powder=0.0`, `stage=3.0` without a native `loaded` flag. The no-`fork_rest` player branch is guarded by the player aim/shift condition, so the recruit adapter intentionally bypasses that player-only gate while preserving the confirmed no-`fork_rest` launch profile: one `IronballProjectileEntity`, velocity `6.5`, inaccuracy `5.5`, base damage `5.25`, knockback `1`, critical `true`, pierce level `1`, silent `false`, no fire, and pickup disallowed. The native `fork_rest` launch branches and their separate inaccuracy values remain outside this slice.

### Native multi-step loading chains

Every enabled gameplay weapon's loading branches were read from its own right-click procedure with
`javap -p -c`. The same branches were re-read from the pinned server-safe 1.11 artifact: they are
identical there, only jump offsets differ. `ArtilleryReloadProtocol` reproduces this table.

| Weapon | Step 1 | Step 2 | Step 3 | Native ready state |
|---|---|---|---|---|
| `arquebus` | flask → `stage=0.0`, `powder=1.0`, `hand_cannon_loading_powder` | `iron_ball` → `stage=1.0`, `arquebus_ball` | `#artillery:ramrod` → `stage=2.0`, `arquebus_ramming` | `stage=2.0` |
| `matchlock_musket` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `hand_cannon_load_ball` | ramrod → `stage=2.0` | `stage=2.0` |
| `toradar_rifle` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `arquebus_ball` | ramrod → `stage=2.0` | `stage=2.0` |
| `hackbut` | flask → `stage=0.0`, `powder=1.0` | `large_iron_ball` → `stage=1.0`, `arquebus_ball` | ramrod → `stage=2.0` | `stage=2.0` |
| `matchlock_pistol` | flask → `stage=0.0`, `powder=1.0`, `arquebus_ball` | `iron_ball` → `stage=1.0`, `hand_cannon_load_ball` | bare hand **or** ramrod → `stage=2.0` | `stage=2.0` |
| `matchlock_carbine` | flask → `stage=1.0`, `powder=1.0` | `iron_ball` → `stage=2.0`, `arquebus_ball` | bare hand only → `stage=3.0` | `stage=3.0` |
| `harquebus` | flask → `stage=1.0`, `powder=1.0` | `iron_ball` → `stage=2.0`, `arquebus_ball` | bare hand only → `stage=3.0` | `stage=3.0` |
| `handgonne` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `ammo=0.0`, `hand_cannon_load_ball` | bare hand or ramrod → `stage=2.0`, `loaded=true`, `hand_cannon_ramming` | `stage=2.0`, `loaded=true` |
| `taccola_handgonne` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `ammo=0.0` | bare hand or ramrod → `stage=2.0`, `loaded=true` | `stage=2.0`, `loaded=true` |
| `hand_cannon` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `hand_cannon_load_ball` | bare hand or ramrod → `stage=2.0`, `hand_cannon_ramming` | `stage=2.0` |
| `tiller_gun` | flask → `stage=0.0`, `powder=1.0` | `small_iron_ball` → `stage=1.0`, `ammo=0.0` | bare hand **or** flask → `stage=2.0`, `loaded=true` | `stage=2.0`, `loaded=true` |
| `mini_pistola` | flask → `stage=0.0`, `powder=1.0` | `small_iron_ball` → `stage=1.0`, `hand_cannon_load_ball` | — (no ramming branch) | `stage=1.0` |
| `noble_handgonne` (Iron Ball) | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `ammo=0.0` | bare hand or ramrod → `stage=2.0`, `loaded=true` | `stage=2.0`, `loaded=true` |
| `noble_handgonne` (Arrow) | flask → `stage=0.0`, `powder=1.0` | `minecraft:arrow` → `stage=2.0`, `ammo=2.0` | — (the Arrow branch skips ramming) | `stage=2.0` |
| `markmengonne` | flask → `stage=0.0`, `powder=1.0` | `minecraft:arrow` → `stage=2.0`, `ammo=2.0` | — (the Arrow branch skips ramming) | `stage=2.0` |
| `markmengonne` (Iron Ball) | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `ammo=0.0` | vanilla stick or ramrod → `stage=2.0` | `stage=2.0`, `ammo=0.0` |
| `bronze_handgonne` | flask → `stage=0.0`, `powder=1.0` | `iron_ball` → `stage=1.0`, `ammo=0.0` | vanilla stick or ramrod → `stage=2.0`, `loaded=true` | `stage=2.0`, `loaded=true` |
| `double_barrel_gonne` | flask → `barrel_one=1.0`, `rammed_one=0.0` | `iron_ball` → `barrel_one=2.0` | bare hand or ramrod → `rammed_one=1.0`, `loaded=1.0` | `barrel_one=2.0`, `rammed_one=1.0`, `loaded=1.0` |
| `windlass_crossbow` | bare hand → `stage=1.0`, `crossbow.loading_start` | bare hand → `stage=2.0`, `crossbow.loading_middle` | bare hand → `stage=3.0`, then `minecraft:arrow` → `stage=4.0`, `crossbow.loading_end` | `stage=4.0` |
| `chu_ko_nu` | `minecraft:arrow` → `ammo=1.0` | … one arrow per round … | `minecraft:arrow` → `ammo=8.0` | `ammo>=1.0`, capacity `8.0` |

Notes taken from the same disassembly:

- the flask and the ramrod are damaged (`ItemStack.hurt(1, …)`), never consumed; only ammunition is
  shrunk;
- several ramming branches test the bare hand before the ramrod (`mainHand.getItem() ==
  ItemStack.EMPTY.getItem()`), and Carbine/Harquebus accept **only** the bare hand, so a recruit
  rams them without spending a ramrod;
- Carbine and Harquebus shift the whole staged protocol by one, so their firing branches check
  `stage=3.0`, not `stage=2.0`;
- `ToccolaRightclickedProcedure` has no powder-flask guard on its first step at all; the recruit path
  still requires a flask so the transaction stays recognisable;
- `HandcannonRightclickProcedure` shrinks exactly one `IRON_BALL` and then spawns its whole
  multi-projectile volley, so one native volley costs one ball;
- `ChuKoNuShootProcedure` both fires and reloads: it accepts one `minecraft:arrow` per round and
  walks the native counter `1.0` through `8.0` with the lore `§7Ammo N/8`;
- the native Windlass cocking steps need the arrow only in the inventory and spend it on the shot;
  the recruit path spends it at the last cocking step instead, which is a documented NPC policy.

A separate utility exists at `gun_maker.GunMaker.GunUtils`. It reads/writes the following generic keys:

| Field | NBT key |
|---|---|
| `LEVEL` | `gun_maker_00` |
| `AMMO_NUMBER` | `gun_maker_01` |
| `MAX_AMMO_NUMBER` | `gun_maker_02` |
| `RECOVERY_TIME` | `gun_maker_03` |
| `SHOOTED_ROUNDS` | `gun_maker_04` |
| `ACCUMULATED_INACCURACY` | `gun_maker_05` |
| `IS_AIMING` | `gun_maker_06` |
| `IS_SHOOTING` | `gun_maker_07` |
| `IS_RELOADING` | `gun_maker_08` |
| `SHOULD_SHOOT` | `gun_maker_09` |
| `MOUSE_LEFT` | `gun_maker_10` |
| `MOUSE_RIGHT` | `gun_maker_11` |
| `HAS_SHOOTED` | `gun_maker_12` |

`GunUtils.isGun(ItemStack)` checks the broad `artillery:guns` tag. The inspected native procedures primarily expose the older `stage`/`powder` transaction, so the generic keys must not be assumed to replace the per-weapon protocol.

## Player-only and client/network hazards

The broad `Entity` procedure descriptor is not NPC-safe. For every listed executable procedure, `javap -p -s -c` found the same hazard family:

- repeated casts to `LivingEntity` to read main/offhand stacks;
- a guarded `instanceof Player` / `checkcast Player` branch that calls `Player.getInventory()` and `Inventory.setChanged()`;
- `ItemStack.hurt(int, RandomSource, ServerPlayer)` (currently passed a null `ServerPlayer`, but still part of the player-oriented transaction shape);
- a call to `CooldownProcedure.execute(Entity)`;
- server particles and level sounds in the same method as loading, inventory, durability, and spawn mutations.

`CooldownProcedure` guards its casts with `instanceof Player`, then calls `Player.getCooldowns()`, adds per-item cooldowns, and may call `Player.displayClientMessage`. It has no NPC cooldown behavior, so it cannot define recruit timing.

The item `use` methods are explicitly player-only and call `Player.startUsingItem` for the use-duration weapons before dispatching to the procedure. They must not be invoked for a recruit.

The `gun_maker` subsystem also contains direct client/network integration:

- `GunMaker.Events.onClientTick` loads `net.minecraft.client.Minecraft`;
- `onEventTriggered(RenderPlayerEvent.Pre)` loads client renderer/model classes;
- `onClientMouseTick` reads the mouse and sends `MouseClickPacket`;
- `GunMaker.PacketHandler` registers `artillery_addon:main_channel`;
- the packet handler obtains a `ServerPlayer` from `NetworkEvent.Context.getSender()` and writes mouse-state NBT to that player.

These paths are player input/rendering infrastructure and are not reusable by common/server recruit code.

## Compatibility boundary decision

For recruits:

1. Never invoke item `use`, any native `*RightclickProcedure.execute`, `CooldownProcedure`, or the GunMaker mouse packet path.
2. Read and write only bytecode-confirmed loading state through an adapter owned by this project.
3. Count every required component before mutation, then commit the reload transaction once.
4. Construct the verified native projectile directly on the logical server, set owner/position/trajectory/pickup and confirmed properties, and check `Level.addFreshEntity` before committing weapon cleanup.
5. Reproduce only confirmed server-safe sounds/particles/misfire effects.
6. Keep the supported-item list explicit; do not use `#artillery:guns` as the production allowlist.
7. Treat a physical throwing stack as both weapon and projectile supply: never route it through the firearm reload protocol, and spend it only after the native projectile is accepted by the server level.

## Reproduction commands

```bash
./gradlew processResources
./gradlew dependencyInsight --dependency epic-knights-artillery-addon --configuration runtimeClasspath
javap -classpath <mapped-jar> -p -s -c <class>
```

The dependency was resolved as `curse.maven:epic-knights-artillery-addon-1307540:8325660_mapped_official_1.20.1`. The first forced-refresh attempt encountered transient TLS handshake failures against Forge/Mojang repositories; the normal cached resolution then succeeded.
