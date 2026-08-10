# Changelog

All notable changes to Recruits Use Boomsticks are documented here.

## 1.0.3 — 2026-07-14

### Added

- Added **Weapons out!** and **Weapons away!** orders to the combat tab of the Recruits command screen, modelled on its shield orders. Recruits only draw a weapon when a combat goal starts, so an idle company kept its melee weapon in hand no matter what it carried; the order now moves a supported firearm into the main hand of every commanded crossbowman and shows it in an upright parade carry instead of the levelled firing pose. Stowing puts the firearm back and returns the recruit's sword or axe, falling back to its crossbow; a recruit whose only weapon is the firearm keeps holding it rather than being disarmed by an order about how it carries its weapons. The orders reach the selected groups, or every recruit the player owns when no group is selected, and they report in chat what happened — including why nothing happened: nobody in range, no supported firearms, or nothing to take back. The main hand is filled from the recruit's whole container rather than through Recruits' own `switchMainHandItem`, which starts its scan past both hand slots and therefore cannot see a firearm the player dropped into the off-hand slot. Any open loading transaction is closed before the hands move, and a recruit that owns no supported firearm is left untouched. The two orders are deliberately separate buttons rather than one toggle: a toggle has to guess on the client what a whole company is holding, and a wrong guess puts the weapons away exactly when the player asked for them.

### Fixed

- Removed the unnecessary upper version limit for Epic Knights. All available Forge releases for Minecraft 1.20.1, from 8.2 through 10.11, are supported.

### Compatibility

