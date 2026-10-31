# Recruits Use Boomsticks

> **Turn your Recruits into an organized gunpowder formation.**

**Recruits Use Boomsticks** is a Minecraft 1.20.1 Forge compatibility mod that lets [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) crossbowmen fight with weapons from [Medieval Boomsticks](https://www.curseforge.com/minecraft/mc-mods/medieval-boomsticks) and selected weapons from [Epic Knights: Artilleries and Firearms](https://www.curseforge.com/minecraft/mc-mods/epic-knights-artillery-addon).

Recruits find weapons and physical ammunition in their inventories, load them through supported weapon-specific steps, aim with compensation for projectile drop, fire native projectiles, coordinate volleys, and protect their owner and allies from friendly fire.

## What's new in 2.2.1

Artillery Addon **1.16.2** support now covers **26 handheld weapon IDs**. Version 2.2 introduced six wheellock firearms, native two-barrel operation, cartridge loading and extraction, and version-specific ballistics. Version 2.2.1 adds:

- **Wheellock Hand Mortar:** loads a physical clay or iron hand grenade, fires the native projectile with its 45-tick fuse, and uses a recruit-owned blast that spares allies.
- **Fire Lance:** uses a powder flask and torch for its native nine-projectile fire burst. Optional Iron Bits add four more projectiles.

Both weapons use the recruit combat AI, physical loading components, and server-side projectile ownership. The release JAR has been started on a separate Forge dedicated server with Artillery 1.16.2.

## Combat and loading

- Real Artillery loading stages with physical ammunition, powder flasks, ramrods, spanners, visible tools, native sounds, and normal tool wear where applicable.
- An eight-arrow Chu Ko Nu magazine, Windlass Crossbow cocking, two loaded dual-wheellock barrels, and recoverable spent breechloader cartridges.
- Coordinated formation fire that avoids committing unnecessary shots to a target and can redirect recruits to another enemy.
- Ballistic compensation, mounted firing where the weapon supports it, aiming and recoil poses, and reload animations.
- **Weapons out!** and **Weapons away!** orders in the Villager Recruits combat screen. Recruits draw supported weapons or stow them and restore a sword, axe, or crossbow when available.
- Configurable projectile damage, minimum damage, smoke, strategic fire, and volley hit behavior.

Only **Villager Recruits crossbowmen** use the supported ranged weapons. Loading and firing timing follows recruit AI policy; it does not reproduce every player click or every Artillery ammunition branch.

## Supported weapons

**Medieval Boomsticks firearms:** Handgonne, Arquebus, Spiked Handgonne, and Arbalest.

**Medieval Boomsticks throwing weapons:** Iron Throwing Knife, Iron Throwing Axe, Small Throwing Rock, Large Throwing Rock, War Dart, and Javelin.

**Artillery Addon firearms and ranged weapons:** Arquebus, Matchlock Musket, Matchlock Carbine, Matchlock Pistol, Toradar Rifle, Mini Pistola, Tiller Gun, Handgonne, Taccola Handgonne, Noble Handgonne, Markmengonne, Bronze Handgonne, Harquebus, Hackbut, Windlass Crossbow, Hand Cannon, Chu Ko Nu, and Double Barrel Gonne.

**Additional weapons with Artillery 1.16.2:** Wheellock Pistol, Wheellock Musket, Wheellock Hunting Rifle, Wheellock Breechloading Rifle, Dual Wheellock Pistol, Dual Wheellock Carbine, Wheellock Hand Mortar, and Fire Lance.

**Artillery Addon throwing weapons:** Francisca, Hurlbat, Throwing Cross, Javelin, and Throwable Cobblestone.

Support applies to the tested ammunition and firing branches. For example, Double Barrel Gonne uses its first iron-ball barrel; other grenade use, wallguns, emplaced weapons, and additional ammunition branches are not supported. Earlier Artillery versions provide only the weapons present in those versions.

## Installation and dependencies

Use **Minecraft 1.20.1**, **Forge 47.3.32–47.4.x**, and **Java 17**. Install Recruits Use Boomsticks on **both client and server**. Dependencies are not bundled in its JAR.

**Required:** [Villager Recruits](https://www.curseforge.com/minecraft/mc-mods/recruits) 1.15.0–1.15.2, [Epic Knights](https://www.curseforge.com/minecraft/mc-mods/epic-knights-armor-and-weapons) 8.2–10.11, [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api) 9.0.8–9.2.14, and [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config) 11.0.99–12.0.109.

**Optional integrations:**

- [Medieval Boomsticks](https://www.curseforge.com/minecraft/mc-mods/medieval-boomsticks) 1.01–1.2. Install [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.2.4–4.8.4 with it.
- [Epic Knights: Artilleries and Firearms](https://www.curseforge.com/minecraft/mc-mods/epic-knights-artillery-addon) **1.11–1.13.4, 1.15.2, or 1.16.2**.
- [BetterRecruitFormations](https://www.curseforge.com/minecraft/mc-mods/betterrecruitformations).
- [Epic Knights: Addon](https://www.curseforge.com/minecraft/mc-mods/epic-knights-addon). Artillery's native Fire Lance crafting and Steel Francisca conversion recipes require it; the weapons still register without it.

**Do not use Artillery Addon 1.14–1.15.1 on a dedicated server.** Those upstream releases load a client-only class during server startup and are excluded from this mod's supported dependency range. Artillery 1.15.2 and 1.16.2 have been verified on an unpatched dedicated server.

## Configuration and support

After first launch, edit `config/recruits_use_boomsticks-common.toml` to enable integrations and adjust projectile damage, minimum damage, strategic fire, smoke, volley hit behavior, or debug logging.

Report issues on [GitHub](https://github.com/iwosw/recruits-use-boomsticks/issues). Include the Minecraft, Forge, Recruits, and weapon-mod versions, steps to reproduce, and `latest.log` when relevant.

Villager Recruits, Medieval Boomsticks, Epic Knights, Artillery Addon, and the other integrations are separate projects owned by their respective authors. Recruits Use Boomsticks does not redistribute their assets or source code. [Source code](https://github.com/iwosw/recruits-use-boomsticks) · [MIT License](https://github.com/iwosw/recruits-use-boomsticks/blob/main/LICENSE)

**Form ranks. Load. Aim. Fire.**
