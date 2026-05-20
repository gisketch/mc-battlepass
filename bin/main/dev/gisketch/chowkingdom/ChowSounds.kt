package dev.gisketch.chowkingdom

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ChowSounds {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> = DeferredRegister.create(Registries.SOUND_EVENT, ChowKingdomMod.MOD_ID)

    val PARRY: DeferredHolder<SoundEvent, SoundEvent> = SOUND_EVENTS.register("parry", Supplier {
        SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ChowKingdomMod.MOD_ID, "parry"))
    })

    fun register(modBus: IEventBus) {
        SOUND_EVENTS.register(modBus)
    }
}
