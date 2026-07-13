package org.iwoss.recruits_use_boomsticks;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.slf4j.Logger;

/**
 * Independent compatibility layer between Recruits and Medieval Boomsticks.
 */
@Mod(RecruitsUseBoomsticks.MOD_ID)
public final class RecruitsUseBoomsticks {
    public static final String MOD_ID = "recruits_use_boomsticks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecruitsUseBoomsticks() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CompatConfig.SPEC);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} {} loaded; Recruits={}, Medieval Boomsticks={}, GeckoLib={}",
                MOD_ID,
                ModList.get().getModContainerById(MOD_ID)
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("unknown"),
                dependencyVersion("recruits"),
                dependencyVersion("medieval_boomsticks"),
                dependencyVersion("geckolib"));
    }

    private static String dependencyVersion(final String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }
}
