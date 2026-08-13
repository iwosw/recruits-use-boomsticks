package org.iwoss.recruits_use_boomsticks.mixin.artillery;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Lets a dedicated GameTest server start with Artillery Addon 1.14, which upstream cannot do.
 *
 * <p>{@code GunMaker$Events} carries a bare {@code @Mod.EventBusSubscriber} — no {@code Dist}
 * filter — while declaring {@code onEventTriggered(RenderPlayerEvent.Pre)} and {@code onClientTick},
 * neither marked {@code @OnlyIn(Dist.CLIENT)}. FML therefore loads the class on a dedicated server,
 * verifying it drags in {@code ClientLevel}, and Forge's own dist cleaner refuses:</p>
 *
 * <pre>
 * RuntimeException: Attempted to load class net/minecraft/client/multiplayer/ClientLevel
 *         for invalid dist DEDICATED_SERVER
 *     at AutomaticEventSubscriber.inject(AutomaticEventSubscriber.java:48)
 * </pre>
 *
 * <p>The package does not exist at all in the pinned 1.11 artifact, which is exactly why that one
 * boots a server and 1.14 does not. Removing the two client-only listeners before verification
 * leaves nothing to resolve. {@code onPlayerTick} is deliberately kept: it runs on both dists, and
 * Artillery's own 1.14 Handgonne, Noble Gonne, and Bronze Gonne procedures call into
 * {@code GunMaker}.</p>
 *
 * <p><b>Scope.</b> This is verification scaffolding for {@code runGameTestServer -Partillery=1.14},
 * not a shipped compatibility claim. Its config is registered for development runs only and is not
 * listed in the release jar's {@code MixinConfigs} manifest entry, and the plugin additionally
 * refuses to apply outside a dev dedicated server. Patching another mod's binary for players would
 * make this project answerable for Artillery's server stability, which it cannot be: this fixes the
 * first client-only leak in that artifact, not every one it may contain. The upstream fix is one
 * annotation — {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} — and belongs upstream.</p>
 */
public class ArtilleryDedicatedServerTestPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARTILLERY_MOD_ID = "artillery_addon";
    /** Listeners whose verification pulls in client-only classes on a dedicated server. */
    private static final Set<String> CLIENT_ONLY_LISTENERS = Set.of("onEventTriggered", "onClientTick");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return isDevelopmentDedicatedServer() && isArtilleryPresent();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        List<MethodNode> methods = targetClass.methods;
        int before = methods.size();
        methods.removeIf(method -> CLIENT_ONLY_LISTENERS.contains(method.name));
        LOGGER.info(
                "Removed {} client-only listener(s) from {} so this dedicated test server can load"
                        + " Artillery 1.14; this patch is development-only",
                before - methods.size(),
                targetClassName);
    }

    /**
     * Production never sees this patch. A dev client does not need it either: the leak only trips
     * the dist cleaner on a dedicated server.
     */
    private static boolean isDevelopmentDedicatedServer() {
        try {
            return !FMLLoader.isProduction() && FMLLoader.getDist().isDedicatedServer();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static boolean isArtilleryPresent() {
        try {
            return FMLLoader.getLoadingModList().getModFileById(ARTILLERY_MOD_ID) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }
}
