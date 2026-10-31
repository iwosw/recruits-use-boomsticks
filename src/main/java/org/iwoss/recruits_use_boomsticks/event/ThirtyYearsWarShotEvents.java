package org.iwoss.recruits_use_boomsticks.event;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickDamagePolicy;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponIntegration;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

/** Native raycast bullets have a living attacker, with no AbstractArrow impact event. */
@Mod.EventBusSubscriber(modid = RecruitsUseBoomsticks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThirtyYearsWarShotEvents {
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.tryParse("thirtyyearswar:bullet"));

    private ThirtyYearsWarShotEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        CrossBowmanEntity shooter = shooter(event.getSource());
        if (shooter == null) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (isProtected(shooter, target)) {
            event.setCanceled(true);
            return;
        }
        if (CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.get()) {
            // Instant bullets have no ProjectileImpactEvent to clear the same volley guard there.
            target.invulnerableTime = 0;
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (shooter(event.getSource()) != null) {
            event.setAmount(BoomstickDamagePolicy.configuredDamage(
                    event.getAmount(), RecruitWeaponIntegration.THIRTY_YEARS_WAR));
        }
    }

    /** Owner, same-owner recruits, allies, and anything the recruit rides with never take its bullets. */
    public static boolean isProtected(CrossBowmanEntity shooter, LivingEntity target) {
        return shooter == target || shooter.isAlliedTo(target) || !shooter.canAttack(target)
                || target.getRootVehicle() == shooter.getRootVehicle()
                || target.getUUID().equals(shooter.getOwnerUUID())
                || target instanceof AbstractRecruitEntity recruit && shooter.getOwnerUUID() != null
                && shooter.getOwnerUUID().equals(recruit.getOwnerUUID());
    }

    private static CrossBowmanEntity shooter(DamageSource source) {
        return source.is(BULLET) && source.getEntity() instanceof CrossBowmanEntity recruit
                && !recruit.level().isClientSide
                && CompatConfig.isIntegrationEnabled(RecruitWeaponIntegration.THIRTY_YEARS_WAR)
                ? recruit : null;
    }
}
