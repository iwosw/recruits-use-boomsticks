# Changelog

All notable changes to Recruits Use Boomsticks are documented here.

## 2.0.0 — 2026-08-16

### Added

- Added recruit support for eighteen EK: Artillery Addon firearm profiles and five native throwing weapons on the server-safe Artillery 1.11–1.13.4 line.
- Added recruit support for all six Medieval Boomsticks throwing weapons: the iron throwing knife, the iron throwing axe, the small and large throwing rocks, the war dart, and the javelin. Each spends one physical item for one native projectile at its own configured speed and damage, keeps the thrown weapon collectible, and plays its native throw sound. Durable throwing weapons carry their native durability cost on the item the projectile hands back, and break in the recruit's hand rather than throwing a spent stack.
- Added the native Noble Handgonne iron-ball branch alongside its Arrow branch. Recruits prefer Iron Ball when both loading paths are available.
- Added the native Markmengonne iron-ball branch alongside its Arrow branch, and added the separately registered Bronze Handgonne iron-ball profile even though upstream omits it from `#artillery:guns`.
- Added real multi-step Artillery loading: powder, physical shot, ramming or cocking, native item state, loading sounds, visible tools, tool durability, and the eight-round Chu Ko Nu magazine.
- Added coordinated formation fire. Recruits reserve expected volley damage per target and never commit more than ten physical projectiles to the same target at once.
- Added global and per-integration projectile damage settings, a configurable minimum-damage floor, and a volley hurt-cooldown switch.
- Added **Weapons out!** and **Weapons away!** company orders to the Recruits combat command screen.

### Changed

- A recruit the formation has no shot left for now looks for an enemy nobody has covered instead of idling behind a kill its comrades already own. The search runs only after a refused reservation, on a staggered twenty-tick interval, within thirty-two blocks, and spends a line-of-sight ray on at most four candidates per search.
- Coordinated formation fire no longer holds back a recruit the enemy is itself attacking. A target that has aggroed on a recruit, or hit it within the last hundred ticks, can now be fired on by that recruit even when the formation has already reserved lethal damage, so more than the usual two recruits may engage one target. The ten-projectile ceiling per target still applies.
- Recruits now turn toward the target for the aim and shot, hold the committed firing direction through recoil, and return to their previous formation direction afterward.
- Empty and reloading Medieval Boomsticks weapons use appropriate lowered and charging poses, including third-person GeckoLib reload playback.
- Supported slow throwing weapons approach until their native projectile can reach the target.
- **Weapons away!** now puts the weapon away even when the recruit owns nothing to take instead. The firearm moves into the off-hand shield slot and the main hand is simply left empty, the way a player carries a weapon in its off hand, instead of the order refusing and leaving the gun raised. The combat goal draws it back out of the off hand when a fight starts, so an empty hand costs the recruit nothing. Only a recruit whose shield hand is occupied and whose storage is full is left as it stood, because moving anything there would mean dropping a stack; its chat report now names that reason instead of the missing replacement weapon.
- The carry order now travels in a single packet for every selected group, and its network channel moved to protocol version 2. Forge's server-list check deliberately uses `displayTest = "IGNORE_ALL_VERSION"`, so the list does not reject a server merely because this optional compatibility mod is absent or has a different version. The actual channel uses `acceptMissingOr("2")`: a client without this mod can still join, while a client running version 1.x must be updated to match the server.

### Fixed

- Long firearms no longer snap toward the ground for the shot and recoil. Their visible facing now stays on the target's eyes while the projectile keeps its separate lower ballistic aim point.
- The Medieval Boomsticks javelin no longer points backwards while a recruit winds up. Its throwing model is selected by an item predicate that only holds while the weapon is in use, so the recruit is now put into that state for the wind-up and released from it with the arm.
- Recruits no longer forget a still-valid combat target during the post-shot scheduling gap.
- A recruit ordered to stay passive no longer keeps fighting with a boomstick. The combat goal hands targets back to the recruit — it restores a retained enemy across its own cooldown and picks an uncovered one when the formation has already covered the current target — and did so without reading the aggro state, so the target Recruits clears as the passive order lands was written straight back on the next tick. Passive recruits are now refused every enemy, including one already attacking them, which is how Recruits' own ranged goal behaves. An explicit strategic-fire order still fires, also as upstream does.
- Artillery loading tools and ammunition can no longer be returned into inventory indices used by recruit armour.
- Noble Handgonne can be accepted into the recruit weapon slot and use ordinary Iron Ball ammunition.
- The optional Recruits Musket Mod goal yields when a supported Boomsticks weapon should take control.
- Carry orders preserve weapon counts, recover interrupted loading transactions, and can draw a weapon again after it was stowed.

