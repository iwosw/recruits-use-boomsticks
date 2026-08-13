package org.iwoss.recruits_use_boomsticks.compat;

import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep.ComponentRequirement;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep.ComponentUse;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep.NativeWrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * Bytecode-confirmed multi-step loading chains for Artillery Addon weapons.
 *
 * <p>Every enabled gameplay weapon walks the loading branches of its own native right-click
 * procedure, in the native order, with the native components, NBT writes, sounds, and lore. The
 * chains were read from the mapped 1.14.0 artifact and re-read from the pinned server-safe 1.11
 * artifact; the loading branches are identical in both, only jump offsets differ.</p>
 *
 * <p>A weapon without an entry here keeps the original single-transaction NPC reload.</p>
 */
public final class ArtilleryReloadProtocol {
    /** Native powder-flask tag; its members are `horn_flask`, `wooden_flasks`, and `boneflask`. */
    public static final String POWDER_FLASK_TAG = "minecraft:powder_flask";
    /** Native ramrod tag; its members are `iron_ramrod` and `ramrod`. */
    public static final String RAMROD_TAG = "artillery:ramrod";
    public static final String MATCH_ID = SupportedArtillery.MOD_ID + ":match";

    /**
     * Weapons whose native firing branch is lit with a hand-held {@code artillery_addon:match}.
     *
     * <p>Read from the binary rather than assumed: these are the procedures that reference the match
     * item at all. The matchlock family is deliberately absent — an arquebus, musket, carbine,
     * pistol, toradar, or hackbut carries its cord in the lock, so its own procedures never ask for
     * one — and neither is the Noble Handgonne, whose branch does not use it either.</p>
     */
    private static final Set<String> MATCH_LIT_WEAPONS = Set.of(
            SupportedArtillery.HANDGONNE_ID,
            SupportedArtillery.TACCOLA_HANDGONNE_ID,
            SupportedArtillery.HAND_CANNON_ID,
            SupportedArtillery.MINI_PISTOLA_ID,
            SupportedArtillery.TILLER_GUN_ID,
            SupportedArtillery.MARKMENGONNE_ID,
            SupportedArtillery.DOUBLE_BARREL_GONNE_ID);

    /** Whether the native shot for this weapon is lit with a match held in the free hand. */
    public static boolean firesWithMatch(String registryId) {
        return MATCH_LIT_WEAPONS.contains(registryId);
    }

    /** Native Chu Ko Nu magazine capacity; its lore counts every round out of eight. */
    public static final int CHU_KO_NU_CAPACITY = 8;

    private static final String LORE_NEEDS_SHOT = "§7Needs shot";
    private static final String LORE_NEEDS_RAMMING = "§7Needs to be rammed";
    private static final String LORE_READY = "§7Ready to fire";
    private static final String LORE_NONE = "";

    private static final List<ComponentRequirement> FLASK = List.of(
            ComponentRequirement.tag(POWDER_FLASK_TAG));
    private static final List<ComponentRequirement> RAMROD = List.of(
            ComponentRequirement.tag(RAMROD_TAG));
    /** The native branch accepts a bare hand first and only then checks the ramrod tag. */
    private static final List<ComponentRequirement> BARE_HAND_OR_RAMROD = List.of(
            ComponentRequirement.EMPTY_HAND,
            ComponentRequirement.tag(RAMROD_TAG));
    private static final List<ComponentRequirement> BARE_HAND = List.of(
            ComponentRequirement.EMPTY_HAND);
    /** Tiller Gun rams with a bare hand or, in its own native branch, with the powder flask. */
    private static final List<ComponentRequirement> BARE_HAND_OR_FLASK = List.of(
            ComponentRequirement.EMPTY_HAND,
            ComponentRequirement.tag(POWDER_FLASK_TAG));

    private static final Map<String, List<ArtilleryReloadStep>> CHAINS = createChains();

