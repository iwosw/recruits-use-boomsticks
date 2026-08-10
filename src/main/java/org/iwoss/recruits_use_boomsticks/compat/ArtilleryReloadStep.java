package org.iwoss.recruits_use_boomsticks.compat;

import java.util.List;
import java.util.Objects;

/**
 * One bytecode-confirmed step of a native Artillery loading transaction.
 *
 * <p>The native procedures are player-only: they read the loading component from the main hand and
 * write the weapon state to the off hand. A recruit must keep its weapon in the main hand for
 * Recruits' own targeting and animation, so this project mirrors the hands and reproduces everything
 * else exactly — the same accepted components, the same consumption or durability cost, the same
 * native NBT writes, the same step sound, and the same display lore.</p>
 *
 * <p>A step accepts a list of alternatives because several native branches accept either a tool or a
 * bare hand ({@link ComponentRequirement.Kind#EMPTY_HAND}). The first alternative the recruit can
 * actually supply is used.</p>
 */
public record ArtilleryReloadStep(
        List<ComponentRequirement> components,
        List<NativeWrite> writes,
        ComponentUse componentUse,
        String nativeLore,
        BoomstickSound sound
) {
    /** How the native procedure pays for one step. */
    public enum ComponentUse {
        /** The native step calls {@code ItemStack.shrink(1)}; ammunition is spent outright. */
        CONSUME_ONE,
        /** The native step calls {@code ItemStack.hurt(1, ...)} and only removes a broken tool. */
        DAMAGE_ONE,
        /** The native step is performed bare-handed and pays nothing. */
        NONE
    }

    /** A required loading component, matched by registry identity, item tag, or an empty hand. */
    public record ComponentRequirement(String id, Kind kind) {
        public enum Kind {
            ITEM,
            TAG,
            /** The native branch compares the hand against {@code ItemStack.EMPTY.getItem()}. */
            EMPTY_HAND
        }

        /** The native bare-hand branch; it needs no inventory item at all. */
        public static final ComponentRequirement EMPTY_HAND =
                new ComponentRequirement("minecraft:air", Kind.EMPTY_HAND);

        public ComponentRequirement {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("component id must not be blank");
            }
            Objects.requireNonNull(kind, "kind");
        }

        public static ComponentRequirement item(String id) {
            return new ComponentRequirement(id, Kind.ITEM);
        }

        public static ComponentRequirement tag(String id) {
            return new ComponentRequirement(id, Kind.TAG);
        }

        public boolean isEmptyHand() {
            return kind == Kind.EMPTY_HAND;
        }
    }

    /** One native NBT value a loading step commits on the weapon. */
    public record NativeWrite(String key, Kind kind, double number, boolean flag) {
        public enum Kind {
            DOUBLE,
            BOOLEAN
        }

        public NativeWrite {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("native key must not be blank");
            }
            Objects.requireNonNull(kind, "kind");
            if (kind == Kind.DOUBLE && !Double.isFinite(number)) {
                throw new IllegalArgumentException("native double value must be finite");
            }
        }

        public static NativeWrite number(String key, double value) {
            return new NativeWrite(key, Kind.DOUBLE, value, false);
        }

        public static NativeWrite flag(String key, boolean value) {
            return new NativeWrite(key, Kind.BOOLEAN, 0.0D, value);
        }
    }

    public ArtilleryReloadStep {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(writes, "writes");
        Objects.requireNonNull(componentUse, "componentUse");
        Objects.requireNonNull(nativeLore, "nativeLore");
        Objects.requireNonNull(sound, "sound");
        if (components.isEmpty()) {
            throw new IllegalArgumentException("a loading step must accept at least one component");
        }
        components = List.copyOf(components);
        writes = List.copyOf(writes);
        if (componentUse == ComponentUse.NONE
                && components.stream().anyMatch(component -> !component.isEmptyHand())) {
            throw new IllegalArgumentException("a free step must only accept the bare-hand branch");
        }
    }

    /** A tool survives the step unless it breaks, so it is never counted as spent ammunition. */
    public boolean isTool() {
        return componentUse == ComponentUse.DAMAGE_ONE;
    }

    /** Whether the recruit has to supply a real item; a bare-hand branch never needs one. */
    public boolean requiresCarriedComponent() {
        return components.stream().noneMatch(ComponentRequirement::isEmptyHand);
    }

    /** The native value written to {@code key}, when the step writes one. */
    public java.util.Optional<NativeWrite> write(String key) {
        return writes.stream().filter(write -> write.key().equals(key)).findFirst();
    }
}
