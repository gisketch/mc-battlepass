package dev.gisketch.chowkingdom.npc

interface NpcJob {
    val id: String
    fun tick(entity: ChowNpcEntity, definition: NpcDefinition)
}

object NpcJobs {
    private val jobs: MutableMap<String, NpcJob> = linkedMapOf()

    init {
        register(AdventurerNpcJob)
        register("warrior", AdventurerNpcJob)
    }

    fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        jobs[definition.job]?.tick(entity, definition) ?: AdventurerNpcJob.tick(entity, definition)
    }

    private fun register(job: NpcJob) {
        jobs[job.id] = job
    }

    private fun register(id: String, job: NpcJob) {
        jobs[id] = job
    }
}

object NpcBrain {
    fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        val activity = definition.schedule.activityAt(entity.level().dayTime)
        entity.debugActivity = activity
        if (activity != "sleep" && entity.isSleeping) entity.stopSleeping()
        if (entity.tickCount % definition.jobDefinition.scanIntervalTicks != 0 || !entity.navigation.isDone) return
        NpcFeature.moveToActivityTarget(entity, definition, activity)
    }
}

private object AdventurerNpcJob : NpcJob {
    override val id: String = "adventurer"

    override fun tick(entity: ChowNpcEntity, definition: NpcDefinition) {
        if (entity.tickCount % 60 != 0 || !entity.navigation.isDone) return
        NpcFeature.moveToActivityTarget(entity, definition, "work")
    }
}