    private ArtilleryReloadProtocol() {
    }

    /** Returns the confirmed native chain, or an empty list when the weapon keeps the single-step reload. */
    public static List<ArtilleryReloadStep> stepsFor(String registryId) {
        return CHAINS.getOrDefault(registryId, List.of());
    }

    public static boolean hasSteppedChain(String registryId) {
        return !stepsFor(registryId).isEmpty();
    }

    public static Map<String, List<ArtilleryReloadStep>> chains() {
        return CHAINS;
    }

    /**
     * Physical ammunition one native chain spends.
     *
     * <p>This is the native cost, not a policy estimate: a multi-projectile volley such as the Hand
     * Cannon still loads exactly the one ball its native ball step shrinks, and only the Chu Ko Nu
     * repeater spends one arrow per magazine round.</p>
     */
    public static int ammoConsumed(String registryId) {
        return (int) stepsFor(registryId).stream()
                .filter(step -> step.componentUse() == ComponentUse.CONSUME_ONE)
                .count();
    }

    private static Map<String, List<ArtilleryReloadStep>> createChains() {
        Map<String, List<ArtilleryReloadStep>> chains = new LinkedHashMap<>();

        // ArquebusRightclickProcedure / ToradarRightclickProcedure: flask (stage 0.0, powder 1.0),
        // ball (stage 1.0), ramrod (stage 2.0). The match branch then fires at stage 2.0.
        chains.put(SupportedArtillery.ARQUEBUS_ID, matchlockChain(
                SupportedArtillery.IRON_BALL_ID,
                BoomstickSound.ARTILLERY_LOADING_POWDER,
                BoomstickSound.ARTILLERY_LOAD_BALL,
                RAMROD));
        chains.put(SupportedArtillery.TORADAR_RIFLE_ID, matchlockChain(
                SupportedArtillery.IRON_BALL_ID,
                BoomstickSound.ARTILLERY_LOADING_POWDER,
                BoomstickSound.ARTILLERY_LOAD_BALL,
                RAMROD));
        // MatchlockRifleRightclickProcedure: the same chain with the hand cannon ball sound.
        chains.put(SupportedArtillery.MATCHLOCK_MUSKET_ID, matchlockChain(
                SupportedArtillery.IRON_BALL_ID,
                BoomstickSound.ARTILLERY_LOADING_POWDER,
                BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                RAMROD));
        // HackbutMatchlockRightclickProcedure: the same chain on the large ball.
        chains.put(SupportedArtillery.HACKBUT_ID, matchlockChain(
                SupportedArtillery.LARGE_IRON_BALL_ID,
                BoomstickSound.ARTILLERY_LOADING_POWDER,
                BoomstickSound.ARTILLERY_LOAD_BALL,
                RAMROD));
        // MatchlockPistolRightclickProcedure: the powder step plays the arquebus ball sound and the
        // ramming step accepts a bare hand before it checks the ramrod tag.
        chains.put(SupportedArtillery.MATCHLOCK_PISTOL_ID, matchlockChain(
                SupportedArtillery.IRON_BALL_ID,
                BoomstickSound.ARTILLERY_LOAD_BALL,
                BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                BARE_HAND_OR_RAMROD));

        // CarbineRightclickProcedure / HarquebusRightclick2Procedure: the same three steps shifted by
        // one, so the native ready state is stage 3.0 and the ramming step is bare-handed.
        chains.put(SupportedArtillery.MATCHLOCK_CARBINE_ID, shiftedMatchlockChain());
        chains.put(SupportedArtillery.HARQUEBUS_ID, shiftedMatchlockChain());

        // TestgunRightclickedProcedure / ToccolaRightclickedProcedure: flask (stage 0.0), ball
        // (stage 1.0, ammo 0.0), ramrod or bare hand (stage 2.0, loaded true).
        chains.put(SupportedArtillery.HANDGONNE_ID, handCannonChain(
                SupportedArtillery.IRON_BALL_ID, true, true));
        chains.put(SupportedArtillery.TACCOLA_HANDGONNE_ID, handCannonChain(
                SupportedArtillery.IRON_BALL_ID, true, true));
        // HandcannonRightclickProcedure: the same three steps without the ammo marker and without a
        // native loaded flag; its match branch only checks stage 2.0.
        chains.put(SupportedArtillery.HAND_CANNON_ID, handCannonChain(
                SupportedArtillery.IRON_BALL_ID, false, false));
        // TillergunRightclickedProcedure: the ramming branch accepts a bare hand or the flask.
        chains.put(SupportedArtillery.TILLER_GUN_ID, List.of(
                flaskStep(0.0D, BoomstickSound.ARTILLERY_LOADING_POWDER),
                ballStep(
                        SupportedArtillery.SMALL_IRON_BALL_ID,
                        1.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                        LORE_NEEDS_RAMMING,
                        NativeWrite.number(ArtilleryNativeState.AMMO_KEY, 0.0D)),
                rammingStep(
                        BARE_HAND_OR_FLASK,
                        2.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_RAMMING,
                        NativeWrite.flag(ArtilleryNativeState.LOADED_KEY, true))));

        // MinipistolaRightclickedProcedure: the ball step already reaches the native ready state, so
        // the weapon has no ramming step at all.
        chains.put(SupportedArtillery.MINI_PISTOLA_ID, List.of(
                flaskStep(0.0D, BoomstickSound.ARTILLERY_LOADING_POWDER),
                ballStep(
                        SupportedArtillery.SMALL_IRON_BALL_ID,
                        1.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                        LORE_READY)));

        // NobleGonneRightclickedProcedure / MarkmenRightclickProcedure: the enabled Arrow branch
        // jumps straight from stage 0.0 to stage 2.0 with ammo 2.0 and never rams.
        chains.put(SupportedArtillery.NOBLE_HANDGONNE_ID, arrowGonneChain(LORE_READY));
        // Markmengonne keeps the native "needs to be rammed" lore even though its Arrow branch is
        // already at the native firing stage; the lore is reproduced, not corrected.
        chains.put(SupportedArtillery.MARKMENGONNE_ID, arrowGonneChain(LORE_NEEDS_RAMMING));

        // DoubleBarrelGonneRightclickedProcedure: the first barrel walks barrel_one 1.0 -> 2.0 and
        // then rammed_one 1.0 with the native double loaded marker.
        chains.put(SupportedArtillery.DOUBLE_BARREL_GONNE_ID, List.of(
                new ArtilleryReloadStep(
                        FLASK,
                        List.of(
                                NativeWrite.number(ArtilleryNativeState.BARREL_ONE_KEY, 1.0D),
                                NativeWrite.number(ArtilleryNativeState.RAMMED_ONE_KEY, 0.0D)),
                        ComponentUse.DAMAGE_ONE,
                        LORE_NEEDS_SHOT,
                        BoomstickSound.ARTILLERY_LOADING_POWDER),
                new ArtilleryReloadStep(
                        List.of(ComponentRequirement.item(SupportedArtillery.IRON_BALL_ID)),
                        List.of(NativeWrite.number(ArtilleryNativeState.BARREL_ONE_KEY, 2.0D)),
                        ComponentUse.CONSUME_ONE,
                        LORE_NEEDS_RAMMING,
                        BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL),
                new ArtilleryReloadStep(
                        BARE_HAND_OR_RAMROD,
                        List.of(
                                NativeWrite.number(ArtilleryNativeState.RAMMED_ONE_KEY, 1.0D),
                                NativeWrite.number(ArtilleryNativeState.LOADED_KEY, 1.0D)),
                        ComponentUse.DAMAGE_ONE,
                        LORE_READY,
                        BoomstickSound.ARTILLERY_HAND_CANNON_RAMMING)));

        // WindlassCrossbowPlayerFinishesUsingItem2Procedure: three bare-handed cocking steps and one
        // arrow step. The native procedure only requires the arrow in the inventory and spends it on
        // the shot; the NPC path spends it here instead, which is this project's documented policy.
        chains.put(SupportedArtillery.WINDLASS_CROSSBOW_ID, List.of(
                cockingStep(1.0D, BoomstickSound.CROSSBOW_LOADING_START),
                cockingStep(2.0D, BoomstickSound.CROSSBOW_LOADING_MIDDLE),
                cockingStep(3.0D, BoomstickSound.CROSSBOW_LOADING_MIDDLE),
                new ArtilleryReloadStep(
                        List.of(ComponentRequirement.item(SupportedArtillery.VANILLA_ARROW_ID)),
                        List.of(NativeWrite.number(ArtilleryNativeState.STAGE_KEY, 4.0D)),
                        ComponentUse.CONSUME_ONE,
                        LORE_NONE,
                        BoomstickSound.CROSSBOW_LOADING_END)));

        // ChuKoNuShootProcedure: the magazine is filled one physical arrow at a time, walking the
        // native double counter 1.0 -> 8.0 with no stage, powder, or loaded marker and no sound.
        chains.put(SupportedArtillery.CHU_KO_NU_ID, chuKoNuChain());

        return Collections.unmodifiableMap(chains);
    }

