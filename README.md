<p align="center">
  <img src="assets/recruits-use-boomsticks-logo.png" alt="Recruits Use Boomsticks" width="480">
</p>

# Recruits Use Boomsticks

A small Forge compatibility mod that lets **Villager Recruits crossbowmen** use the firearms and heavy crossbow from **Medieval Boomsticks**, plus the server-safe **EK: Artillery Addon** Arquebus, Matchlock Musket, Matchlock Carbine standing and `fork_rest` branches, Matchlock Pistol, Toradar Rifle, Mini Pistola, Tiller Gun, Handgonne, Taccola Handgonne iron-ball branch, Noble Handgonne Arrow branch, Markmengonne Arrow branch, Harquebus standing and `fork_rest` branches, Hackbut ordinary branch, Windlass Crossbow Arrow branch, Chu Ko Nu eight-round repeater, Double Barrel Gonne first-barrel iron-ball branch, and the whole throwing-weapon family: Francisca, Hurlbat, Throwing Cross, Javelin, and throwable cobblestone.

## Compatibility

| Component | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.4.20 or newer 47.x |
| Villager Recruits | 1.15.2 |
| Medieval Boomsticks | 1.01 |
| GeckoLib | 4.8.3–4.8.x |
| Epic Knights | 8.2 or newer |
| EK: Artillery Addon | optional; thirteen firearm profiles and all five throwing weapons validated with file 7455014 (1.11); Tiller Gun, Noble Handgonne, Hackbut, and Double Barrel Gonne validated separately against 1.14.0, which the mod's own client-only listener stops from loading on a dedicated server |
| Epic Knights: Addon | optional; supplies `steel_francisca_axe` for Artillery's native throwing-Francisca recipe |
| Architectury API | 9.2.14–9.x |
| Cloth Config | 11.1.118–11.x |
| Java | 17 |

Install the mod on both the client and the dedicated server. Recruits Use Boomsticks does not bundle its dependencies. Villager Recruits, Medieval Boomsticks, GeckoLib, Epic Knights, Architectury API, and Cloth Config are required and must be installed separately. The Artillery Addon integration is optional; when the addon is present, this release enables the confirmed profiles and branches listed below with their physical ammunition and projectile boundaries.

**Artillery slice status:** thirteen firearm profiles and all five throwing weapons — Francisca, Hurlbat, Throwing Cross, Javelin, and throwable cobblestone — are runtime-verified against the pinned server-safe artifact, including both Matchlock Carbine branches, the Taccola Handgonne iron-ball branch, the Markmengonne Arrow, both Harquebus branches, the Windlass Crossbow Arrow, and the Chu Ko Nu eight-round repeater. Tiller Gun, the Noble Handgonne Arrow branch, Hackbut, and the Double Barrel Gonne first-barrel branch are not registered by file 7455014, so their tests are gated there; they are runtime-verified separately against Artillery 1.14.0, which needs a development-only patch to start a dedicated server at all. That patch is not shipped, and this release still does not advertise Artillery 1.14 as server-safe.

## Supported weapons

| Weapon | Ammunition | Projectiles per shot |
| --- | --- | ---: |
| Handgonne | Round Ball | 1 |
| Arquebus | Round Ball | 1 |
| Spiked Handgonne | Round Ball | 3 |
| Arbalest | Heavy Bolt | 1 |
| EK: Artillery Addon Arquebus | Iron Ball | 1 |
| EK: Artillery Addon Matchlock Musket | Iron Ball | 1 |
| EK: Artillery Addon Matchlock Carbine (standing + `fork_rest`) | Iron Ball | 1 |
| EK: Artillery Addon Matchlock Pistol | Iron Ball | 1 |
| EK: Artillery Addon Toradar Rifle | Iron Ball | 1 |
| EK: Artillery Addon Mini Pistola | Small Iron Ball | 1 |
| EK: Artillery Addon Tiller Gun | Small Iron Ball | 1 |
| EK: Artillery Addon Handgonne | Iron Ball | 1 |
| EK: Artillery Addon Taccola Handgonne (iron-ball branch) | Iron Ball | 1 |
| EK: Artillery Addon Markmengonne | Arrow | 1 |
| EK: Artillery Addon Noble Handgonne | Arrow | 1 |
| EK: Artillery Addon Harquebus (standing + `fork_rest`) | Iron Ball | 1 |
| EK: Artillery Addon Hackbut | Large Iron Ball | 1 |
| EK: Artillery Addon Windlass Crossbow | Arrow | 1 |
| EK: Artillery Addon Hand Cannon (native volley) | Iron Ball | 3 |
| EK: Artillery Addon Chu Ko Nu (eight-round magazine) | Arrow | 1 |
| EK: Artillery Addon Double Barrel Gonne (first barrel) | Iron Ball | 1 |
| EK: Artillery Addon Francisca | The held Francisca itself | 1 |
| EK: Artillery Addon Hurlbat | The held Hurlbat itself | 1 |
| EK: Artillery Addon Throwing Cross | The held Throwing Cross itself | 1 |
| EK: Artillery Addon Javelin | The held Javelin itself | 1 |
| EK: Artillery Addon Throwable Cobblestone | The held cobblestone itself | 1 |

