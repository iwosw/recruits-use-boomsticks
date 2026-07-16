# Epic Knights: Artilleries and Firearms 1.14.0 binary API report

This report records the compatibility boundary inspected for Recruits Use Boomsticks. It is based on the exact resolved binary artifact and `javap`; it does not contain decompiled upstream source.

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

The project does not link Artillery classes at compile time. The isolated adapter is exercised in the Forge runtime with the pinned 1.11 file (`7455014`), whose common registry/NBT contract matches this report; the 1.14 file remains a reconnaissance target. The 1.14 artifact fails dedicated-server loading because its automatic subscriber attempts to load the client-only `ClientLevel` class, so 1.14 must not be advertised as a supported server dependency until that upstream boundary is corrected. The current gameplay slice is deliberately limited to the Arquebus, iron ball, and native Ironball projectile.

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

## First-tranche class map

| Registry ID | Item class | Native right-click procedure | Projectile observed in procedure |
|---|---|---|---|
| `artillery_addon:handgonne` | `item.HandgonneItem` | `procedures.TestgunRightclickedProcedure` | vanilla `Arrow` |
| `artillery_addon:arquebus` | `item.ArquebusItem` | `procedures.ArquebusRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:matchlock_musket` | `item.MatchlockMusketItem` | `procedures.MatchlockRifleRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:matchlock_pistol` | `item.MatchlockPistolItem` | `procedures.MatchlockPistolRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:toradar_rifle` | `item.ToradarRifleItem` | `procedures.ToradarRightclickProcedure` | `IronballProjectileEntity` |
| `artillery_addon:markmengonne` | `item.MarkmengonneItem` | `procedures.MarkmenRightclickProcedure` | vanilla `Arrow` |

All class names above are under `net.mcreator.artilleryaddon`. Registration access is exposed through:

- `init.ArtilleryAddonModItems`: `HANDGONNE`, `ARQUEBUS`, `MATCHLOCK_MUSKET`, `MATCHLOCK_PISTOL`, `TORADAR_RIFLE`, `MARKMENGONNE`, `IRON_BALL`, and `FORK_REST`;
- `init.ArtilleryAddonModEntities`: `IRONBALL_PROJECTILE` and `IRON_BIT_PROJECTILE`;
- `init.ArtilleryAddonModSounds`: loading/firing/impact sound registry objects including `HAND_CANNON_LOAD_BALL`, `HAND_CANNON_LOADING_POWDER`, `HAND_CANNON_FIREING`, `ARQUEBUS_BALL`, `ARQUEBUS_RAMMING`, `ARQUEBUS_FIRING`, `BALL_IMPACT`, and `BALL_HITS`;
- `init.ArtilleryAddonModParticleTypes`: `GUNSMOKE`.

`IronBitProjectileEntity` is registered and relevant to other ammunition families but was not observed in the six native first-tranche right-click procedures.

## Relevant signatures

Each item exposes a vanilla player-only entry point:

```text
use(Level, Player, InteractionHand) -> InteractionResultHolder<ItemStack>
```

The native procedures expose a broader-looking but unsafe entry point:

```text
execute(LevelAccessor, double, double, double, Entity, ItemStack) -> void
```

Both native projectile classes extend `AbstractArrow` and expose constructors for `(EntityType, Level)`, `(EntityType, x, y, z, Level)`, and `(EntityType, LivingEntity, Level)`. They also expose static `shoot` overloads. The position constructor is the one used by the inspected first-tranche procedures; owner and launch properties are then assigned explicitly.

`IronballProjectileEntity` owns hit/tick behavior through:

- `onHitEntity(EntityHitResult)`;
- `onHitBlock(BlockHitResult)`;
- `tick()`;
- `getPickupItem()`.

The upstream procedure creates the native entity with `ArtilleryAddonModEntities.IRONBALL_PROJECTILE`, then configures its `AbstractArrow` state. Compatibility code must preserve that projectile class where its hit behavior is required.

## NBT and loading protocol evidence

The six procedures use the legacy staged loading protocol rather than presenting a stable firearm API:

- powder flask tag: `minecraft:powder_flask`;
- powder amount key: `powder`;
- weapon/loading key: `stage`;
- `powder` and `stage` are stored as NBT doubles by the inspected procedures; the Arquebus loaded/fired boundary is `powder=1.0, stage=2.0` / `powder=0.0, stage=3.0`;
- additional strings seen in some procedures: `ammo` and `loaded`;
- inventory loading component: `ArtilleryAddonModItems.IRON_BALL`;
- transient display lore is rewritten to text equivalent to “Needs powder”, “Needs shot”, “Empty”, or a loaded state.

The procedures mutate flask and weapon NBT, inventory, durability, lore, sounds, particles, projectile insertion, and cooldown in one large transaction. There is no rollback API and no public immutable weapon profile. Exact stage values, component ownership, launch constants, durability, misfire probability, and recovery timing must be captured per weapon before implementation; these are deliberately deferred to the catalog/transaction checkpoints rather than inferred from translated lore.

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

`GunUtils.isGun(ItemStack)` checks the broad `artillery:guns` tag. The six inspected native procedures primarily expose the older `stage`/`powder` transaction, so the generic keys must not be assumed to replace the per-weapon protocol.

## Player-only and client/network hazards

The broad `Entity` procedure descriptor is not NPC-safe. For every one of the six first-tranche procedures, `javap -p -s -c` found the same hazard family:

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

1. Never invoke item `use`, any first-tranche `*RightclickProcedure.execute`, `CooldownProcedure`, or the GunMaker mouse packet path.
2. Read and write only bytecode-confirmed loading state through an adapter owned by this project.
3. Count every required component before mutation, then commit the reload transaction once.
4. Construct the verified native projectile directly on the logical server, set owner/position/trajectory/pickup and confirmed properties, and check `Level.addFreshEntity` before committing weapon cleanup.
5. Reproduce only confirmed server-safe sounds/particles/misfire effects.
6. Keep the six-item support list explicit; do not use `#artillery:guns` as the production allowlist.

## Reproduction commands

```bash
./gradlew processResources
./gradlew dependencyInsight --dependency epic-knights-artillery-addon --configuration runtimeClasspath
javap -classpath <mapped-jar> -p -s -c <class>
```

The dependency was resolved as `curse.maven:epic-knights-artillery-addon-1307540:8325660_mapped_official_1.20.1`. The first forced-refresh attempt encountered transient TLS handshake failures against Forge/Mojang repositories; the normal cached resolution then succeeded.
