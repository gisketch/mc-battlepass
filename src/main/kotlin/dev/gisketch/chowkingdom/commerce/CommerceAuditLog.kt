package dev.gisketch.chowkingdom.commerce

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class CommerceAuditLog : SavedData() {
    private var nextId = 1L
    private val entries: MutableList<CompoundTag> = mutableListOf()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putLong("NextId", nextId)
        val list = ListTag()
        entries.forEach(list::add)
        tag.put("Entries", list)
        return tag
    }

    private fun append(server: MinecraftServer, entry: CompoundTag) {
        entry.putLong("Id", nextId++)
        entry.putLong("GameTime", server.overworld().gameTime)
        entries += entry
        setDirty()
    }

    companion object {
        private const val DATA_ID = "chowkingdom_commerce_audit"

        private val FACTORY = Factory(::CommerceAuditLog, ::load, DataFixTypes.LEVEL)

        fun recordShopBuy(
            buyer: ServerPlayer,
            sellerId: UUID,
            sellerName: String,
            pos: BlockPos,
            itemName: String,
            quantity: Int,
            total: Long,
        ) {
            get(buyer.server).append(
                buyer.server,
                base("shop_buy").also { tag ->
                    putPlayer(tag, "Buyer", buyer)
                    putIdentity(tag, "Seller", sellerId, sellerName)
                    putBlock(tag, buyer.level().dimension().location().toString(), pos)
                    tag.putString("ItemName", itemName)
                    tag.putInt("Quantity", quantity)
                    tag.putLong("TotalChowcoins", total)
                },
            )
        }

        fun recordVendorBuy(
            buyer: ServerPlayer,
            sellerId: UUID,
            sellerName: String,
            vendor: Entity,
            shopDimension: String,
            shopPos: BlockPos,
            itemName: String,
            quantity: Int,
            total: Long,
        ) {
            get(buyer.server).append(
                buyer.server,
                base("vendor_buy").also { tag ->
                    putPlayer(tag, "Buyer", buyer)
                    putIdentity(tag, "Seller", sellerId, sellerName)
                    tag.putUUID("VendorEntityId", vendor.uuid)
                    tag.putString("VendorEntityType", BuiltInRegistries.ENTITY_TYPE.getKey(vendor.type).toString())
                    putBlock(tag, shopDimension, shopPos)
                    tag.putString("ItemName", itemName)
                    tag.putInt("Quantity", quantity)
                    tag.putLong("TotalChowcoins", total)
                },
            )
        }

        fun recordTrade(
            server: MinecraftServer,
            firstId: UUID,
            firstName: String,
            secondId: UUID,
            secondName: String,
            firstOfferedItems: List<ItemStack>,
            secondOfferedItems: List<ItemStack>,
            firstChowcoins: Long,
            secondChowcoins: Long,
            debug: Boolean,
        ) {
            get(server).append(
                server,
                base(if (debug) "debug_trade" else "trade").also { tag ->
                    putIdentity(tag, "First", firstId, firstName)
                    putIdentity(tag, "Second", secondId, secondName)
                    tag.put("FirstItems", itemList(firstOfferedItems))
                    tag.put("SecondItems", itemList(secondOfferedItems))
                    tag.putLong("FirstChowcoins", firstChowcoins)
                    tag.putLong("SecondChowcoins", secondChowcoins)
                },
            )
        }

        private fun get(server: MinecraftServer): CommerceAuditLog =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_ID)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): CommerceAuditLog =
            CommerceAuditLog().also { data ->
                data.nextId = tag.getLong("NextId").coerceAtLeast(1L)
                val list = tag.getList("Entries", CompoundTag.TAG_COMPOUND.toInt())
                (0 until list.size).forEach { index -> data.entries += list.getCompound(index) }
            }

        private fun base(type: String): CompoundTag =
            CompoundTag().also { tag ->
                tag.putString("Type", type)
                tag.putLong("UnixTimeMillis", System.currentTimeMillis())
            }

        private fun putPlayer(tag: CompoundTag, prefix: String, player: ServerPlayer) =
            putIdentity(tag, prefix, player.uuid, player.gameProfile.name)

        private fun putIdentity(tag: CompoundTag, prefix: String, id: UUID, name: String) {
            tag.putUUID("${prefix}Id", id)
            tag.putString("${prefix}Name", name)
        }

        private fun putBlock(tag: CompoundTag, dimension: String, pos: BlockPos) {
            tag.putString("Dimension", dimension)
            tag.putInt("X", pos.x)
            tag.putInt("Y", pos.y)
            tag.putInt("Z", pos.z)
        }

        private fun itemList(stacks: List<ItemStack>): ListTag =
            ListTag().also { list ->
                stacks.filterNot(ItemStack::isEmpty).forEach { stack ->
                    list.add(
                        CompoundTag().also { tag ->
                            tag.putString("Item", BuiltInRegistries.ITEM.getKey(stack.item).toString())
                            tag.putString("Name", stack.hoverName.string)
                            tag.putInt("Count", stack.count)
                        },
                    )
                }
            }
    }
}
