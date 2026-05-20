package dev.gisketch.chowkingdom.battlepass

import com.mojang.blaze3d.platform.InputConstants
import dev.gisketch.chowkingdom.ChowKingdomMod
import dev.gisketch.chowkingdom.client.InventoryMenuClient
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier
import net.neoforged.neoforge.common.NeoForge

object BattlepassClient {
    private const val CATEGORY = "key.category.${ChowKingdomMod.MOD_ID}"

    val OPEN_BATTLEPASS: KeyMapping = KeyMapping(
        "key.${ChowKingdomMod.MOD_ID}.battlepass.open",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_B,
        CATEGORY,
    )

    fun register(modBus: IEventBus) {
        modBus.addListener(::registerKeyMappings)
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::hideHudBehindBattlepass)
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_BATTLEPASS)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        while (OPEN_BATTLEPASS.consumeClick()) {
            openInventoryPassMenu()
        }
    }

    private fun hideHudBehindBattlepass(event: RenderGuiLayerEvent.Pre) {
        if (Minecraft.getInstance().screen is BattlepassScreen) {
            event.isCanceled = true
        }
    }

    fun openBattlepass(initialPassId: String? = null) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.screen is BattlepassScreen) return

        BattlepassNetwork.requestSync()
        minecraft.setScreen(BattlepassScreen(initialPassId))
    }

    private fun openInventoryPassMenu() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        BattlepassNetwork.requestSync()
        InventoryMenuClient.openPassMenuOnNextInventory()
        if (minecraft.screen !is InventoryScreen) minecraft.setScreen(InventoryScreen(player))
    }
}