### Verification

- The current release harness contains 163 unit tests and 136 required Forge GameTests.
- Artillery 1.13.4 is the default unpatched dedicated-server test artifact and registers all eighteen supported firearm profiles.
- The current tree passed all 133 required GameTests at both dependency-range corners, on every Artillery release from 1.11 through 1.13.4, and with GeckoLib 4.2.4, 4.4, and 4.4.9. The minimum corner omits the optional Epic Knights Addon and BetterRecruitFormations runtime stress dependencies because those addons independently require newer Epic Knights and Recruits versions than this mod does.
- Artillery 1.14 remains excluded from the release range because its upstream client-only listener crashes an unpatched dedicated server.

## 1.0.4 — 2026-08-14

### Changed

- **Weapons out!** now draws the supported ranged weapon into the recruit's main hand. **Weapons away!** moves it into the off-hand shield slot and restores a sword, axe, or vanilla crossbow when one exists. README, tooltips, chat reports, and runtime behavior now describe the same hand layout.
- Empty and reloading Medieval Boomsticks firearms and the Arbalest now use lowered recruit arms. Reloading adds a lowered crossbow-loading motion and enables the addon's native GeckoLib item clip in third person, which its renderers normally suppress outside first person. A completed reload raises the charged weapon into the normal two-handed EK-style hold; firing empties and lowers it again. Stowed Boomsticks use the same item orientation in the off hand as in the main hand instead of Minecraft's mirrored left-hand transform. Artillery poses are unchanged.
- Added global and per-integration projectile-damage multipliers, a minimum-damage floor, and a volley hurt-cooldown bypass. The intended high-power defaults remain active: multipliers `1.0`, floor `10.0` (five hearts per projectile), bypass enabled.
- Made Forge initialization, resource IDs, and projectile-impact cancellation work across the verified Forge 47.3.32–47.4.22 range.
- Expanded verified dependency ranges: Recruits 1.15.0–1.15.2, Medieval Boomsticks 1.01–1.2, GeckoLib 4.2.4–4.8.4, Epic Knights 8.2–10.11, Architectury 9.0.8–9.2.14, and Cloth Config 11.0.99–12.0.109.
- Added an optional Artillery metadata range of 1.11–1.13.4. Artillery 1.14 is deliberately rejected because its unpatched dedicated-server startup still crashes on a client-only class.

### Fixed

- A recruit that kills its target mid-reload now keeps loading with its arms up. The combat goal used to tear itself down the moment the target died, which dropped the weapon's reloading marker; the passive reload goal then opened a fresh window over the same weapon, so the loading motion fell away and the rest of the reload played with the arms at rest. A loading window now belongs to the goal that opened it until it closes, and the marker is held up for as long as that window is open.
- Returned Artillery loading tools and ammunition can no longer occupy the inventory indices that back recruit armour. Existing invalid saved stacks are repaired into ordinary storage without losing valid armour.
- Recruits' optional Musket Mod goal now yields when an enabled Boomsticks weapon is available, preventing equal-priority goal contention.
- The Hand Cannon combat GameTest now fixes the recruit RNG and protects its target from neighboring concurrent tests, so a valid native random misfire or cross-test projectile cannot make the release gate alternate between `112/113` and `113/113`.
- The Arbalest ballistic GameTest now checks the stable contract—positive upward lead—instead of a random-inaccuracy-sensitive minimum velocity.

### Verification

- Added reusable Gradle dependency selectors and isolated matrix run directories.
- Verified all 113 required GameTests on the minimum and maximum supported dependency combinations.
- Verified Artillery 1.11, 1.11.1, 1.12, 1.13, 1.13.1, 1.13.2, 1.13.3, and 1.13.4 separately on unpatched dedicated servers. The default test artifact is now 1.13.4, where all seventeen firearm profiles execute.
- Confirmed Artillery 1.14 fails unpatched with `ClientLevel`; its 113/113 patched development run remains non-shipped verification only.

## 1.0.3 — 2026-07-14

### Added

