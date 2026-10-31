# Artillery Addon 1.16.2 compatibility

## Artifact and scope

Traced against the original Forge 1.20.1 artifact, CurseForge project `1307540`, file
`8812944`: `curse.maven:epic-knights-artillery-addon-1307540:8812944`.
The JAR reports mod ID `artillery_addon`, version `1.16.2`; its SHA-1 is
`292b5b74afd930fd14850fb7c156f2252811b124`.

The current working tree supports 26 handheld firearm item IDs, including Wheellock Hand Mortar
and Fire Lance. The 2.2.0 release artifact supported 24. The six wheellocks and new ballistics
are selected only when Artillery reports exactly `1.16.2`. Earlier supported
releases retain their previous profiles. The release dependency range deliberately does
not admit unverified later 1.16.x releases.

The contracts below were read from the artifact's registered items, right-click and firing
procedures, and projectile classes. Upstream decompiled code is not distributed here.

## New firearms

All IDs below belong to `artillery_addon`. Each shot uses the native Ironball entity,
with the recruit as owner, one projectile per round, no critical flag, no piercing,
silent projectile, knockback 1 and disabled pickup. Existing ownership, friendly-fire,
damage configuration and formation-fire rules apply.

| Registry ID | Physical ammunition | Rounds | Speed | Spread, ordinary / fork rest | Base damage |
| --- | --- | ---: | ---: | --- | ---: |
| `wheellock_pistol` | `small_iron_ball` | 1 | 4 | 6 / unavailable | 3 |
| `wheellock_musket` | `iron_ball` | 1 | 7 | 4 / 2 | 5.7 |
| `wheellock_hunting_rifle` | `iron_ball` | 1 | 7.4 | 3 / 1.5 | 6.2 |
| `wheellock_breechloading_rifle` | `loaded_cartridge` | 1 | 7.4 | 3 / 1 | 6.2 |
| `dual_wheellock_pistol` | `small_iron_ball` | 2 | 4 | 6 / unavailable | 3 |
| `dual_wheellock_carbine` | `small_iron_ball` | 2 | 5 | 5 / 2.5 | 3.6 |

Mounted musket spread is 5.5; mounted hunting and breechloading rifle spread is 4.5.
The single pistol requires an empty off hand. Fork-rest branches accept an empty hand
or the native fork rest. Base damage is the projectile coefficient before Minecraft's
velocity calculation and this mod's configured damage adjustments.

## Loading and native state

The Wheellock Hand Mortar accepts a powder flask, one `clay_hand_grenade` or
`iron_hand_grenade`, and an item in `#artillery:spanner`. It writes native stages 0, 1, 2,
then stage 3 after firing. The loaded double `ammo` marker is 1 for clay or 2 for iron.
The recruit fires the native `claynade_pro` or `ironnade_pro` projectile. The native
45-tick fuse is retained. Its blast uses the recruit-owned projectile as the source and
filters allied recruits and their owner before applying damage; the native ownerless
explosion is intercepted for recruit shots only. Clay has radius 3, iron radius 4.
The firing hand must be empty or hold a fork rest.

Fire Lance accepts a powder flask and a torch. One charge wears the flask by three.
An optional `iron_bits` item writes native double `ammo=1`. The recruit borrows a real
torch into the off hand to fire. The native fire burst launches nine `fire_proecttile`
entities in a 2/2/5 sequence over four ticks; loading iron bits adds four `iron_bit_projectile`
entities on the first tick. These projectiles retain recruit ownership and friendly-fire
rules. The recruit must be unmounted, matching the native firing gate. The upstream
player-only water and catastrophic misfire branches are not reproduced for recruits.

Recruits keep the weapon in the main hand and temporarily borrow components into the
off hand, mirroring the player's native hand layout. Components move through inventory
slots; ammunition is split and consumed one unit at a time. Interrupted canonical chains
resume from their native NBT writes without paying completed steps again.

* Single muzzleloaders: powder flask (`powder=1`, `stage=0`), physical ball (`stage=1`),
  ramrod (`stage=2`), then an item in `#artillery:spanner` (`stage=3`). A shot writes
  `stage=4`, `powder=0`. The flask, ramrod and winding step each cost one durability.
