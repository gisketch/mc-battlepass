package dev.gisketch.chowkingdom.shops

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class VendorContractItem(properties: Properties) : Item(properties) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltipComponents: MutableList<Component>, tooltipFlag: TooltipFlag) {
        val links = VendorContractData.links(stack)
        tooltipComponents += Component.literal("${links.size}/${VendorContractConfig.maxLinks()} shops linked")
        if (links.isEmpty()) tooltipComponents += Component.literal("Right-click priced shops with an item to link.")
        else tooltipComponents += Component.literal("Right-click a mob to sign.")
    }
}

object VendorContractFeature {
    private var tickCounter = 0

    fun register(modBus: IEventBus) {
        VendorContractConfig.load()
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onRightClickBlock)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteractSpecific)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ::onEntityInteract)
        NeoForge.EVENT_BUS.addListener(::onLivingDeath)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(VendorOpenPayload.TYPE, VendorOpenPayload.STREAM_CODEC, ::handleOpenClient)
        registrar.playToClient(VendorContractSelectionPayload.TYPE, VendorContractSelectionPayload.STREAM_CODEC, ::handleSelectionClient)
        registrar.playToServer(VendorBuyPayload.TYPE, VendorBuyPayload.STREAM_CODEC, ::handleBuy)
        registrar.playToServer(VendorVoidPayload.TYPE, VendorVoidPayload.STREAM_CODEC, ::handleVoid)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        VendorContractConfig.load()
    }

    private fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity
        if (event.hand != InteractionHand.MAIN_HAND) return
        val stack = player.getItemInHand(event.hand)
        if (!isContract(stack)) return
        val shop = player.level().getBlockEntity(event.pos) as? ShopBlockEntity ?: return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        if (player.level().isClientSide) return
        player as? ServerPlayer ?: return
        if (player.isShiftKeyDown) unlinkShop(player, stack, shop)
        else linkShop(player, stack, shop)
        syncContractSelection(player)
    }

    private fun onEntityInteractSpecific(event: PlayerInteractEvent.EntityInteractSpecific) {
        handleEntityInteract(event.entity, event.target, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        handleEntityInteract(event.entity, event.target, event.hand) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun handleEntityInteract(player: Player, target: Entity, hand: InteractionHand, cancel: () -> Unit) {
        if (hand != InteractionHand.MAIN_HAND) return
        val seller = target as? Mob ?: return
        if (SellerData.isSeller(seller)) {
            cancel()
            if (!player.level().isClientSide && player is ServerPlayer) openVendor(player, seller)
            return
        }
        val stack = player.getItemInHand(hand)
        if (!isContract(stack) || VendorContractData.links(stack).isEmpty()) return
        cancel()
        if (!player.level().isClientSide && player is ServerPlayer) signSeller(player, seller, stack)
    }

    private fun linkShop(player: ServerPlayer, stack: ItemStack, shop: ShopBlockEntity) {
        val key = ShopKey.from(player.level(), shop.blockPos)
        if (!isValidLinkedShop(shop)) {
            player.displayClientMessage(Component.literal("Shop needs an item, owner, and price."), true)
            return
        }
        val links = VendorContractData.links(stack).toMutableList()
        if (links.any { it == key }) {
            player.displayClientMessage(Component.literal("Shop already linked (${links.size}/${VendorContractConfig.maxLinks()})."), true)
            return
        }
        if (links.size >= VendorContractConfig.maxLinks()) {
            player.displayClientMessage(Component.literal("Contract is full (${VendorContractConfig.maxLinks()} shops)."), true)
            return
        }
        links += key
        VendorContractData.saveLinks(stack, links)
        player.displayClientMessage(Component.literal("Linked ${shop.displayItem.hoverName.string} (${links.size}/${VendorContractConfig.maxLinks()})."), true)
    }

    private fun unlinkShop(player: ServerPlayer, stack: ItemStack, shop: ShopBlockEntity) {
        val key = ShopKey.from(player.level(), shop.blockPos)
        val links = VendorContractData.links(stack)
        val updated = links.filterNot { it == key }
        if (updated.size == links.size) {
            player.displayClientMessage(Component.literal("Shop is not linked."), true)
            return
        }
        VendorContractData.saveLinks(stack, updated)
        player.displayClientMessage(Component.literal("Unlinked shop (${updated.size}/${VendorContractConfig.maxLinks()})."), true)
    }

    private fun signSeller(player: ServerPlayer, seller: Mob, stack: ItemStack) {
        val links = VendorContractData.links(stack).filter { resolveShop(player.server, it)?.let(::isValidLinkedShop) == true }.take(VendorContractConfig.maxLinks())
        if (links.isEmpty()) {
            player.displayClientMessage(Component.literal("No loaded valid shops on this contract."), true)
            return
        }
        SellerData.save(seller, player, links)
        seller.setNoAi(true)
        seller.setPersistenceRequired()
        seller.setDeltaMovement(Vec3.ZERO)
        stack.shrink(1)
        player.displayClientMessage(Component.literal("Contract Signed"), true)
        openVendor(player, seller)
    }

    private fun openVendor(player: ServerPlayer, seller: Mob) {
        val state = SellerData.read(seller) ?: return
        val entries = state.links.mapNotNull { key ->
            val shop = resolveShop(player.server, key) ?: return@mapNotNull null
            if (!isValidLinkedShop(shop)) return@mapNotNull null
            VendorEntry(key.dimension, key.pos, shop.displayItem.copy(), shop.stockCount, shop.price, shop.ownerName)
        }
        PacketDistributor.sendToPlayer(player, VendorOpenPayload(seller.uuid, state.ownerId == player.uuid || player.isCreative, entries))
    }

    private fun handleBuy(payload: VendorBuyPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val seller = findSeller(player.server, payload.sellerId) as? Mob ?: return
        val state = SellerData.read(seller) ?: return
        if (player.level() !== seller.level() || player.distanceToSqr(seller) > 64.0) return
        val key = ShopKey(payload.dimension, payload.pos)
        if (state.links.none { it == key }) return
        val shop = resolveShop(player.server, key) ?: return
        if (!isValidLinkedShop(shop)) return
        if (ShopStockNetwork.buyFromShop(player, shop, payload.quantity, requireOtherOwner = true)) openVendor(player, seller)
    }

    private fun handleVoid(payload: VendorVoidPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        val seller = findSeller(player.server, payload.sellerId) as? Mob ?: return
        val state = SellerData.read(seller) ?: return
        if (player.level() !== seller.level() || player.distanceToSqr(seller) > 64.0) return
        if (state.ownerId != player.uuid && !player.isCreative) return
        SellerData.clear(seller)
        seller.setNoAi(state.previousNoAi)
        val contract = contractStack(state.links)
        if (!player.inventory.add(contract)) player.drop(contract, false)
        player.displayClientMessage(Component.literal("Contract Voided"), true)
    }

    private fun handleOpenClient(payload: VendorOpenPayload, context: IPayloadContext) {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.shops.VendorContractClient")
                client.getMethod("openVendor", VendorOpenPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun handleSelectionClient(payload: VendorContractSelectionPayload, context: IPayloadContext) {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient) return
        context.enqueueWork {
            runCatching {
                val client = Class.forName("dev.gisketch.chowkingdom.shops.VendorContractClient")
                client.getMethod("syncSelection", VendorContractSelectionPayload::class.java).invoke(client.getField("INSTANCE").get(null), payload)
            }
        }
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        val seller = event.entity as? Mob ?: return
        val state = SellerData.read(seller) ?: return
        SellerData.clear(seller)
        seller.spawnAtLocation(contractStack(state.links))
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        tickCounter = (tickCounter + 1) % 10
        if (tickCounter != 0) return
        event.server.allLevels.forEach { level ->
            level.allEntities.forEach { entity ->
                val mob = entity as? Mob ?: return@forEach
                if (SellerData.isSeller(mob)) freezeSeller(mob)
            }
        }
        event.server.playerList.players.forEach(::syncContractSelection)
    }

    private fun freezeSeller(mob: Mob) {
        val state = SellerData.read(mob) ?: return
        mob.setNoAi(true)
        mob.setDeltaMovement(Vec3.ZERO)
        val dx = mob.x - state.anchorX
        val dy = mob.y - state.anchorY
        val dz = mob.z - state.anchorZ
        if (dx * dx + dy * dy + dz * dz > 0.01) mob.teleportTo(state.anchorX, state.anchorY, state.anchorZ)
        val nearest = (mob.level() as? ServerLevel)?.players()?.filter { it.distanceToSqr(mob) <= 100.0 }?.minByOrNull { it.distanceToSqr(mob) }
        if (nearest != null) mob.lookAt(nearest, 30.0f, 30.0f)
    }

    private fun syncContractSelection(player: ServerPlayer) {
        val stack = player.mainHandItem.takeIf(::isContract) ?: player.offhandItem.takeIf(::isContract)
        val dimension = player.level().dimension().location().toString()
        val positions = stack?.let { VendorContractData.links(it).filter { key -> key.dimension == dimension }.map { key -> key.pos } } ?: emptyList()
        PacketDistributor.sendToPlayer(player, VendorContractSelectionPayload(positions))
    }

    private fun contractStack(links: List<ShopKey>): ItemStack =
        ItemStack(ShopsFeature.VENDOR_CONTRACT_ITEM.get()).also { VendorContractData.saveLinks(it, links.take(VendorContractConfig.maxLinks())) }

    private fun resolveShop(server: MinecraftServer, key: ShopKey): ShopBlockEntity? {
        val level = server.getLevel(dimensionKey(key.dimension)) ?: return null
        if (!level.isLoaded(key.pos)) return null
        return level.getBlockEntity(key.pos) as? ShopBlockEntity
    }

    private fun findSeller(server: MinecraftServer, id: UUID): Entity? =
        server.allLevels.asSequence().flatMap { level -> level.allEntities.asSequence() }.firstOrNull { it.uuid == id && SellerData.isSeller(it) }

    private fun dimensionKey(raw: String): ResourceKey<net.minecraft.world.level.Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(raw))

    private fun isValidLinkedShop(shop: ShopBlockEntity): Boolean =
        shop.ownerUuid != null && shop.hasDisplayItem && shop.price > 0L

    private fun isContract(stack: ItemStack): Boolean = !stack.isEmpty && stack.item === ShopsFeature.VENDOR_CONTRACT_ITEM.get()
}

object VendorContractConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var config = VendorConfig()

    private val file: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ChowKingdomMod.MOD_ID).resolve("shops").resolve("vendor_contract.json")

    fun load() {
        file.parent.createDirectories()
        if (!file.exists()) writeDefault()
        config = try {
            file.bufferedReader().use { reader -> gson.fromJson(reader, VendorConfig::class.java) } ?: VendorConfig()
        } catch (exception: Exception) {
            ChowKingdomMod.LOGGER.warn("Failed to load vendor contract config {}", file, exception)
            VendorConfig()
        }
    }

    fun maxLinks(): Int = config.maxLinkedShops.coerceIn(1, 1_000)

    private fun writeDefault() {
        Files.createTempFile(file.parent, "vendor_contract", ".json.tmp").also { temp ->
            temp.bufferedWriter().use { writer -> gson.toJson(VendorConfig(), writer) }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class VendorConfig(
    @SerializedName("max_linked_shops") var maxLinkedShops: Int = 100,
)

data class ShopKey(val dimension: String, val pos: BlockPos) {
    companion object {
        fun from(level: net.minecraft.world.level.Level, pos: BlockPos): ShopKey = ShopKey(level.dimension().location().toString(), pos)
    }
}

object VendorContractData {
    fun links(stack: ItemStack): List<ShopKey> {
        val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        return readLinks(tag)
    }

    fun saveLinks(stack: ItemStack, links: List<ShopKey>) {
        val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        writeLinks(tag, links)
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag)
    }

    fun readLinks(tag: CompoundTag): List<ShopKey> {
        val list = tag.getList(SHOPS_TAG, CompoundTag.TAG_COMPOUND.toInt())
        return (0 until list.size).mapNotNull { index ->
            val entry = list.getCompound(index)
            val dimension = entry.getString(DIMENSION_TAG).takeIf(String::isNotBlank) ?: return@mapNotNull null
            ShopKey(dimension, BlockPos(entry.getInt(X_TAG), entry.getInt(Y_TAG), entry.getInt(Z_TAG)))
        }
    }

    fun writeLinks(tag: CompoundTag, links: List<ShopKey>) {
        val list = ListTag()
        links.distinct().forEach { key ->
            list.add(CompoundTag().also { entry ->
                entry.putString(DIMENSION_TAG, key.dimension)
                entry.putInt(X_TAG, key.pos.x)
                entry.putInt(Y_TAG, key.pos.y)
                entry.putInt(Z_TAG, key.pos.z)
            })
        }
        tag.put(SHOPS_TAG, list)
    }

    const val SHOPS_TAG = "VendorShops"
    private const val DIMENSION_TAG = "Dimension"
    private const val X_TAG = "X"
    private const val Y_TAG = "Y"
    private const val Z_TAG = "Z"
}

data class SellerState(
    val ownerId: UUID,
    val ownerName: String,
    val previousNoAi: Boolean,
    val anchorX: Double,
    val anchorY: Double,
    val anchorZ: Double,
    val links: List<ShopKey>,
)

object SellerData {
    fun isSeller(entity: Entity): Boolean = entity.persistentData.contains(SELLER_TAG, CompoundTag.TAG_COMPOUND.toInt())

    fun save(entity: Mob, owner: ServerPlayer, links: List<ShopKey>) {
        entity.persistentData.put(SELLER_TAG, CompoundTag().also { tag ->
            tag.putUUID(OWNER_ID_TAG, owner.uuid)
            tag.putString(OWNER_NAME_TAG, owner.gameProfile.name)
            tag.putBoolean(PREVIOUS_NO_AI_TAG, entity.isNoAi)
            tag.putDouble(ANCHOR_X_TAG, entity.x)
            tag.putDouble(ANCHOR_Y_TAG, entity.y)
            tag.putDouble(ANCHOR_Z_TAG, entity.z)
            VendorContractData.writeLinks(tag, links)
        })
    }

    fun read(entity: Entity): SellerState? {
        val tag = entity.persistentData.getCompound(SELLER_TAG)
        if (!tag.hasUUID(OWNER_ID_TAG)) return null
        return SellerState(
            tag.getUUID(OWNER_ID_TAG),
            tag.getString(OWNER_NAME_TAG),
            tag.getBoolean(PREVIOUS_NO_AI_TAG),
            tag.getDouble(ANCHOR_X_TAG),
            tag.getDouble(ANCHOR_Y_TAG),
            tag.getDouble(ANCHOR_Z_TAG),
            VendorContractData.readLinks(tag),
        )
    }

    fun clear(entity: Entity) {
        entity.persistentData.remove(SELLER_TAG)
    }

    private const val SELLER_TAG = "ChowkingdomVendorSeller"
    private const val OWNER_ID_TAG = "OwnerId"
    private const val OWNER_NAME_TAG = "OwnerName"
    private const val PREVIOUS_NO_AI_TAG = "PreviousNoAi"
    private const val ANCHOR_X_TAG = "AnchorX"
    private const val ANCHOR_Y_TAG = "AnchorY"
    private const val ANCHOR_Z_TAG = "AnchorZ"
}

data class VendorEntry(
    val dimension: String,
    val pos: BlockPos,
    val stack: ItemStack,
    val stockCount: Int,
    val price: Long,
    val ownerName: String,
)

data class VendorOpenPayload(val sellerId: UUID, val canVoid: Boolean, val entries: List<VendorEntry>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<VendorOpenPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<VendorOpenPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/vendor_open"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, VendorOpenPayload> = object : StreamCodec<RegistryFriendlyByteBuf, VendorOpenPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): VendorOpenPayload {
                val sellerId = buffer.readUUID()
                val canVoid = buffer.readBoolean()
                val entries = List(buffer.readVarInt().coerceIn(0, VendorContractConfig.maxLinks())) {
                    VendorEntry(
                        buffer.readUtf(128),
                        buffer.readBlockPos(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(),
                        buffer.readVarLong(),
                        buffer.readUtf(64),
                    )
                }
                return VendorOpenPayload(sellerId, canVoid, entries)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: VendorOpenPayload) {
                buffer.writeUUID(value.sellerId)
                buffer.writeBoolean(value.canVoid)
                buffer.writeVarInt(value.entries.size)
                value.entries.forEach { entry ->
                    buffer.writeUtf(entry.dimension, 128)
                    buffer.writeBlockPos(entry.pos)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack.copyWithCount(1))
                    buffer.writeVarInt(entry.stockCount)
                    buffer.writeVarLong(entry.price)
                    buffer.writeUtf(entry.ownerName, 64)
                }
            }
        }
    }
}