Crossbowmen can pick up supported weapons and ammunition, switch to them, reload or ready them, aim, fire, and respect allied-unit friendly fire rules. Medieval Boomsticks always require their matching physical ammunition, independently of Villager Recruits' `RangedRecruitsNeedArrowsToShoot` server setting. Artillery full-size ball profiles consume exactly one `artillery_addon:iron_ball` — including the Hand Cannon, whose native volley loads one ball and spawns three projectiles; Mini Pistola and Tiller Gun consume exactly one `artillery_addon:small_iron_ball`; Hackbut consumes exactly one `artillery_addon:large_iron_ball`; Markmengonne, Noble Handgonne, and Windlass Crossbow consume exactly one `minecraft:arrow`. Chu Ko Nu fills its native eight-round magazine one arrow at a time and then fires one round per shot without reloading in between; a partial magazine is refused, and the slice does not claim native player reload parity or native firing cadence. The throwing weapons have no invented loaded state and no separate ammunition: each successful native projectile spawn consumes one held item from the stack, and each keeps its own native use window — 15 ticks for the Francisca, 14 for the Hurlbat, 13 for the Throwing Cross, 30 for the throwable cobblestone, and 40 for the Javelin. All enabled profiles avoid player-only procedures.

Artillery's shapeless `throwing_francisca_recipe` converts one `magistuarmoryaddon:steel_francisca_axe` plus one steel nugget into the supported `artillery_addon:francisca`. Install **Epic Knights: Addon** if that recipe should be craftable. The original `steel_francisca_axe` remains an ordinary Epic Knights melee item and is not silently treated as a throwable.

### Multi-step loading

Every supported Artillery weapon performs the real native loading transaction rather than a single collapsed reload. The recruit works through the weapon's own steps — powder, then shot, then ramming for the firearms; four cocking steps for the Windlass Crossbow; one arrow per round for the Chu Ko Nu magazine — visibly holding each tool, playing the native loading sounds, and passing through the native staged item state on the way.

That means those weapons need more than ammunition. Give the recruit:

| Component | Accepted items |
| --- | --- |
| Powder | `horn_flask`, `wooden_flasks`, or `boneflask` |
| Shot | the weapon's own ball or arrow |
| Ramrod | `artillery_addon:ramrod` or `artillery_addon:iron_ramrod` |

Only the shot is consumed. The flask and the ramrod take durability damage per reload and eventually break, exactly as they do for a player, so resupply them like any other tool. A recruit missing a component it needs will not start a reload it cannot finish.

Not every weapon needs a ramrod. The Matchlock Carbine and the Harquebus ram bare-handed, the Handgonne, Taccola Handgonne, Hand Cannon, and Matchlock Pistol accept a bare hand before they reach for a ramrod, the Tiller Gun rams with a bare hand or its flask, and the Mini Pistola, Noble Handgonne, and Markmengonne have no ramming step at all — all exactly as their native procedures do it. The Windlass Crossbow and the Chu Ko Nu need no powder and no ramrod, only arrows.

A recruit shoulders a loaded firearm with both hands and drops to a one-handed carry while it works through a reload, so the weapon's state is readable at a glance. A throwing weapon is carried at the recruit's side and cocked back into the spear-throwing pose only during the wind-up before a throw, dropping again as the weapon leaves the hand.

Recruits also close the distance rather than throwing short. A thrown projectile only reaches as far as the aim compensation can pay for its drop — roughly 13 blocks for a cobblestone and 21 for a Hurlbat — so a recruit holding one walks toward a target beyond that instead of lobbing into the ground. A Javelin already outruns the normal engagement range and behaves as before, as do all firearms.

## Weapons out order

Recruits only draw a weapon when a combat goal starts, so an idle company marches with a melee weapon in hand even when every recruit carries a supported ranged weapon. The combat tab of the Recruits command screen gets two extra buttons for that, next to the shield orders they are modelled on:

- **Weapons out!** moves a supported ranged weapon into the main hand of every commanded crossbowman and keeps it there in an upright parade carry instead of the firing pose.
- **Weapons away!** puts the ranged weapon back and returns the recruit's own weapon to its hand: a sword or an axe first, then its crossbow. A recruit whose only weapon is the ranged weapon keeps holding it — the order decides how a company carries its weapons, not whether it is armed.