- Added **Weapons out!** and **Weapons away!** orders to the combat tab of the Recruits command screen, modelled on its shield orders. Recruits only draw a weapon when a combat goal starts, so an idle company kept its melee weapon in hand no matter what it carried; the order now wears a supported firearm in the off hand of every commanded crossbowman — the slot a shield takes — leaving the melee weapon in the main hand. Stowing puts the firearm back and returns the recruit's sword or axe, falling back to its crossbow; a recruit whose only weapon is the firearm keeps holding it rather than being disarmed by an order about how it carries its weapons. The orders reach the selected groups, or every recruit the player owns when no group is selected, and they report in chat what happened — including why nothing happened: nobody in range, no supported firearms, or nothing to take back. Both hands are filled from the recruit's whole container rather than through Recruits' own `switchMainHandItem`, which starts its scan past both hand slots and therefore cannot see a firearm in either of them. Any open loading transaction is closed before the hands move, and a recruit that owns no supported firearm is left untouched. The two orders are deliberately separate buttons rather than one toggle: a toggle has to guess on the client what a whole company is holding, and a wrong guess puts the weapons away exactly when the player asked for them.

### Fixed

- Removed the unnecessary upper version limit for Epic Knights. All available Forge releases for Minecraft 1.20.1, from 8.2 through 10.11, are supported.

### Compatibility