    /**
     * The three-step matchlock family chain: powder flask, then ball, then ramming.
     *
     * <p>The flask and the ramrod are damaged rather than consumed; only the ball is spent.</p>
     */
    private static List<ArtilleryReloadStep> matchlockChain(
            String ammoId,
            BoomstickSound powderSound,
            BoomstickSound ballSound,
            List<ComponentRequirement> rammingComponents
    ) {
        Objects.requireNonNull(ammoId, "ammoId");
        return List.of(
                flaskStep(0.0D, powderSound),
                ballStep(ammoId, 1.0D, ballSound, LORE_NEEDS_RAMMING),
                rammingStep(rammingComponents, 2.0D, BoomstickSound.ARTILLERY_RAMMING));
    }

    /**
     * The Carbine/Harquebus variant of the matchlock chain.
     *
     * <p>Its native stages run 1.0, 2.0, 3.0 instead of 0.0, 1.0, 2.0, so the native firing branch
     * checks stage 3.0. Its ramming branch is bare-handed and takes no ramrod.</p>
     */
    private static List<ArtilleryReloadStep> shiftedMatchlockChain() {
        return List.of(
                flaskStep(1.0D, BoomstickSound.ARTILLERY_LOADING_POWDER),
                ballStep(
                        SupportedArtillery.IRON_BALL_ID,
                        2.0D,
                        BoomstickSound.ARTILLERY_LOAD_BALL,
                        LORE_NEEDS_RAMMING),
                rammingStep(BARE_HAND, 3.0D, BoomstickSound.ARTILLERY_RAMMING));
    }

