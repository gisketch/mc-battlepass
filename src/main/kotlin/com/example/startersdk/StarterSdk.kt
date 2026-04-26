package com.example.startersdk

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger

@Mod(StarterSdk.MOD_ID)
class StarterSdk(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Loading {}", container.modInfo.displayName)
        StarterSdkContent.register(modBus)
    }

    companion object {
        const val MOD_ID = "starter_sdk"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}