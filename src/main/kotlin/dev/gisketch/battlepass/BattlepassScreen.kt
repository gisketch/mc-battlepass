package dev.gisketch.battlepass

import io.wispforest.owo.ui.base.BaseOwoScreen
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.HorizontalAlignment
import io.wispforest.owo.ui.core.Insets
import io.wispforest.owo.ui.core.OwoUIAdapter
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.Surface
import io.wispforest.owo.ui.core.VerticalAlignment
import io.wispforest.owo.ui.event.MouseDown
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class BattlepassScreen : BaseOwoScreen<FlowLayout>(Component.translatable("screen.${BattlepassMod.MOD_ID}.battlepass")) {
    private enum class ViewMode { PASS_SELECTION, PASS_DETAIL }

    private lateinit var content: FlowLayout
    private var selectedPassId: String? = null
    private var viewMode = ViewMode.PASS_SELECTION

    override fun createAdapter(): OwoUIAdapter<FlowLayout> = OwoUIAdapter.create(this, Containers::verticalFlow)

    override fun build(rootComponent: FlowLayout) {
        BattlepassPassRegistry.reload()
        selectedPassId = selectedPassId ?: BattlepassPassRegistry.all().firstOrNull()?.id

        rootComponent
            .surface(Surface.VANILLA_TRANSLUCENT)
            .alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER)
            .padding(Insets.of(10))

        content = Containers.verticalFlow(Sizing.fill(100), Sizing.expand()).gap(8)

        val panel = Containers.verticalFlow(Sizing.fill(96), Sizing.fill(88))
        panel.surface(Surface.flat(0xDD10141C.toInt()).and(Surface.outline(0x663A70C4)))
        panel.padding(Insets.of(8))
        panel.gap(8)
        panel.child(Components.label(Component.translatable("screen.${BattlepassMod.MOD_ID}.battlepass")).shadow(true))
        panel.child(content)
        rootComponent.child(panel)

        rebuild()
    }

    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        super.removed()
        BattlepassCameraController.stop()
    }

    private fun rebuild() {
        content.clearChildren()

        if (viewMode == ViewMode.PASS_SELECTION) {
            buildSelection()
        } else {
            buildDetail()
        }
    }

    private fun buildSelection() {
        content.child(Components.label(Component.translatable("ui.${BattlepassMod.MOD_ID}.select_pass")).shadow(true))

        val list = Containers.verticalFlow(Sizing.fill(42), Sizing.content()).gap(4)
        BattlepassPassRegistry.all().forEach { pass -> list.child(passRow(pass)) }
        if (BattlepassPassRegistry.all().isEmpty()) {
            list.child(Components.label(Component.translatable("ui.${BattlepassMod.MOD_ID}.empty")).shadow(true))
        }
        content.child(Containers.verticalScroll(Sizing.fill(46), Sizing.expand(), list))
    }

    private fun buildDetail() {
        val passes = BattlepassPassRegistry.all().toList()
        val selectedPass = passes.firstOrNull { pass -> pass.id == selectedPassId } ?: run {
            viewMode = ViewMode.PASS_SELECTION
            buildSelection()
            return
        }

        val playerId = Minecraft.getInstance().player?.uuid
        val currentXp = playerId?.let { BattlepassXpStore.getXp(it, selectedPass.id) } ?: 0

        val topRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24))
        topRow.gap(6)
        topRow.verticalAlignment(VerticalAlignment.CENTER)
        topRow.child(backButton())
        topRow.child(Components.label(Component.literal(selectedPass.displayName)).shadow(true).maxWidth(220))
        content.child(topRow)

        val infoPanel = Containers.verticalFlow(Sizing.fill(42), Sizing.content())
        infoPanel.surface(Surface.PANEL)
        infoPanel.padding(Insets.of(6))
        infoPanel.gap(4)
        infoPanel.child(Components.label(Component.literal("XP: $currentXp").withStyle(ChatFormatting.AQUA)).shadow(true))
        infoPanel.child(Components.label(Component.literal(selectedPass.description).withStyle(ChatFormatting.GRAY)).maxWidth(360))
        content.child(infoPanel)

        val spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.expand())
        content.child(spacer)

        content.child(Components.label(Component.translatable("ui.${BattlepassMod.MOD_ID}.rewards")).shadow(true))
        val rewardRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(68))
        rewardRow.gap(6)
        selectedPass.progression.sortedBy { tier -> tier.xp }.forEach { tier -> rewardRow.child(rewardTierRow(tier, currentXp)) }
        val hotbar = Containers.horizontalScroll(Sizing.fill(100), Sizing.fixed(78), rewardRow)
        hotbar.surface(Surface.flat(0xAA05070A.toInt()).and(Surface.outline(0x884C566A.toInt())))
        hotbar.padding(Insets.of(6))
        content.child(hotbar)
    }

    private fun backButton(): FlowLayout {
        val row = Containers.horizontalFlow(Sizing.fixed(54), Sizing.fixed(22))
        row.surface(Surface.PANEL)
        row.padding(Insets.of(4))
        row.verticalAlignment(VerticalAlignment.CENTER)
        row.horizontalAlignment(HorizontalAlignment.CENTER)
        row.child(Components.label(Component.translatable("ui.${BattlepassMod.MOD_ID}.back")).shadow(true))
        row.mouseDown().subscribe(MouseDown { _, _, button ->
            if (button == 0) {
                viewMode = ViewMode.PASS_SELECTION
                rebuild()
                true
            } else {
                false
            }
        })
        return row
    }

    private fun passRow(pass: BattlepassPassDefinition): FlowLayout {
        val selected = pass.id == selectedPassId
        val row = Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        row.surface(if (selected) Surface.flat(0x883A70C4.toInt()).and(Surface.outline(0xFF8DB3FF.toInt())) else Surface.DARK_PANEL)
        row.padding(Insets.of(5))
        row.gap(2)
        row.child(Components.label(Component.literal(pass.displayName)).shadow(true).maxWidth(220))
        row.child(Components.label(Component.literal(pass.categories.joinToString(" | ")).withStyle(ChatFormatting.GRAY)).maxWidth(220))
        row.mouseDown().subscribe(MouseDown { _, _, button ->
            if (button == 0) {
                selectedPassId = pass.id
                viewMode = ViewMode.PASS_DETAIL
                rebuild()
                true
            } else {
                false
            }
        })
        return row
    }

    private fun rewardTierRow(tier: BattlepassProgressionDefinition, currentXp: Int): FlowLayout {
        val unlocked = currentXp >= tier.xp
        val row = Containers.verticalFlow(Sizing.fixed(104), Sizing.fixed(64))
        row.surface(if (unlocked) Surface.PANEL else Surface.flat(0x66303030))
        row.padding(Insets.of(4))
        row.gap(3)
        row.horizontalAlignment(HorizontalAlignment.CENTER)
        row.child(Components.label(lockLabel(unlocked, tier.xp)).shadow(true).maxWidth(92))
        val rewards = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(22))
        rewards.gap(4)
        rewards.verticalAlignment(VerticalAlignment.CENTER)
        tier.rewards.take(3).forEach { reward -> rewards.child(rewardIcon(reward, unlocked)) }
        row.child(rewards)
        return row
    }

    private fun lockLabel(unlocked: Boolean, xp: Int): Component {
        val label = if (unlocked) "Open" else "Lock"
        val style = if (unlocked) ChatFormatting.GREEN else ChatFormatting.DARK_GRAY
        return Component.literal("$label $xp XP").withStyle(style)
    }

    private fun rewardIcon(reward: BattlepassRewardDefinition, unlocked: Boolean): io.wispforest.owo.ui.core.Component {
        val stack = rewardStack(reward)
        val labelStyle = if (unlocked) ChatFormatting.WHITE else ChatFormatting.DARK_GRAY
        val group = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20))
        group.gap(4)
        group.verticalAlignment(VerticalAlignment.CENTER)
        group.child(Components.item(stack).showOverlay(false))
        group.child(Components.label(Component.literal("x${reward.quantity}").withStyle(labelStyle)).shadow(unlocked))
        return group
    }

    private fun rewardStack(reward: BattlepassRewardDefinition): ItemStack {
        val item = runCatching { ResourceLocation.parse(reward.item) }.getOrNull()
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER) }
            ?: Items.BARRIER
        return ItemStack(item, reward.quantity.coerceIn(1, 64))
    }
}