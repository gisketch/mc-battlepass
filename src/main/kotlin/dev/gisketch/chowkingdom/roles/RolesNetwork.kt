package dev.gisketch.chowkingdom.roles

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

object RolesNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun syncTo(player: ServerPlayer, openOnboarding: Boolean) {
        PacketDistributor.sendToPlayer(player, createSyncPayload(player, openOnboarding))
    }

    fun choose(jobId: String, classId: String) {
        runCatching { PacketDistributor.sendToServer(RolesChoosePayload(jobId, classId)) }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(RolesSyncPayload.TYPE, RolesSyncPayload.STREAM_CODEC, ::handleSyncClient)
        registrar.playToServer(RolesChoosePayload.TYPE, RolesChoosePayload.STREAM_CODEC, ::handleChoose)
    }

    private fun handleSyncClient(payload: RolesSyncPayload, context: IPayloadContext) {
        if (!FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.roles.RolesClient")
                client.getMethod("sync", RolesSyncPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }.onFailure { exception ->
                ChowKingdomMod.LOGGER.warn("Failed to open roles onboarding screen", exception)
            }
        }
    }

    private fun handleChoose(payload: RolesChoosePayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        RolesFeature.applyOnboardingChoice(player, payload.jobId, payload.classId)
    }

    private fun createSyncPayload(player: ServerPlayer, openOnboarding: Boolean): RolesSyncPayload {
        val record = RoleStore.role(player)
        return RolesSyncPayload(
            jobs = RolesConfig.jobs().map(::definitionPayload),
            classes = RolesConfig.classes().map(::definitionPayload),
            activeJobIds = record.activeJobIds.toList(),
            activeClassIds = record.activeClassIds.toList(),
            welcomeContent = RolesConfig.welcomeContent(),
            openOnboarding = openOnboarding,
        )
    }

    private fun definitionPayload(role: RoleDefinition): RoleUiDefinitionPayload = RoleUiDefinitionPayload(
        id = role.id,
        displayName = role.displayName.ifBlank { role.id },
        icon = role.icon.ifBlank { DEFAULT_ROLE_ICON },
        description = role.description.ifBlank { "A Chowkingdom role waiting for a proper description." },
    )
}

data class RolesSyncPayload(
    val jobs: List<RoleUiDefinitionPayload>,
    val classes: List<RoleUiDefinitionPayload>,
    val activeJobIds: List<String>,
    val activeClassIds: List<String>,
    val welcomeContent: String,
    val openOnboarding: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RolesSyncPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RolesSyncPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "roles/sync"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RolesSyncPayload> = object : StreamCodec<RegistryFriendlyByteBuf, RolesSyncPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RolesSyncPayload = RolesSyncPayload(
                jobs = List(buffer.readVarInt().coerceIn(0, MAX_ROLES)) { RoleUiDefinitionPayload.decode(buffer) },
                classes = List(buffer.readVarInt().coerceIn(0, MAX_ROLES)) { RoleUiDefinitionPayload.decode(buffer) },
                activeJobIds = readStringList(buffer, MAX_ACTIVE_ROLES, MAX_ID_LENGTH),
                activeClassIds = readStringList(buffer, MAX_ACTIVE_ROLES, MAX_ID_LENGTH),
                welcomeContent = buffer.readUtf(MAX_DESCRIPTION_LENGTH),
                openOnboarding = buffer.readBoolean(),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RolesSyncPayload) {
                val jobs = value.jobs.take(MAX_ROLES)
                buffer.writeVarInt(jobs.size)
                jobs.forEach { role -> role.encode(buffer) }
                val classes = value.classes.take(MAX_ROLES)
                buffer.writeVarInt(classes.size)
                classes.forEach { role -> role.encode(buffer) }
                writeStringList(buffer, value.activeJobIds, MAX_ACTIVE_ROLES, MAX_ID_LENGTH)
                writeStringList(buffer, value.activeClassIds, MAX_ACTIVE_ROLES, MAX_ID_LENGTH)
                buffer.writeUtf(value.welcomeContent.take(MAX_DESCRIPTION_LENGTH), MAX_DESCRIPTION_LENGTH)
                buffer.writeBoolean(value.openOnboarding)
            }
        }
    }
}

data class RoleUiDefinitionPayload(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(id.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
        buffer.writeUtf(displayName.take(MAX_DISPLAY_LENGTH), MAX_DISPLAY_LENGTH)
        buffer.writeUtf(icon.take(MAX_ICON_LENGTH), MAX_ICON_LENGTH)
        buffer.writeUtf(description.take(MAX_DESCRIPTION_LENGTH), MAX_DESCRIPTION_LENGTH)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RoleUiDefinitionPayload = RoleUiDefinitionPayload(
            id = buffer.readUtf(MAX_ID_LENGTH),
            displayName = buffer.readUtf(MAX_DISPLAY_LENGTH),
            icon = buffer.readUtf(MAX_ICON_LENGTH),
            description = buffer.readUtf(MAX_DESCRIPTION_LENGTH),
        )
    }
}

data class RolesChoosePayload(
    val jobId: String,
    val classId: String,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RolesChoosePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RolesChoosePayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "roles/choose"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RolesChoosePayload> = object : StreamCodec<RegistryFriendlyByteBuf, RolesChoosePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): RolesChoosePayload = RolesChoosePayload(
                jobId = buffer.readUtf(MAX_ID_LENGTH),
                classId = buffer.readUtf(MAX_ID_LENGTH),
            )

            override fun encode(buffer: RegistryFriendlyByteBuf, value: RolesChoosePayload) {
                buffer.writeUtf(value.jobId.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
                buffer.writeUtf(value.classId.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
            }
        }
    }
}

private fun readStringList(buffer: RegistryFriendlyByteBuf, maxCount: Int, maxLength: Int): List<String> =
    List(buffer.readVarInt().coerceIn(0, maxCount)) { buffer.readUtf(maxLength) }

private fun writeStringList(buffer: RegistryFriendlyByteBuf, values: List<String>, maxCount: Int, maxLength: Int) {
    val limited = values.take(maxCount)
    buffer.writeVarInt(limited.size)
    limited.forEach { value -> buffer.writeUtf(value.take(maxLength), maxLength) }
}

private const val DEFAULT_ROLE_ICON = "minecraft:grass_block"
private const val MAX_ROLES = 128
private const val MAX_ACTIVE_ROLES = 32
private const val MAX_ID_LENGTH = 64
private const val MAX_DISPLAY_LENGTH = 96
private const val MAX_ICON_LENGTH = 128
private const val MAX_DESCRIPTION_LENGTH = 512
