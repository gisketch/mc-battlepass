package dev.gisketch.chowkingdom.cosmetics

import dev.gisketch.chowkingdom.ChowKingdomMod
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Ingredient
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.EnumMap
import java.util.function.Supplier

object PokeClothingCosmeticsFeature {
    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ChowKingdomMod.MOD_ID)
    val REGISTERED_ITEMS: List<DeferredHolder<Item, ArmorItem>>

    init {
        REGISTERED_ITEMS = POKE_CLOTHING_PIECES.map { piece ->
            ITEMS.register(piece.itemId, Supplier { ArmorItem(material(piece.setId), piece.type, Item.Properties().stacksTo(1)) })
        }
    }

    fun register(modBus: IEventBus) {
        ITEMS.register(modBus)
    }

    private fun material(setId: String): Holder<ArmorMaterial> {
        val layer = ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, setId))
        return Holder.direct(
            ArmorMaterial(
                EnumMap(ArmorItem.Type::class.java),
                0,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                Supplier { Ingredient.EMPTY },
                listOf(layer),
                0.0f,
                0.0f,
            ),
        )
    }
}

private data class PokeClothingPiece(val setId: String, val itemId: String, val type: ArmorItem.Type)

private val POKE_CLOTHING_PIECES = listOf(
    PokeClothingPiece("alola_ash", "alola_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("alola_ash", "alola_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("alola_ash", "alola_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("alola_ash", "alola_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("brendan", "brendan_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("brendan", "brendan_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("brendan", "brendan_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("brendan", "brendan_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("brock", "brock_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("brock", "brock_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("brock", "brock_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("dawn", "dawn_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("dawn", "dawn_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("dawn", "dawn_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("dawn", "dawn_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("emerald_brendan", "emerald_brendan_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("emerald_brendan", "emerald_brendan_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("emerald_brendan", "emerald_brendan_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("emerald_brendan", "emerald_brendan_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("emerald_may", "emerald_may_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("emerald_may", "emerald_may_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("emerald_may", "emerald_may_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("emerald_may", "emerald_may_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("galar_ash", "galar_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("galar_ash", "galar_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("galar_ash", "galar_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("galar_ash", "galar_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("hoenn_ash", "hoenn_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("hoenn_ash", "hoenn_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("hoenn_ash", "hoenn_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("hoenn_ash", "hoenn_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("james", "james_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("james", "james_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("james", "james_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("jessie", "jessie_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("jessie", "jessie_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("jessie", "jessie_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("kalos_ash", "kalos_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("kalos_ash", "kalos_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("kalos_ash", "kalos_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("kalos_ash", "kalos_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("kanto_ash", "kanto_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("kanto_ash", "kanto_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("kanto_ash", "kanto_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("kanto_ash", "kanto_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("may", "may_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("may", "may_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("may", "may_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("may", "may_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("misty", "misty_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("misty", "misty_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("misty", "misty_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("platinum_dawn", "platinum_dawn_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("platinum_dawn", "platinum_dawn_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("platinum_dawn", "platinum_dawn_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("red", "red_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("red", "red_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("red", "red_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("red", "red_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("sinnoh_ash", "sinnoh_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("sinnoh_ash", "sinnoh_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("sinnoh_ash", "sinnoh_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("sinnoh_ash", "sinnoh_ash_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("team_aqua_grunt", "team_aqua_grunt_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("team_aqua_grunt", "team_aqua_grunt_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("team_aqua_grunt", "team_aqua_grunt_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("team_aqua_grunt", "team_aqua_grunt_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("team_magma_grunt", "team_magma_grunt_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("team_magma_grunt", "team_magma_grunt_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("team_magma_grunt", "team_magma_grunt_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("team_magma_grunt", "team_magma_grunt_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("team_rocket_grunt", "team_rocket_grunt_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("team_rocket_grunt", "team_rocket_grunt_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("team_rocket_grunt", "team_rocket_grunt_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("team_rocket_grunt", "team_rocket_grunt_boots", ArmorItem.Type.BOOTS),
    PokeClothingPiece("unova_ash", "unova_ash_helmet", ArmorItem.Type.HELMET),
    PokeClothingPiece("unova_ash", "unova_ash_chestplate", ArmorItem.Type.CHESTPLATE),
    PokeClothingPiece("unova_ash", "unova_ash_leggings", ArmorItem.Type.LEGGINGS),
    PokeClothingPiece("unova_ash", "unova_ash_boots", ArmorItem.Type.BOOTS),
)