data class VendorBuyPayload(val sellerId: UUID, val dimension: String, val pos: BlockPos, val quantity: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<VendorBuyPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<VendorBuyPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/vendor_buy"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, VendorBuyPayload> = object : StreamCodec<RegistryFriendlyByteBuf, VendorBuyPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): VendorBuyPayload =
                VendorBuyPayload(buffer.readUUID(), buffer.readUtf(128), buffer.readBlockPos(), buffer.readVarInt())

            override fun encode(buffer: RegistryFriendlyByteBuf, value: VendorBuyPayload) {
                buffer.writeUUID(value.sellerId)
                buffer.writeUtf(value.dimension, 128)
                buffer.writeBlockPos(value.pos)
                buffer.writeVarInt(value.quantity)
            }
        }
    }
}

data class VendorVoidPayload(val sellerId: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<VendorVoidPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<VendorVoidPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/vendor_void"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, VendorVoidPayload> = object : StreamCodec<RegistryFriendlyByteBuf, VendorVoidPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): VendorVoidPayload = VendorVoidPayload(buffer.readUUID())
            override fun encode(buffer: RegistryFriendlyByteBuf, value: VendorVoidPayload) {
                buffer.writeUUID(value.sellerId)
            }
        }
    }
}

data class VendorContractSelectionPayload(val positions: List<BlockPos>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<VendorContractSelectionPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<VendorContractSelectionPayload> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "shops/vendor_contract_selection"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, VendorContractSelectionPayload> = object : StreamCodec<RegistryFriendlyByteBuf, VendorContractSelectionPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): VendorContractSelectionPayload =
                VendorContractSelectionPayload(List(buffer.readVarInt().coerceIn(0, VendorContractConfig.maxLinks())) { buffer.readBlockPos() })

            override fun encode(buffer: RegistryFriendlyByteBuf, value: VendorContractSelectionPayload) {
                buffer.writeVarInt(value.positions.size)
                value.positions.forEach(buffer::writeBlockPos)
            }
        }
    }
}