- Added and runtime-verified the first Artillery throwing-weapon slice: `artillery_addon:francisca`, one physical held Francisca consumed only after a successful spawn, native `FranciscaProEntity`, 15-tick full-use window, full-use velocity/damage/inaccuracy profile, native impact recovery, Recruits combat-goal handoff, throwing pose, and allied-projectile protection. The Epic Knights Addon `steel_francisca_axe` remains the input to Artillery's own conversion recipe rather than being silently replaced with a throwable item. Hurlbat, Throwing Cross, throwable cobblestone, and the other throwing families remain disabled.
- Completed the Artillery throwing-weapon family: `hurlbat`, `throwing_cross`, `javelin`, and `throwable_cobblestone` join the Francisca, all runtime-verified against the pinned server-safe artifact. Each walks its own native full-use branch read from its `...PlayerFinishesUsingItemProcedure` — per-weapon use window (14, 13, 40, and 30 ticks), base damage, velocity, and inaccuracy — spends exactly one held item only after its native projectile enters the server level, and keeps recruit ownership, allied-projectile protection, the throwing pose, and the combat-goal handoff the Francisca already had. Native pickup mode is resolved per artifact rather than assumed: Artillery 1.11 passes `DISALLOWED` and discards the projectile in its own recovery roll, Artillery 1.14 passes `ALLOWED` and leaves it collectible.
- Recruits carrying a slow throwing weapon now walk into range instead of throwing short. Aim compensation pays for a projectile's drop with an upward arc capped at eight blocks, and past the distance where that cap bites the throw lands short however long the recruit aims. Each weapon now reports the reach its own velocity can pay for — about 13 blocks for a thrown cobblestone, 21 for a Hurlbat — and the combat goal narrows the shared 45-block engagement range to it, approaching the target instead of firing. A weapon may only shorten that range, never extend it, so the Javelin and every firearm behave exactly as before.
- Throwing weapons now animate the wind-up. The raised spear pose was shown the whole time a recruit held one; it is now driven by the aim window, so the weapon rides at the recruit's side on the approach, cocks back once there is a shot to take, and drops as it is thrown. The marker is display-only state on the weapon stack and deliberately avoids vanilla's `startUsingItem`, because these items implement `finishUsingItem` and a completed vanilla use would run the native throw a second time and spend another item.
- Fixed a flaky Hand Cannon combat GameTest. It drove 160 goal ticks inside a single server tick while the shot cadence is measured against level game time, and counted the volley only at the end of the run, by which point the three native projectiles had flown out of the sampled box. It now runs on real server ticks and latches the volley at its widest.
- **Weapons out!** now wears the ranged weapon in the off hand — the slot a shield takes — instead of putting it in the main hand. A drawn company keeps its melee weapons ready and still visibly carries what it will shoot with. **Weapons away!** takes the weapon back out of whichever hand holds it. A fight moves it into the main hand on its own: firing and the native loading chain both need it there, and the chain borrows the off hand for its own powder flask and ramrod. Recruits' own `switchMainHandItem` cannot see either hand slot, so the combat goal now uses this project's own hand swap.
- **Weapons away!** now arms an empty main hand. A recruit given nothing but a gun carries it in the off hand with an empty main hand, so putting the gun away used to leave it standing there empty-handed instead of reaching for its sword.
- The hand-gonne family now shows the native match for the shot. A player holds the match in one hand and the loaded gun in the other; a recruit keeps the weapon in its main hand, so the match is mirrored into the off hand exactly as the loading chain mirrors a flask or a ramrod, and given straight back afterwards. Nothing is spent, and which weapons are lit this way is read from the binary: the Handgonne, Taccola, Bronze Handgonne, Hand Cannon, Mini Pistola, Tiller Gun, Markmengonne, and Double Barrel Gonne procedures ask for a match, while the matchlock family carries its cord in the lock and never does.
- Fixed the stow fallback holding on to the weapon it was stowing. Medieval Boomsticks' guns extend `CrossbowItem`, so the "take the crossbow back" branch accepted the gun itself as the crossbow and left it in the recruit's hand.
- Fixed a firearm floating beside a recruit instead of being held. The idle and parade-carry states used vanilla's `ITEM` pose, which reads as a normal grip on a player but not on a recruit: its arm hangs at its side while the item renders at the humanoid hand point, so a musket hung in the air next to the body. Every firearm state other than reloading now uses the two-handed hold. The visible difference between a loaded and an empty weapon at rest is gone with it, which is the better trade: the reload animation and the aim already show what the weapon is doing, and a pose the model cannot carry showed nothing at all.
- The four weapons whose tests could only ever be skipped are now actually tested. Tiller Gun, Noble Handgonne, Hackbut, and Double Barrel Gonne are absent from the pinned 1.11 artifact, so their GameTests ended in an early pass without checking anything; they now run against Artillery 1.14.0 on a real dedicated GameTest server, reached with a development-only Mixin config that removes the client-only listeners upstream's `GunMaker$Events` registers on both dists. That config is registered for development runs only, is excluded from the release jar, and refuses to apply outside a dev dedicated server: this project tests against Artillery 1.14, it does not vouch for it.
- Executing those four tests immediately found two stale expectations in them — the Tiller Gun test denied the `loaded` flag its native ramming step writes, and the Noble Handgonne test demanded one its native Arrow branch never writes. The adapters were already right; the assertions now match the bytecode.
- Throwing-weapon pickup mode is asserted from whatever Artillery artifact is installed instead of being pinned to the 1.11 branch, so one test proves `DISALLOWED` under 1.11 and `ALLOWED` under 1.14 rather than failing on the artifact it was not written for.
- A throwing weapon this project cannot recognise now says so once in the log instead of failing silently. The boundary is keyed on the native entity registration and class shape, so a repackaged Artillery artifact would leave a recruit that simply never throws, with nothing to explain it; each affected weapon now reports its first refusal, and an artifact whose pickup mode cannot be probed at all states that it is falling back to the pinned 1.11 behaviour.
- Grenades (`clay_hand_grenade`, `iron_hand_grenade`, `fire_bomb`, `lime_bomb`) are documented as deliberately out of scope rather than merely missing. Their native impact chain queues a 45-tick fuse and then calls `Level.explode(null, ...)`; an ownerless explosion carries no attribution, so this project's ownership-keyed allied protection cannot cover its damage and a recruit would kill its own squad and owner. The explicit weapon allowlists already hold that boundary with no code change.
- Completed and verified the first server-safe EK Artillery Addon vertical slice: Arquebus, physical iron-ball ammunition, native Ironball projectile, Recruits crossbowman AI integration, ownership/friendly-fire protection, kill-switches, focused tests, GameTests, and release verification.
- Added and verified the second Artillery gameplay slice for the Matchlock Musket, reusing its confirmed staged NBT protocol, physical iron ball, native Ironball projectile, and the shared Recruits combat AI boundary.
- Added and verified the third Artillery gameplay slice for the Matchlock Pistol, using its bytecode-confirmed critical Ironball projectile profile, physical iron ball, staged NBT protocol, and the shared Recruits combat AI boundary.
- Added and verified the fourth Artillery gameplay slice for the Toradar Rifle, using its bytecode-confirmed `3.2` Ironball projectile profile, physical iron ball, staged NBT protocol, and the shared Recruits combat AI boundary.
- Added and verified the fifth Artillery gameplay slice for the Mini Pistola, using its separate physical `small_iron_ball`, stage-one native load boundary, passenger-aware accuracy, native Ironball projectile, and the shared Recruits combat AI boundary.
- Completed the Matchlock Carbine branch slice for both native standing and `fork_rest` launch paths: physical `iron_ball` ammunition, native stage-two state, branch-specific inaccuracy (`6.0` standing / `3.5` with `fork_rest`), native non-critical Ironball projectile properties, explicit offhand gating, and dedicated GameTest coverage.
- Added the Hackbut ordinary/no-`fork_rest` slice with physical `large_iron_ball` ammunition, native stage-two state, critical/piercing/audible Ironball projectile properties, and the shared Recruits combat AI boundary. Its Forge GameTests are dependency-gated because the pinned 1.11 artifact does not register Hackbut.
- Added the Noble Handgonne Arrow-branch slice with physical `minecraft:arrow` ammunition, native double `ammo=2.0`, stage-two `loaded=true` state, marked vanilla Arrow projectile, and the shared Recruits combat AI boundary. The slice is binary-confirmed against Artillery 1.14.0 and dependency-gated in the pinned 1.11 runtime.
- Added and verified the Markmengonne Arrow and iron-ball branches. The Arrow fallback uses physical `minecraft:arrow`, native double `ammo=2.0`, and a marked vanilla Arrow; the preferred ball branch uses physical `iron_ball`, native double `ammo=0.0`, and a native Ironball projectile. Neither branch invents a `loaded` flag. Shatter Shot and Iron Bit remain disabled.
- Added and verified the Harquebus `fork_rest` branch with physical `iron_ball` ammunition, native stage-two loading, native stage-zero fired cleanup, branch-specific inaccuracy (`6.0` standing / `3.5` with `fork_rest`), explicit offhand gating, native Ironball projectile properties, and dedicated direct/combat GameTests.
- Added and verified the Taccola Handgonne iron-ball branch with one physical `iron_ball`, native double `ammo=0.0`, stage-two `loaded=true` loading, stage-three fired cleanup, normal/shift launch inaccuracy, native Ironball projectile properties, and dedicated direct/combat GameTests. The Shatter Shot, Iron Bit, and Arrow branches remain disabled.
- Recruits now shoulder a loaded firearm with both hands and keep both hands on it while reloading, instead of letting it hang from one hand. Recruits' renderers only reach the two-handed pose for a vanilla crossbow or for a hardcoded list of Musket Mod description IDs, so every supported weapon fell through to the limp item pose; a client-side mixin now supplies the pose from the weapon's own loaded state. This is display only and never touches weapon state.
- Fixed recruits firing visibly into the ground. Every projectile here extends `AbstractArrow` and falls at 0.05 blocks per tick squared, but shots were aimed straight at a point a third up the target's height — below the recruit's own eyes — with no lead at all. Shots now add the drop expected over the projectile's flight time, so a fast iron ball barely arcs while a slow bolt gets a real lob.
- Added the bytecode-confirmed hand cannon loading chain for the Handgonne: powder flask, then iron ball, then ramrod, with the native `hand_cannon_*` sounds, its audible powder step, and the native `loaded` flag committed by the ramming step. The native `MATCH` step is guarded by `stage==2.0`, so it ignites an already loaded weapon and belongs to the firing path rather than the loading chain.
- Recruits now perform the real multi-step Artillery loading transaction instead of one collapsed reload. The Arquebus and Matchlock Musket walk their bytecode-confirmed chain — powder flask, then iron ball, then ramrod — passing through native `stage=0.0`/`powder=1.0`, `stage=1.0`, and `stage=2.0`, playing the native `arquebus_ball` and `arquebus_ramming` sounds, writing the native "Needs shot"/"Needs to be rammed"/"Ready to fire" lore, and visibly holding each tool. **Breaking:** these weapons now require the recruit to carry a `minecraft:powder_flask` member and an `artillery:ramrod` member; the flask and ramrod take native durability damage per reload and break like they do for a player. Weapons whose native chain has not been captured from the binary keep the previous single-step reload.
- Added and verified the Chu Ko Nu repeater branch: three physical `minecraft:arrow` committed in one reload transaction, the native double counter walked down `3.0 -> 2.0 -> 1.0 -> 0.0` with one marked vanilla Arrow per round, no reload between rounds of the same magazine, and a refused shot on an empty magazine. A partial magazine is refused rather than partially loaded; player reload parity and native firing cadence remain disabled.
- Every supported Artillery weapon now reloads through its own bytecode-confirmed native chain instead of only the Arquebus, Matchlock Musket, and Handgonne. Each chain was read from that weapon's right-click procedure and re-read from the pinned 1.11 artifact, so recruits walk the native components, staged NBT values, loading sounds, and lore for all eighteen enabled profiles. **Breaking:** a firearm reload now needs a `minecraft:powder_flask` member in the recruit's inventory, and the flask takes native durability damage per reload. A ramrod is only needed where the native branch actually requires one: Matchlock Carbine and Harquebus ram bare-handed; Handgonne, Taccola Handgonne, Hand Cannon, and Matchlock Pistol prefer the native bare-hand branch; Bronze Handgonne and the Markmengonne iron-ball branch use a vanilla stick or ramrod; Tiller Gun rams with a bare hand or its flask; Mini Pistola and the Noble/Markmengonne Arrow branches have no ramming step; and Windlass Crossbow and Chu Ko Nu need no powder at all.
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