- Completed and verified the first server-safe EK Artillery Addon vertical slice: Arquebus, physical iron-ball ammunition, native Ironball projectile, Recruits crossbowman AI integration, ownership/friendly-fire protection, kill-switches, focused tests, GameTests, and release verification.
- Added and verified the second Artillery gameplay slice for the Matchlock Musket, reusing its confirmed staged NBT protocol, physical iron ball, native Ironball projectile, and the shared Recruits combat AI boundary.
- Added and verified the third Artillery gameplay slice for the Matchlock Pistol, using its bytecode-confirmed critical Ironball projectile profile, physical iron ball, staged NBT protocol, and the shared Recruits combat AI boundary.
- Added and verified the fourth Artillery gameplay slice for the Toradar Rifle, using its bytecode-confirmed `3.2` Ironball projectile profile, physical iron ball, staged NBT protocol, and the shared Recruits combat AI boundary.
- Added and verified the fifth Artillery gameplay slice for the Mini Pistola, using its separate physical `small_iron_ball`, stage-one native load boundary, passenger-aware accuracy, native Ironball projectile, and the shared Recruits combat AI boundary.
- Completed the Matchlock Carbine branch slice for both native standing and `fork_rest` launch paths: physical `iron_ball` ammunition, native stage-two state, branch-specific inaccuracy (`6.0` standing / `3.5` with `fork_rest`), native non-critical Ironball projectile properties, explicit offhand gating, and dedicated GameTest coverage.
- Added the Hackbut ordinary/no-`fork_rest` slice with physical `large_iron_ball` ammunition, native stage-two state, critical/piercing/audible Ironball projectile properties, and the shared Recruits combat AI boundary. Its Forge GameTests are dependency-gated because the pinned 1.11 artifact does not register Hackbut.
- Added the Noble Handgonne Arrow-branch slice with physical `minecraft:arrow` ammunition, native double `ammo=2.0`, stage-two `loaded=true` state, marked vanilla Arrow projectile, and the shared Recruits combat AI boundary. The slice is binary-confirmed against Artillery 1.14.0 and dependency-gated in the pinned 1.11 runtime.
- Added and verified the Markmengonne Arrow-branch slice with physical `minecraft:arrow` ammunition, native double `ammo=2.0`, stage-two load without inventing a `loaded` flag, marked vanilla Arrow projectile, and the shared Recruits combat AI boundary. The other Markmengonne ammunition branches remain disabled.
- Added and verified the Harquebus `fork_rest` branch with physical `iron_ball` ammunition, native stage-two loading, native stage-zero fired cleanup, branch-specific inaccuracy (`6.0` standing / `3.5` with `fork_rest`), explicit offhand gating, native Ironball projectile properties, and dedicated direct/combat GameTests.
- Added and verified the Taccola Handgonne iron-ball branch with one physical `iron_ball`, native double `ammo=0.0`, stage-two `loaded=true` loading, stage-three fired cleanup, normal/shift launch inaccuracy, native Ironball projectile properties, and dedicated direct/combat GameTests. The Shatter Shot, Iron Bit, and Arrow branches remain disabled.
- Recruits now shoulder a loaded firearm with both hands and keep both hands on it while reloading, instead of letting it hang from one hand. Recruits' renderers only reach the two-handed pose for a vanilla crossbow or for a hardcoded list of Musket Mod description IDs, so every supported weapon fell through to the limp item pose; a client-side mixin now supplies the pose from the weapon's own loaded state. This is display only and never touches weapon state.
- Fixed recruits firing visibly into the ground. Every projectile here extends `AbstractArrow` and falls at 0.05 blocks per tick squared, but shots were aimed straight at a point a third up the target's height — below the recruit's own eyes — with no lead at all. Shots now add the drop expected over the projectile's flight time, so a fast iron ball barely arcs while a slow bolt gets a real lob.
- Added the bytecode-confirmed hand cannon loading chain for the Handgonne: powder flask, then iron ball, then ramrod, with the native `hand_cannon_*` sounds, its audible powder step, and the native `loaded` flag committed by the ramming step. The native `MATCH` step is guarded by `stage==2.0`, so it ignites an already loaded weapon and belongs to the firing path rather than the loading chain.
- Recruits now perform the real multi-step Artillery loading transaction instead of one collapsed reload. The Arquebus and Matchlock Musket walk their bytecode-confirmed chain — powder flask, then iron ball, then ramrod — passing through native `stage=0.0`/`powder=1.0`, `stage=1.0`, and `stage=2.0`, playing the native `arquebus_ball` and `arquebus_ramming` sounds, writing the native "Needs shot"/"Needs to be rammed"/"Ready to fire" lore, and visibly holding each tool. **Breaking:** these weapons now require the recruit to carry a `minecraft:powder_flask` member and an `artillery:ramrod` member; the flask and ramrod take native durability damage per reload and break like they do for a player. Weapons whose native chain has not been captured from the binary keep the previous single-step reload.
- Added and verified the Chu Ko Nu repeater branch: three physical `minecraft:arrow` committed in one reload transaction, the native double counter walked down `3.0 -> 2.0 -> 1.0 -> 0.0` with one marked vanilla Arrow per round, no reload between rounds of the same magazine, and a refused shot on an empty magazine. A partial magazine is refused rather than partially loaded; player reload parity and native firing cadence remain disabled.
- Every supported Artillery weapon now reloads through its own bytecode-confirmed native chain instead of only the Arquebus, Matchlock Musket, and Handgonne. Each chain was read from that weapon's right-click procedure and re-read from the pinned 1.11 artifact, so recruits walk the native components, staged NBT values, loading sounds, and lore for all seventeen enabled profiles. **Breaking:** a firearm reload now needs a `minecraft:powder_flask` member in the recruit's inventory, and the flask takes native durability damage per reload. A ramrod is only needed where the native branch actually requires one: Matchlock Carbine and Harquebus ram bare-handed, Handgonne, Taccola Handgonne, Hand Cannon, and Matchlock Pistol prefer the native bare-hand branch, Tiller Gun rams with a bare hand or its flask, Mini Pistola, Noble Handgonne, and Markmengonne have no ramming step, and Windlass Crossbow and Chu Ko Nu need no powder at all.
- Added the native Windlass Crossbow cocking chain (three crank steps with the vanilla crossbow loading sounds, then the arrow at native `stage=4.0`) and the native Chu Ko Nu magazine chain (one physical arrow per round with the native `§7Ammo N/8` lore).
- Corrected four profiles against the addon binary: the Matchlock Carbine and the Harquebus load at native `stage=3.0` rather than `stage=2.0`, the Chu Ko Nu magazine is the native eight rounds rather than three, the Handgonne and the Tiller Gun carry the native `ammo=0.0` and `loaded` markers their procedures write, and the Noble Handgonne Arrow branch no longer invents a `loaded` flag its native branch never sets. The Matchlock Musket's ball step now plays its own native `hand_cannon_load_ball` sound, and the Arquebus powder step is audible as it is natively.
- A Hand Cannon volley now costs the one iron ball its native procedure spends, instead of one ball per projectile. Ammunition per reload comes from the chain itself.
- Fixed recruits losing one round of a multi-round magazine: a consumed component is now split one at a time into the off hand instead of shuttling the whole ammunition stack in and out of it.
- Added the Double Barrel Gonne first-barrel iron-ball branch with one physical `iron_ball`, native double `barrel_one`/`rammed_one`/`loaded` state, one critical/piercing/audible native Ironball projectile, shift-aware inaccuracy, and dedicated direct/combat GameTests. The second barrel, multi-shot operation, and other ammunition branches remain disabled; runtime tests are gated because the pinned 1.11 artifact does not register the item.

