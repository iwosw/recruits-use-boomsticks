# Changelog

All notable changes to Recruits Use Boomsticks are documented here.

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
