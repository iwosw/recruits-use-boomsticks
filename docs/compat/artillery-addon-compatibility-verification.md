# Artillery Addon compatibility verification

## Implemented slice

The first executable Artillery slice is intentionally limited to the Arquebus path:

- `artillery_addon:arquebus`;
- `artillery_addon:iron_ball`;
- `artillery_addon:ironball_projectile` / `IronballProjectileEntity`;
- explicit registry-ID routing through `SupportedArtillery` and `RecruitWeaponAdapters`;
- native staged item state (`powder` and `stage` as NBT doubles, plus the compatibility reload marker);
- one physical iron ball consumed per reload, independently of Recruits' vanilla-arrow setting;
- server-side native projectile creation with the recruit as owner;
- existing recruit targeting, ownership, allied-projectile protection, cooldown, and animation cleanup;
- global and Artillery-specific compatibility kill-switches.

The other Artillery weapon profiles remain catalog/reference data only. They are not enabled by the first gameplay slice.

## Verification commands

Run from the repository root:

```text
./gradlew.bat test --rerun-tasks --console=plain
./gradlew.bat runGameTestServer --rerun-tasks --console=plain
```

The unit-test task completed successfully with 55 tests. The GameTest task completed successfully with 24 required tests:

```text
24 tests are now running!
All 24 required tests passed :)
```

The GameTest coverage includes Arquebus pickup, native reload state, exact iron-ball consumption, server-side firing, native projectile ownership, friendly-fire filtering, combat-goal integration, and both compatibility switches.

## Test-environment limitations

The executable validation uses the pinned Artillery Addon 1.11 artifact available to the ForgeGradle userdev run. Its data contains two recipes that reference IDs from a missing companion namespace:

- `artillery_addon:throwing_francisca_recipe` -> `magistuarmoryaddon:steel_francisca_axe`;
- `artillery_addon:firelancerecipe_0` -> `magistuarmoryaddon:steel_lance`.

Forge reports those upstream recipe parsing errors while continuing to load the test server. The Arquebus GameTests pass, but this environment must not be described as a clean Artillery datapack run. The missing companion dependency is outside this compatibility layer; the dependency JAR is not modified and unrelated recipes are not silently removed.

The Artillery 1.14 artifact is not claimed as dedicated-server validated: the API audit found a client-only `ClientLevel` load in that artifact. The compatibility boundary therefore fails closed when the optional mod is absent or disabled, while the server-side gameplay proof is pinned to the known-loadable test artifact above.
