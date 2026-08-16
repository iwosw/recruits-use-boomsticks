package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;

import java.util.Optional;
import java.util.UUID;

/** Durable recruit ownership used after a projectile's live owner is gone. */
public final class BoomstickProjectileAttribution {
    private static final String SHOOTER_KEY = "recruits_use_boomsticks:projectile_shooter";
    private static final String OWNER_KEY = "recruits_use_boomsticks:projectile_recruit_owner";
    private static final String TEAM_KEY = "recruits_use_boomsticks:projectile_team";

    private BoomstickProjectileAttribution() {
    }

    public static void mark(AbstractArrow projectile, AbstractRecruitEntity shooter) {
        if (projectile == null || shooter == null) {
            return;
        }
        CompoundTag data = projectile.getPersistentData();
        data.putUUID(SHOOTER_KEY, shooter.getUUID());
        UUID owner = shooter.getOwnerUUID();
        if (owner != null) {
            data.putUUID(OWNER_KEY, owner);
        }
        if (shooter.getTeam() != null) {
            data.putString(TEAM_KEY, shooter.getTeam().getName());
        }
    }

    public static boolean isRecruitOwned(AbstractArrow projectile) {
        if (liveShooter(projectile).isPresent()) {
            return true;
        }
        CompoundTag data = attribution(projectile);
        return data != null && data.hasUUID(SHOOTER_KEY);
    }

    public static Optional<UUID> shooterUuid(AbstractArrow projectile) {
        Optional<AbstractRecruitEntity> live = liveShooter(projectile);
        if (live.isPresent()) {
            return Optional.of(live.orElseThrow().getUUID());
        }
        CompoundTag data = attribution(projectile);
        if (data == null || !data.hasUUID(SHOOTER_KEY)) {
            return Optional.empty();
        }
        return Optional.of(data.getUUID(SHOOTER_KEY));
    }

    public static boolean isFriendly(AbstractArrow projectile, Entity target) {
        if (projectile == null || target == null) {
            return false;
        }
        Optional<AbstractRecruitEntity> live = liveShooter(projectile);
        if (live.isPresent()) {
            AbstractRecruitEntity shooter = live.orElseThrow();
            return shooter == target
                    || shooter.isAlliedTo(target)
                    || target instanceof LivingEntity living && !shooter.canAttack(living);
        }

        CompoundTag data = attribution(projectile);
        if (data == null) {
            return false;
        }
        if (data.hasUUID(SHOOTER_KEY) && data.getUUID(SHOOTER_KEY).equals(target.getUUID())) {
            return true;
        }
        if (data.hasUUID(OWNER_KEY)) {
            UUID owner = data.getUUID(OWNER_KEY);
            if (owner.equals(target.getUUID())) {
                return true;
            }
            if (target instanceof AbstractRecruitEntity recruit
                    && owner.equals(recruit.getOwnerUUID())) {
                return true;
            }
        }
        return data.contains(TEAM_KEY)
                && target.getTeam() != null
                && data.getString(TEAM_KEY).equals(target.getTeam().getName());
    }

    /**
     * The stored attribution of a projectile that has no live owner left to ask.
     *
     * <p>Forge's {@code getPersistentData()} creates the compound on first read, keeps it, and then
     * writes it to the entity's save data as {@code ForgeData}. These tests run on every arrow in
     * the world, on every tick, so anything that can be answered without the tag is answered first
     * — otherwise a permanent empty compound is minted for arrows this mod never fired.</p>
     *
     * <p>A live owner answers the question by itself. Beyond that, only a projectile class one of
     * these integrations actually spawns can carry the mark, because {@link #mark} is the only
     * writer: a skeleton's arrow whose shooter has died, a dispenser's arrow, or anything left over
     * in an unloaded chunk reaches this with no owner and must be turned away untouched.</p>
     *
     * <p>The client is refused outright. This mark is never synced, so a client always reads an
     * empty compound, and {@code getOwner()} resolves no owner at all on that side — every arrow in
     * view would otherwise be tagged for the sake of an answer that is always negative.</p>
     */
    private static CompoundTag attribution(AbstractArrow projectile) {
        if (projectile == null
                || projectile.level().isClientSide
                || projectile.getOwner() != null
                || !RecruitWeaponAdapters.production().isSupportedProjectile(projectile.getClass())) {
            return null;
        }
        return projectile.getPersistentData();
    }

    private static Optional<AbstractRecruitEntity> liveShooter(AbstractArrow projectile) {
        if (projectile != null && projectile.getOwner() instanceof AbstractRecruitEntity recruit) {
            return Optional.of(recruit);
        }
        return Optional.empty();
    }
}
