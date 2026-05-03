package dev.gisketch.chowkingdom.revive

import java.util.UUID

object ReviveClientState {
    private var selfState: SelfReviveState? = null
    private val progressByTarget: MutableMap<UUID, ReviveProgress> = linkedMapOf()

    fun applySelf(payload: ReviveSelfStatePayload) {
        selfState = if (payload.active) SelfReviveState(payload.title, payload.expiresAtMs) else null
        ReviveClient.onSelfStateChanged(payload.active)
    }

    fun selfState(): SelfReviveState? = selfState

    fun applyProgress(payload: ReviveProgressPayload) {
        if (payload.active) {
            progressByTarget[payload.targetId] = ReviveProgress(payload.reviverId, payload.targetId, payload.targetEntityId, payload.targetName, payload.expiresAtMs)
        } else {
            progressByTarget.remove(payload.targetId)
        }
    }

    fun progressForReviver(reviverId: UUID): ReviveProgress? {
        val progress = progressByTarget.values.firstOrNull { it.reviverId == reviverId } ?: return null
        if (System.currentTimeMillis() > progress.expiresAtMs + EXPIRED_PROGRESS_GRACE_MS) {
            progressByTarget.remove(progress.targetId)
            return null
        }
        return progress
    }

    fun clearProgress() {
        progressByTarget.clear()
    }

    fun clearAll() {
        selfState = null
        progressByTarget.clear()
    }

    private const val EXPIRED_PROGRESS_GRACE_MS = 1500L
}

data class SelfReviveState(val title: String, val expiresAtMs: Long)

data class ReviveProgress(val reviverId: UUID, val targetId: UUID, val targetEntityId: Int, val targetName: String, val expiresAtMs: Long)