    /**
     * The hand cannon family chain: powder flask, then ball, then a bare hand or a ramrod.
     *
     * <p>The Handgonne and Taccola branches also write the native double {@code ammo=0.0} ball
     * marker and the native {@code loaded} flag; the plain Hand Cannon writes neither.</p>
     */
    private static List<ArtilleryReloadStep> handCannonChain(
            String ammoId,
            boolean writesAmmoMarker,
            boolean writesLoadedFlag
    ) {
        Objects.requireNonNull(ammoId, "ammoId");
        List<NativeWrite> ballExtras = writesAmmoMarker
                ? List.of(NativeWrite.number(ArtilleryNativeState.AMMO_KEY, 0.0D))
                : List.of();
        List<NativeWrite> ramExtras = writesLoadedFlag
                ? List.of(NativeWrite.flag(ArtilleryNativeState.LOADED_KEY, true))
                : List.of();
        return List.of(
                flaskStep(0.0D, BoomstickSound.ARTILLERY_LOADING_POWDER),
                ballStep(
                        ammoId,
                        1.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                        LORE_NEEDS_RAMMING,
                        ballExtras.toArray(new NativeWrite[0])),
                rammingStep(
                        BARE_HAND_OR_RAMROD,
                        2.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_RAMMING,
                        ramExtras.toArray(new NativeWrite[0])));
    }

