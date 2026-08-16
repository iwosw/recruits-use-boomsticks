package org.iwoss.recruits_use_boomsticks.compat;

/** Sound selectors kept independent from the external Boomsticks implementation. */
public enum BoomstickSound {
    HANDGONNE_SHOOT,
    ARQUEBUS_SHOOT,
    CROSSBOW_SHOOT,
    /** Medieval Boomsticks' own {@code throw_weapon} event, played by its instant-throw items. */
    THROW_WEAPON,
    /** Vanilla trident throw, played by the Medieval Boomsticks trident-shaped throwing items. */
    TRIDENT_THROW,
    ARTILLERY_FIRE,
    ARTILLERY_HAND_CANNON_FIRE,
    /** No native sound is played for this step. */
    NONE,
    ARTILLERY_LOADING_POWDER,
    ARTILLERY_LOAD_BALL,
    ARTILLERY_RAMMING,
    ARTILLERY_HAND_CANNON_LOAD_BALL,
    ARTILLERY_HAND_CANNON_RAMMING,
    /** Vanilla crossbow cocking sounds used by the native Windlass Crossbow loading chain. */
    CROSSBOW_LOADING_START,
    CROSSBOW_LOADING_MIDDLE,
    CROSSBOW_LOADING_END
}
