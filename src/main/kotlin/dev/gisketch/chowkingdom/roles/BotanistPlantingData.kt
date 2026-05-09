package dev.gisketch.chowkingdom.roles

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData

class BotanistPlantingData : SavedData() {
    private val cropChances: MutableMap<String, Double> = linkedMapOf()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        cropChances.forEach { (key, chance) ->
            list.add(CompoundTag().also { entry ->
                entry.putString("Key", key)
                entry.putDouble("Chance", chance)
            })
        }
        tag.put("Crops", list)
        return tag
    }

    fun mark(level: ServerLevel, pos: BlockPos, chance: Double) {
        cropChances[key(level, pos)] = chance.coerceIn(0.0, 1.0)
        setDirty()
    }

    fun remove(level: ServerLevel, pos: BlockPos) {
        if (cropChances.remove(key(level, pos)) != null) setDirty()
    }

    fun growthChance(level: ServerLevel, pos: BlockPos): Double = cropChances[key(level, pos)] ?: 0.0

    companion object {
        private const val DATA_ID = "chowkingdom_botanist_planting"
        private val FACTORY = Factory(::BotanistPlantingData, ::load, DataFixTypes.LEVEL)

        fun get(server: MinecraftServer): BotanistPlantingData = server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_ID)

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): BotanistPlantingData = BotanistPlantingData().also { data ->
            val list = tag.getList("Crops", CompoundTag.TAG_COMPOUND.toInt())
            (0 until list.size).forEach { index ->
                val entry = list.getCompound(index)
                val key = entry.getString("Key")
                if (key.isNotBlank()) data.cropChances[key] = entry.getDouble("Chance").coerceIn(0.0, 1.0)
            }
        }

        private fun key(level: ServerLevel, pos: BlockPos): String = "${level.dimension().location()}|${pos.x}|${pos.y}|${pos.z}"
    }
}