## 1.0.2 — 2026-07-14

### Fixed

- Updated the required Epic Knights version to 10.x so the addon can be installed with current Medieval Boomsticks dependencies.

## 1.0.1 — 2026-07-14

### Fixed

- A valid hostile target now takes precedence over strategic fire when line of sight is temporarily lost or the target is outside combat range; the recruit keeps trying to restore combat position instead of firing at a strategic position.
- Passive reload no longer claims the `LOOK` goal flag, and the combat goal now processes the committed shot into persistent cooldown before releasing its movement lock.
- Projectile friendly-fire and lifetime hooks now honor the common `enabled` kill switch.
- Projectile rollback also includes the current projectile when entity insertion throws after insertion has begun.
- Aim timing now completes exactly after the configured number of uninterrupted ticks instead of one tick late.
- Boomstick combat now yields navigation to Villager Recruits' emergency flee behavior, including nearby primed TNT, instead of overriding the escape path.
- Arbalest bolts now use vanilla crossbow-style ballistic compensation, aiming higher as horizontal distance increases.
- Arbalest reloads complete in 25 ticks on foot and retain the existing mounted reload multiplier.
- Active reloads now survive damage, target acquisition, and combat-goal handoff instead of restarting.
- Medieval Boomsticks weapons now always require their physical Round Ball or Heavy Bolt ammunition, independently of Villager Recruits' global arrow setting.
- Post-shot handling now uses only a short technical cooldown instead of stacking a full native cooldown with the reload animation.
- Recruits can fire and reload supported weapons while mounted.

### Compatibility

- Declared Epic Knights 9.8, Architectury API 9.2.14, and Cloth Config 11.1.118 as required runtime dependencies.

## 1.0.0 — 2026-07-13

### Added

- Compatibility AI for Villager Recruits crossbowmen using Handgonne, Arquebus, Spiked Handgonne, and Arbalest.
- Server-side reload, projectile spawning, durability, sounds, and smoke effects.
- Optional ammunition consumption controlled by Villager Recruits' ranged-ammunition setting.
- Friendly-fire protection for recruit-owned Medieval Boomsticks projectiles.
- Strategic-fire support and a common Forge configuration.
- Dedicated-server GameTests for loaded-weapon ammo semantics, multi-projectile firing, and friendly-fire scope.

### Compatibility

- Minecraft 1.20.1
- Forge 47.4.20–47.x
- Villager Recruits 1.15.2
- Medieval Boomsticks 1.01
- GeckoLib 4.8.3–4.8.x