* Breechloader: one `loaded_cartridge` writes `stage=1`; the spanner writes `stage=2`.
  It needs neither loose powder nor a ramrod. A shot writes `stage=3`; extraction writes
  `stage=4`, numeric `loaded=0` and returns one physical `empty_cartridge`. The NPC
  automatically drops the recoverable case after firing. If spawning it fails, the spent
  state is retained and extraction is retried before a new cartridge can be consumed.
* Dual firearms: load powder, ball and ram each barrel (`barrel_one` / `barrel_two`
  values 1, 2, 3), then wind each barrel to 4. Winding writes numeric `loaded=1`, then 2.
  Both barrels load before the NPC begins firing. Each shot clears barrel two if wound,
  otherwise barrel one, matching native order; the native `loaded` marker is preserved.
  The pistol accepts a stick or ramrod; the carbine requires a ramrod. These ramming
  steps do not damage the tool. Native dual winding checks `nextDouble(1,2) == 2`
  before wear; this peculiar upstream condition is preserved, not changed to a 50% roll.
  Noncanonical partial states that cannot be resumed are refused without overwriting
  their committed ammunition.

New weapons use the native misfire boundary `nextDouble(1,750) < damage / 2`.
Weapon wear happens before the roll on 1.16.2. Recruit reload duration and shot cadence
remain NPC policy; this does not reproduce every player click or upstream short delay.
The existing NPC policy spends the fired round on a misfire rather than implementing
the player's ammunition recovery branches.

## Existing firearms in 1.16.2

Existing loading chains were compared with 1.15.2 and retain their supported loading
contracts. The new worm-ramrod unloading actions are separate player branches.

| Weapon / ammunition branch | Speed | Ordinary / alternate spread | Base damage | Alternate |
| --- | ---: | --- | ---: | --- |
| Handgonne | 3.5 | 8 / 4 | 4 | crouch |
| Taccola, Double Barrel Gonne first barrel | 3.5 | 8 / 4 | 4 | crouch |
| Bronze Handgonne | 3.5 | 8 / 4 | 3.8 | crouch |
| Tiller Gun | 2.5 | 5 / 2.5 | 3 | crouch |
| Hand Cannon | 3 | 10 / 5 | 3.5 | crouch |
| Arquebus | 6 | 6 / 3 | 3.75 | fork rest |
| Matchlock Musket | 6.6 | 5 / 2.5 | 5.5 | fork rest |
| Matchlock Carbine | 5.6 | 5 / 3 | 5 | fork rest |
| Matchlock Pistol | 4 | 7 | 4.5 | empty off hand required |
| Toradar | 7 | 4 / 2 | 6 | fork rest |
| Mini Pistola | 2 | 6 / 7.5 | 1 / 3 | mounted |
| Noble, iron ball | 3.5 | 7 / 3.5 | 4 | crouch |
| Noble, arrow | 4.5 | 8.5 / 4 | 3.5 | crouch |
| Markmengonne, iron ball | 3.6 | 6 / 3 | 4.1 | crouch |
| Markmengonne, arrow | 4.5 | 6.5 / 4 | 1.5 | crouch |
| Harquebus | 5 | 7 / 3.5 | 4.5 | fork rest |
| Hackbut | 10 | 9 / 7 | 10 | crouch, unmounted and no rest |

Mounted Arquebus uses spread 7.5 and damage 5.5; mounted Matchlock Musket uses spread
6.5; mounted Toradar uses spread 5.5. Hackbut retains piercing 1. Updated firearms use
noncritical projectiles; iron balls are silent and native arrow branches remain audible.
Windlass and Chu Ko Nu retain their previous profiles.
The per-weapon updated misfire bounds are recorded in `Artillery1162Profiles`.

## Explicit exclusions

Wallgun mounting, additional ammunition branches, full Double Barrel Gonne
operation, autonomous use of the new repair kits and worm ramrod, and villager trading
are outside this update. The allowlist now contains 26 handheld firearm IDs; this is
not full Artillery arsenal or ammunition-branch parity.

Runtime results and release checks are recorded in
[compatibility verification](artillery-addon-compatibility-verification.md).