    /** The Noble Handgonne / Markmengonne Arrow branch: powder, then one arrow at stage 2.0. */
    private static List<ArtilleryReloadStep> arrowGonneChain(String arrowLore) {
        return List.of(
                flaskStep(0.0D, BoomstickSound.ARTILLERY_LOADING_POWDER),
                ballStep(
                        SupportedArtillery.VANILLA_ARROW_ID,
                        2.0D,
                        BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                        arrowLore,
                        NativeWrite.number(ArtilleryNativeState.AMMO_KEY, 2.0D)));
    }

    /** The Chu Ko Nu magazine: one physical arrow per native counter step, up to the native eight. */
    private static List<ArtilleryReloadStep> chuKoNuChain() {
        List<ArtilleryReloadStep> steps = new ArrayList<>(CHU_KO_NU_CAPACITY);
        for (int round = 1; round <= CHU_KO_NU_CAPACITY; round++) {
            steps.add(new ArtilleryReloadStep(
                    List.of(ComponentRequirement.item(SupportedArtillery.VANILLA_ARROW_ID)),
                    List.of(NativeWrite.number(ArtilleryNativeState.AMMO_KEY, round)),
                    ComponentUse.CONSUME_ONE,
                    "§7Ammo " + round + "/" + CHU_KO_NU_CAPACITY,
                    BoomstickSound.NONE));
        }
        return List.copyOf(steps);
    }

    private static ArtilleryReloadStep flaskStep(double stageAfter, BoomstickSound sound) {
        return new ArtilleryReloadStep(
                FLASK,
                List.of(
                        NativeWrite.number(ArtilleryNativeState.STAGE_KEY, stageAfter),
                        NativeWrite.number(ArtilleryNativeState.POWDER_KEY, 1.0D)),
                ComponentUse.DAMAGE_ONE,
                LORE_NEEDS_SHOT,
                sound);
    }

    private static ArtilleryReloadStep ballStep(
            String ammoId,
            double stageAfter,
            BoomstickSound sound,
            String lore,
            NativeWrite... extraWrites
    ) {
        List<NativeWrite> writes = new ArrayList<>();
        writes.add(NativeWrite.number(ArtilleryNativeState.STAGE_KEY, stageAfter));
        Collections.addAll(writes, extraWrites);
        return new ArtilleryReloadStep(
                List.of(ComponentRequirement.item(ammoId)),
                writes,
                ComponentUse.CONSUME_ONE,
                lore,
                sound);
    }

    private static ArtilleryReloadStep rammingStep(
            List<ComponentRequirement> components,
            double stageAfter,
            BoomstickSound sound,
            NativeWrite... extraWrites
    ) {
        List<NativeWrite> writes = new ArrayList<>();
        writes.add(NativeWrite.number(ArtilleryNativeState.STAGE_KEY, stageAfter));
        Collections.addAll(writes, extraWrites);
        return new ArtilleryReloadStep(
                components,
                writes,
                ComponentUse.DAMAGE_ONE,
                LORE_READY,
                sound);
    }

    /** A native windlass cocking step: no component, no lore, only the staged crank sound. */
    private static ArtilleryReloadStep cockingStep(double stageAfter, BoomstickSound sound) {
        return new ArtilleryReloadStep(
                BARE_HAND,
                List.of(NativeWrite.number(ArtilleryNativeState.STAGE_KEY, stageAfter)),
                ComponentUse.NONE,
                LORE_NONE,
                sound);
    }
}
