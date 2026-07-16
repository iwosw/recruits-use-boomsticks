<p align="center">
  <img src="assets/recruits-use-boomsticks-logo.png" alt="Recruits Use Boomsticks" width="480">
</p>

# Recruits Use Boomsticks

A small Forge compatibility mod that lets **Villager Recruits crossbowmen** use the firearms and heavy crossbow from **Medieval Boomsticks**, plus the first server-safe **EK: Artillery Addon** Arquebus slice.

## Compatibility

| Component | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.4.20 or newer 47.x |
| Villager Recruits | 1.15.2 |
| Medieval Boomsticks | 1.01 |
| GeckoLib | 4.8.3–4.8.x |
| Epic Knights | 8.2 or newer |
| EK: Artillery Addon | optional; Arquebus slice validated with file 7455014 (1.11) |
| Architectury API | 9.2.14–9.x |
| Cloth Config | 11.1.118–11.x |
| Java | 17 |

Install the mod on both the client and the dedicated server. Recruits Use Boomsticks does not bundle its dependencies. Villager Recruits, Medieval Boomsticks, GeckoLib, Epic Knights, Architectury API, and Cloth Config are required and must be installed separately. The Artillery Addon integration is optional; when the addon is present, this release enables only the Arquebus, iron ball, and native Ironball projectile path.

## Supported weapons

| Weapon | Ammunition | Projectiles per shot |
| --- | --- | ---: |
| Handgonne | Round Ball | 1 |
| Arquebus | Round Ball | 1 |
| Spiked Handgonne | Round Ball | 3 |
| Arbalest | Heavy Bolt | 1 |
| EK: Artillery Addon Arquebus | Iron Ball | 1 |

Crossbowmen can pick up supported weapons and ammunition, switch to them, reload, aim, fire, and respect allied-unit friendly fire rules. Medieval Boomsticks always require their matching physical ammunition, independently of Villager Recruits' `RangedRecruitsNeedArrowsToShoot` server setting. Artillery Arquebus reloads consume exactly one `artillery_addon:iron_ball` and write the addon's confirmed staged NBT state without invoking player-only procedures.

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
- Artillery support is currently limited to the Arquebus, iron ball ammunition, and the native Ironball projectile. The other Artillery gun families are catalogued but not enabled.
- Artillery Addon 1.14 is not advertised for dedicated servers yet because the upstream artifact loads a client-only `ClientLevel` class during server startup; the validated runtime file is 1.11 (`7455014`).
- Mounted crossbowmen can fire and reload supported Boomsticks; mounted reloads take twice as long.
- Compatibility is intentionally limited to the versions in the table above. Other versions may change internal APIs or Mixins.

## Installation

1. Install Minecraft 1.20.1 and Forge 47.4.20 or a newer Forge 47.x build.
2. Install Villager Recruits 1.15.2, Medieval Boomsticks 1.01, GeckoLib 4.8.3–4.8.x, Epic Knights 8.2 or newer, Architectury API 9.2.14–9.x, and Cloth Config 11.1.118–11.x.
3. Optionally install EK: Artillery Addon file 7455014 (1.11) to enable the Arquebus slice. Do not use Artillery Addon 1.14 on a dedicated server with this release.
4. Put `recruits_use_boomsticks-1.0.3.jar` in the `mods` folder on the client and server.

## Support

Report compatibility bugs at <https://github.com/iwosw/recruits-use-boomsticks/issues>. Include the latest log, exact mod versions, whether the issue occurs on a dedicated server, and steps to reproduce it.

## Credits and license

Villager Recruits and Medieval Boomsticks are separate projects owned by their respective authors. This project does not redistribute their assets or code.

Recruits Use Boomsticks is distributed under the terms in [LICENSE](LICENSE).
