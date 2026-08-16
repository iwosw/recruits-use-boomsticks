# Medieval Boomsticks throwing weapons — compatibility notes

Verified against the pinned Medieval Boomsticks artifact `5832925` (mod version `1.01`) and re-read
against `6148892`, the newer artifact inside the declared `[1.01,1.3)` range. Every launch number
below is identical in both.

## Why recruits reproduce the branch instead of using the item

Both native throw shapes are `Player`-only, so a recruit can never reach them by using the item:

- `ThrowingItem.use` (and the `ThrowingKnifeItem` / `ThrowingAxeItem` overrides of it) takes a
  `Player` parameter and throws the instant the item is used.
- `JavelinItem`, `WarDartItem`, `ThrowingSmallRockItem`, and `ThrowingLargeRockItem` extend the
  trident shape: `use` calls `startUsingItem`, and `releaseUsing` returns immediately unless the user
  `instanceof Player` and the item was held for at least ten ticks.

`MedievalBoomsticksThrowableAdapter` therefore builds the same native projectile from the same held
stack, launches it at the same speed, spends the same one physical item, and plays the same throw
sound.

## Supported weapons

| Item | Native projectile | Velocity | Damage | Native wind-up |
| --- | --- | --- | --- | --- |
| `medieval_boomsticks:iron_throwing_knife` | `thrown_knife` (`ThrowableKnife`) | `2.0` | `Config.thrownKnifeDamage`, default `8.0` | none |
| `medieval_boomsticks:iron_throwing_axe` | `thrown_axe` (`ThrowableAxe`) | `1.6` | `Config.thrownAxeDamage`, default `8.0` | none |
| `medieval_boomsticks:small_throwing_rock` | `thrown_small_rock` (`ThrowableSmallRock`) | `Config.smallRockSpeed`, default `2.5` | `Config.smallRockDamage`, default `4.0` | 10 ticks |
| `medieval_boomsticks:large_throwing_rock` | `thrown_large_rock` (`ThrowableLargeRock`) | `Config.largeRockSpeed`, default `1.0` | `Config.largeRockDamage`, default `18.0` | 10 ticks |
| `medieval_boomsticks:war_dart` | `thrown_wardart` (`ThrowableWardart`) | `1.0` | `Config.javelinDamage`, default `8.0` | 10 ticks |
| `medieval_boomsticks:javelin` | `thrown_javelin` (`ThrownJavelin`) | `Config.javelinSpeed`, default `2.5` | `Config.javelinDamage`, default `8.0` | 10 ticks |

Every native throw passes `1.0F` as its `shootFromRotation` inaccuracy and leaves a non-creative
throw collectible (`Pickup.ALLOWED`).

Notes on the table:

- The war dart's projectile reads `Config.javelinDamage`. The mod defines `wardart_damage` and
  `thrown_wardart_damage` settings, but `ThrowableWardart.onHitEntity` is not wired to either in any
  supported artifact, so honouring them would make a recruit hit for something a player never does.
- Damage is never written onto the projectile. Each native entity reads its configured damage in its
  own `onHitEntity`, so a recruit's throw hits for exactly what a player's does, and this project's
  damage settings then apply at the shared `LivingHurtEvent` boundary as they do for the firearms.
- Live Medieval Boomsticks settings are preferred for speed and expected damage, with the native
  default kept whenever the configuration has not been loaded or was set to a value nothing can be
  thrown at — the mod allows a speed of `0.0`.
- The melee, firearm, and bow families of the same mod stay outside this boundary.

## Durability

The javelin and the war dart carry durability. The native branch damages the held stack, hands the
projectile a copy of it, and then removes the stack from the thrower's inventory, so what a player
recovers from the ground is the damaged copy.

A recruit pays that durability point on the copy instead of on the held stack. On the throw that
would break the weapon, the native order leaves the projectile holding an emptied stack — and
`ThrownJavelin`'s constructor casts that stack's item to `JavelinItem`. A recruit breaks the weapon
in hand and takes no shot instead, which is the same cost with nothing launched carrying an item that
no longer exists.

Knives, axes, and rocks have no durability in the pinned artifact; the newer artifact adds it to the
knife and the axe. The adapter reads the stack rather than the artifact, so both are handled without
a version branch.

## Reach

None of these projectiles outruns the shared 45-block combat range. A large rock at velocity `1.0`
runs out of drop compensation after roughly eighteen blocks, and even the javelin at `2.5` falls just
short of the full range, so the combat goal always closes some distance rather than lobbing shots
into the ground.

## The javelin's wind-up model

Medieval Boomsticks registers a `medieval_boomsticks:aim` item predicate on the javelin:

```java
(stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack ? 1.0F : 0.0F
```

It is true only while the holder is *using* the stack, and it selects `item/javelin_aim`, whose
`thirdperson_righthand` rotation is `-75` against the carried model's `95` — a hundred and seventy
degrees apart. A recruit that merely raises its arm therefore holds the carrying model in a throwing
pose, and the javelin points backwards for the whole wind-up.

The recruit is therefore put into the vanilla item-use state for the wind-up of throwables that
declare a use duration longer than it, and taken out of it when the arm releases. This is display
only and cannot cost a second throw:

- every native Medieval Boomsticks release branch refuses a non-player, so the `stopUsingItem` at the
  end of the wind-up does nothing;
- the javelin, the war dart, and both rocks declare a use duration of `72000` ticks, so the vanilla
  use can never complete and reach an item's own finish path;
- the instantly thrown knife and axe declare no use duration at all and are excluded outright.

The same must never be done for the Artillery throwables: those implement `finishUsingItem` with use
durations of 13–40 ticks, so a completed vanilla use would run their native throw a second time and
spend another item. `windUpUsesNativeItemState` is off by default for exactly that reason.

## The cursed axe

`ThrowingAxeItem` marks a stack renamed `Cursed` from `inventoryTick`, which only runs its branch for
a `Player`. A recruit therefore never earns the flag, but an axe cursed in a player's hands keeps its
behaviour when a recruit throws it: the adapter reads the existing `isCursed` tag and passes it to
`ThrowableAxe.setIsCursed`.

## Runtime verification

Twelve Forge GameTests cover this boundary: one per weapon for the single-item native throw, the
combat goal end to end for both native shapes, the durability break, the reach clamp, the wind-up's
native use state, and a direct parity check that launches a second javelin through
`JavelinItem.releaseUsing`'s own `shootFromRotation` call and compares flight direction, yaw, and
pitch. They are part of the 133 required tests the release gate runs.
