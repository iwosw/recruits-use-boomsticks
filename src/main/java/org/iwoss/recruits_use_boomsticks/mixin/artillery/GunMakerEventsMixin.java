package org.iwoss.recruits_use_boomsticks.mixin.artillery;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Empty target hook for {@link ArtilleryDedicatedServerTestPlugin}.
 *
 * <p>Mixin only hands a config plugin the class node of a target something actually mixes into, and
 * the work here is a removal rather than an injection, so this mixin deliberately declares no
 * members: it exists to make Artillery's {@code GunMaker$Events} a target so the plugin can strip
 * its client-only listeners before the class is verified.</p>
 *
 * <p>This is test-harness scaffolding, not shipped behaviour. Its config is registered for
 * development runs only and is absent from the release jar's {@code MixinConfigs} manifest entry.</p>
 */
@Pseudo
@Mixin(targets = "net.mcreator.artilleryaddon.gun_maker.GunMaker$Events", remap = false)
public abstract class GunMakerEventsMixin {
}
