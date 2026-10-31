package org.iwoss.recruits_use_boomsticks.compat;

import java.util.List;

/** Registry boundary verified against Thirty Years War Forge 1.2, file 9307196. */
public final class SupportedThirtyYearsWar {
    public static final String MOD_ID = "thirtyyearswar";
    public static final String MATCHLOCK_MUSKET_ID = MOD_ID + ":baroque_matchlock_musket";
    public static final String WHEELLOCK_PISTOL_ID = MOD_ID + ":baroque_wheellock_pistol";
    public static final String PAPER_CARTRIDGE_ID = MOD_ID + ":paper_cartridge";
    public static final List<String> WEAPON_IDS = List.of(MATCHLOCK_MUSKET_ID, WHEELLOCK_PISTOL_ID);

    private SupportedThirtyYearsWar() {
    }

    public static boolean isWeaponId(String registryId) {
        return registryId != null && WEAPON_IDS.contains(registryId);
    }

    public static boolean isAmmoId(String registryId) {
        return PAPER_CARTRIDGE_ID.equals(registryId);
    }
}
