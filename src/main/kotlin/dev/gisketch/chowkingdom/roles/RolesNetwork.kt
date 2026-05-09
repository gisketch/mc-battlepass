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
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

object RolesNetwork {
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    fun syncTo(player: ServerPlayer, openOnboarding: Boolean) {
        PacketDistributor.sendToPlayer(player, createSyncPayload(player, openOnboarding))
    }

    fun syncAllPlayers(openOnboardingFor: Set<UUID> = emptySet()) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.playerList.players.forEach { player -> syncTo(player, openOnboarding = player.uuid in openOnboardingFor) }
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
            players = playerStates(player),
            welcomeContent = RolesConfig.welcomeContent(),
            openOnboarding = openOnboarding,
            jobRankUnlockOverallLevels = JobLevels.jobRankUnlockOverallLevels(),
            catchRateBonusPercentByRank = JobLevels.catchRateBonusPercentByRank(),
            mountSpeedBonusPercentByRank = JobLevels.mountSpeedBonusPercentByRank(),
        )
    }

    private fun playerStates(receiver: ServerPlayer): List<RolePlayerStatePayload> = receiver.server.playerList.players.map { player ->
        RolePlayerStatePayload(
            playerId = player.uuid,
            jobIds = RoleStore.activeJobIds(player).toList(),
            classIds = RoleStore.activeClassIds(player).toList(),
        )
    }

    private fun definitionPayload(role: RoleDefinition): RoleUiDefinitionPayload = RoleUiDefinitionPayload(
        id = role.id,
        displayName = role.displayName.ifBlank { role.id },
        icon = role.icon.ifBlank { DEFAULT_ROLE_ICON },
        description = role.description.ifBlank { "A Chowkingdom role waiting for a proper description." },
        perks = role.perks.map(::perkPayload),
    )

    private fun perkPayload(perk: RolePerkDefinition): RolePerkUiPayload = RolePerkUiPayload(
        type = perk.type,
        pokemonType = perk.pokemonType.orEmpty(),
        multiplier = perk.multiplier,
        bonusPercentByLevel = perk.bonusPercentByLevel.toList(),
        weaponTag = perk.weaponTag.orEmpty(),
        armorTag = perk.armorTag.orEmpty(),
        weaponTags = perk.weaponTags.toList(),
        armorTags = perk.armorTags.toList(),
        weaponPatterns = perk.weaponPatterns.toList(),
        armorPatterns = perk.armorPatterns.toList(),
        startingItems = perk.startingItems.toList(),
    )
}

data class RolesSyncPayload(
    val jobs: List<RoleUiDefinitionPayload>,
    val classes: List<RoleUiDefinitionPayload>,
    val activeJobIds: List<String>,
    val activeClassIds: List<String>,
    val players: List<RolePlayerStatePayload>,
    val welcomeContent: String,
    val openOnboarding: Boolean,
    val jobRankUnlockOverallLevels: List<Int>,
    val catchRateBonusPercentByRank: List<Double>,
    val mountSpeedBonusPercentByRank: List<Double>,
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
                players = List(buffer.readVarInt().coerceIn(0, MAX_PLAYERS)) { RolePlayerStatePayload.decode(buffer) },
                welcomeContent = buffer.readUtf(MAX_DESCRIPTION_LENGTH),
                openOnboarding = buffer.readBoolean(),
                jobRankUnlockOverallLevels = readIntList(buffer, MAX_JOB_RANKS),
                catchRateBonusPercentByRank = readDoubleList(buffer, MAX_JOB_RANKS),
                mountSpeedBonusPercentByRank = readDoubleList(buffer, MAX_JOB_RANKS),
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
                val players = value.players.take(MAX_PLAYERS)
                buffer.writeVarInt(players.size)
                players.forEach { player -> player.encode(buffer) }
                buffer.writeUtf(value.welcomeContent.take(MAX_DESCRIPTION_LENGTH), MAX_DESCRIPTION_LENGTH)
                buffer.writeBoolean(value.openOnboarding)
                writeIntList(buffer, value.jobRankUnlockOverallLevels, MAX_JOB_RANKS)
                writeDoubleList(buffer, value.catchRateBonusPercentByRank, MAX_JOB_RANKS)
                writeDoubleList(buffer, value.mountSpeedBonusPercentByRank, MAX_JOB_RANKS)
            }
        }
    }
}

data class RolePlayerStatePayload(
    val playerId: UUID,
    val jobIds: List<String>,
    val classIds: List<String>,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(playerId)
        writeStringList(buffer, jobIds, MAX_ACTIVE_ROLES, MAX_ID_LENGTH)
        writeStringList(buffer, classIds, MAX_ACTIVE_ROLES, MAX_ID_LENGTH)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RolePlayerStatePayload = RolePlayerStatePayload(
            playerId = buffer.readUUID(),
            jobIds = readStringList(buffer, MAX_ACTIVE_ROLES, MAX_ID_LENGTH),
            classIds = readStringList(buffer, MAX_ACTIVE_ROLES, MAX_ID_LENGTH),
        )
    }
}