The orders apply to the groups selected in the command screen, and to every recruit you own when no group is selected. Both report in chat what happened, including why nothing happened: no recruits in range, no supported ranged weapons in the company, or nothing to take back in place of one. A recruit that owns no supported ranged weapon is left untouched, and an open loading transaction is closed before the hands move, so the order can never strand a borrowed ramrod or flask. The borrowed powder flask and ramrod a reload puts in the recruit's **off** hand belong to that reload, not to this order. Setting `debugLogging` in the config logs each order and the resulting hands per recruit.

Combat movement yields to Villager Recruits' emergency flee behavior. In particular, a crossbowman will stop pursuing a target and leave navigation to the flee goal while escaping fire or a nearby primed TNT.

## Configuration

Forge creates `config/recruits_use_boomsticks-common.toml` after the first launch.

- `enabled`: enables the compatibility AI. Disabling it restores the original Villager Recruits ranged goal.
- `artilleryAddonEnabled`: enables the optional Artillery Addon adapter independently of Medieval Boomsticks.
- `allowStrategicFire`: permits Villager Recruits strategic-fire positions.
- `smokeParticles`: emits extra server-synchronized smoke after a shot.
- `debugLogging`: enables additional diagnostic logging.

Strategic fire is used only when the crossbowman has no valid hostile target. A target that is temporarily out of range or behind an obstacle keeps priority, so the recruit approaches or recovers line of sight instead of switching to a strategic position.

## Known limitations

- Only Villager Recruits **crossbowmen** use Boomsticks weapons.
- Artillery support is currently limited to the thirteen runtime-verified firearm profiles, the five runtime-verified throwing weapons, and dependency-gated Tiller Gun, Hackbut, Noble Handgonne Arrow, and Double Barrel Gonne first-barrel branches. Taccola is limited to its physical `iron_ball` branch; its Shatter Shot, Iron Bit, and Arrow branches remain unsupported. Double Barrel Gonne is limited to `barrel_one` with one physical iron ball; its second barrel, multi-shot operation, and other ammunition branches remain unsupported. Matchlock Carbine and Harquebus accept an empty offhand for the standing branch or `artillery_addon:fork_rest` for the rest branch; other non-empty offhands and other Artillery rest branches remain unsupported. Chu Ko Nu's physical Arrow magazine is an explicitly documented NPC policy boundary; its native player reload and native firing cadence are not supported. Other Artillery guns and Noble's other ammunition branches are not enabled. Grenades — `clay_hand_grenade`, `iron_hand_grenade`, `fire_bomb`, and `lime_bomb` — are excluded by decision: their impact chain explodes with a `null` source, so ownership-keyed allied protection cannot cover the blast and a recruit would kill its own squad.
- Artillery Addon 1.14 is not advertised for dedicated servers, because `GunMaker$Events` in that artifact subscribes to a client-only render event without a `Dist` filter and Forge refuses to load it on a server. The validated runtime file is 1.11 (`7455014`). This project can start a 1.14 test server through a development-only patch, but does not ship it: fixing that class is upstream's call, and one patched leak is not a promise about the rest of the artifact.
- Mounted crossbowmen can fire and reload supported Boomsticks; mounted reloads take twice as long.
- Compatibility is intentionally limited to the versions in the table above. Other versions may change internal APIs or Mixins.

## Installation

1. Install Minecraft 1.20.1 and Forge 47.4.20 or a newer Forge 47.x build.
2. Install Villager Recruits 1.15.2, Medieval Boomsticks 1.01, GeckoLib 4.8.3–4.8.x, Epic Knights 8.2 or newer, Architectury API 9.2.14–9.x, and Cloth Config 11.1.118–11.x.
3. Optionally install EK: Artillery Addon file 7455014 (1.11) to enable the thirteen runtime-verified firearm profiles and all five throwing weapons. Install Epic Knights: Addon too if Artillery's recipe from `steel_francisca_axe` should be available. Tiller Gun, Hackbut, Noble Handgonne, and Double Barrel Gonne require a registering artifact containing those items. Do not use Artillery Addon 1.14 on a dedicated server with this release.
4. Put `recruits_use_boomsticks-1.0.3.jar` in the `mods` folder on the client and server.

## Support

Report compatibility bugs at <https://github.com/iwosw/recruits-use-boomsticks/issues>. Include the latest log, exact mod versions, whether the issue occurs on a dedicated server, and steps to reproduce it.

## Credits and license

Villager Recruits and Medieval Boomsticks are separate projects owned by their respective authors. This project does not redistribute their assets or code.

Recruits Use Boomsticks is distributed under the terms in [LICENSE](LICENSE).
