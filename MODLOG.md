# Mod implementation journal

## 2026-10-31 — Thirty Years War compatibility

- Workspace: Recruits Use Boomsticks, Minecraft 1.20.1 / Forge 47.4.22 / Java 17.
- Route: optional typed `BoomstickWeaponAdapter`, using the existing crossbowman AI and carry orders.
- Target: Thirty Years War Forge 1.2, CurseForge project 1752267 / file 9307196.
- Source of truth: released `GunItem`, `GunsConfig` and registry entries. Local inspection inputs are in ignored `downloads/`; no upstream code is shipped.
- Learned: native bullets are synchronous raycasts, not projectile entities; the native firearm method accepts `LivingEntity`, but native reloading and weapon durability are player-gated. The adapter supplies the NPC transaction and wears the firearm once.
- Isolation: tests use Forge's disposable `run-gametest-*` worlds; player saves and installed game profiles are untouched.
- Existing Artillery working-tree changes are preserved. No commit or push requested.
- Contract and verification: `docs/compat/thirty-years-war-1.2.md`.
- Verification commands and final results are recorded in the contract. Dependency-specific cases are gated only in absent-addon runs.
- Final checks: 171 unit tests, 158 required Forge GameTests in all three runtime profiles, release build and JAR content check passed. Output: `build/libs/recruits_use_boomsticks-2.2.1.jar`.