data class RoleUiDefinitionPayload(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String,
    val perks: List<RolePerkUiPayload>,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(id.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
        buffer.writeUtf(displayName.take(MAX_DISPLAY_LENGTH), MAX_DISPLAY_LENGTH)
        buffer.writeUtf(icon.take(MAX_ICON_LENGTH), MAX_ICON_LENGTH)
        buffer.writeUtf(description.take(MAX_DESCRIPTION_LENGTH), MAX_DESCRIPTION_LENGTH)
        val perks = perks.take(MAX_PERKS)
        buffer.writeVarInt(perks.size)
        perks.forEach { perk -> perk.encode(buffer) }
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RoleUiDefinitionPayload = RoleUiDefinitionPayload(
            id = buffer.readUtf(MAX_ID_LENGTH),
            displayName = buffer.readUtf(MAX_DISPLAY_LENGTH),
            icon = buffer.readUtf(MAX_ICON_LENGTH),
            description = buffer.readUtf(MAX_DESCRIPTION_LENGTH),
            perks = List(buffer.readVarInt().coerceIn(0, MAX_PERKS)) { RolePerkUiPayload.decode(buffer) },
        )
    }
}

data class RolePerkUiPayload(
    val type: String,
    val pokemonType: String,
    val multiplier: Double,
    val bonusPercentByLevel: List<Double>,
    val weaponTag: String,
    val armorTag: String,
    val weaponTags: List<String>,
    val armorTags: List<String>,
    val weaponPatterns: List<String>,
    val armorPatterns: List<String>,
    val startingItems: List<String>,
) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(type.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
        buffer.writeUtf(pokemonType.take(MAX_ID_LENGTH), MAX_ID_LENGTH)
        buffer.writeDouble(multiplier)
        writeDoubleList(buffer, bonusPercentByLevel, MAX_JOB_RANKS)
        buffer.writeUtf(weaponTag.take(MAX_ICON_LENGTH), MAX_ICON_LENGTH)
        buffer.writeUtf(armorTag.take(MAX_ICON_LENGTH), MAX_ICON_LENGTH)
        writeStringList(buffer, weaponTags, MAX_PERK_VALUES, MAX_ICON_LENGTH)
        writeStringList(buffer, armorTags, MAX_PERK_VALUES, MAX_ICON_LENGTH)
        writeStringList(buffer, weaponPatterns, MAX_PERK_VALUES, MAX_ICON_LENGTH)
        writeStringList(buffer, armorPatterns, MAX_PERK_VALUES, MAX_ICON_LENGTH)
        writeStringList(buffer, startingItems, MAX_PERK_VALUES, MAX_ICON_LENGTH)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RolePerkUiPayload = RolePerkUiPayload(
            type = buffer.readUtf(MAX_ID_LENGTH),
            pokemonType = buffer.readUtf(MAX_ID_LENGTH),
            multiplier = buffer.readDouble(),
            bonusPercentByLevel = readDoubleList(buffer, MAX_JOB_RANKS),
            weaponTag = buffer.readUtf(MAX_ICON_LENGTH),
            armorTag = buffer.readUtf(MAX_ICON_LENGTH),
            weaponTags = readStringList(buffer, MAX_PERK_VALUES, MAX_ICON_LENGTH),
            armorTags = readStringList(buffer, MAX_PERK_VALUES, MAX_ICON_LENGTH),
            weaponPatterns = readStringList(buffer, MAX_PERK_VALUES, MAX_ICON_LENGTH),
            armorPatterns = readStringList(buffer, MAX_PERK_VALUES, MAX_ICON_LENGTH),
            startingItems = readStringList(buffer, MAX_PERK_VALUES, MAX_ICON_LENGTH),
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

private fun readIntList(buffer: RegistryFriendlyByteBuf, maxCount: Int): List<Int> =
    List(buffer.readVarInt().coerceIn(0, maxCount)) { buffer.readVarInt() }

private fun writeIntList(buffer: RegistryFriendlyByteBuf, values: List<Int>, maxCount: Int) {
    val limited = values.take(maxCount)
    buffer.writeVarInt(limited.size)
    limited.forEach(buffer::writeVarInt)
}

private fun readDoubleList(buffer: RegistryFriendlyByteBuf, maxCount: Int): List<Double> =
    List(buffer.readVarInt().coerceIn(0, maxCount)) { buffer.readDouble() }

private fun writeDoubleList(buffer: RegistryFriendlyByteBuf, values: List<Double>, maxCount: Int) {
    val limited = values.take(maxCount)
    buffer.writeVarInt(limited.size)
    limited.forEach(buffer::writeDouble)
}

private const val DEFAULT_ROLE_ICON = "minecraft:grass_block"
private const val MAX_ROLES = 128
private const val MAX_PLAYERS = 256
private const val MAX_ACTIVE_ROLES = 32
private const val MAX_PERKS = 32
private const val MAX_PERK_VALUES = 32
private const val MAX_JOB_RANKS = 16
private const val MAX_ID_LENGTH = 64
private const val MAX_DISPLAY_LENGTH = 96
private const val MAX_ICON_LENGTH = 128
private const val MAX_DESCRIPTION_LENGTH = 512
