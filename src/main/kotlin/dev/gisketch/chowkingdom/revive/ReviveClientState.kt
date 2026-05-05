package dev.gisketch.chowkingdom.revive

import java.util.UUID

object ReviveClientState {
    private var selfState: SelfReviveState? = null
    private val progressByTarget: MutableMap<UUID, ReviveProgress> = linkedMapOf()
    private var completeNotice: ReviveCompleteNotice? = null

    fun applySelf(payload: ReviveSelfStatePayload) {
        selfState = if (payload.active) SelfReviveState(payload.title, deadlineFromRemaining(payload.remainingMs)) else null
        if (payload.active) completeNotice = null
        ReviveClient.onSelfStateChanged(payload.active)
    }

    fun selfState(): SelfReviveState? = selfState

    fun applyProgress(payload: ReviveProgressPayload) {
        if (payload.active) {
            progressByTarget[payload.targetId] = ReviveProgress(payload.reviverIds, payload.reviverNames, payload.targetId, payload.targetEntityId, payload.targetName, deadlineFromRemaining(payload.remainingMs))
        } else {
            progressByTarget.remove(payload.targetId)
        }
    }

    fun applyComplete(payload: ReviveCompletePayload) {
        completeNotice = ReviveCompleteNotice(payload.reviverIds, payload.reviverNames, System.currentTimeMillis(), System.currentTimeMillis() + COMPLETE_NOTICE_MS)
    }

    fun progressForReviver(reviverId: UUID): ReviveProgress? {
        val progress = progressByTarget.values.firstOrNull { reviverId in it.reviverIds } ?: return null
        return activeProgress(progress)
    }

    fun progressForTarget(targetId: UUID): ReviveProgress? {
        val progress = progressByTarget[targetId] ?: return null
        return activeProgress(progress)
    }

    fun completeNotice(): ReviveCompleteNotice? {
        val notice = completeNotice ?: return null
        if (System.currentTimeMillis() > notice.expiresAtMs) {
            completeNotice = null
            return null
        }
        return notice
    }

    fun clearProgress() {
        progressByTarget.clear()
    }

    fun clearAll() {
        selfState = null
        progressByTarget.clear()
        completeNotice = null
    }

    private fun activeProgress(progress: ReviveProgress): ReviveProgress? {
        if (System.currentTimeMillis() <= progress.expiresAtMs + EXPIRED_PROGRESS_GRACE_MS) return progress
        progressByTarget.remove(progress.targetId)
        return null
    }

    private fun deadlineFromRemaining(remainingMs: Long): Long =
        System.currentTimeMillis() + remainingMs.coerceAtLeast(0L)

    private const val EXPIRED_PROGRESS_GRACE_MS = 1500L
    private const val COMPLETE_NOTICE_MS = 5000L
}

data class SelfReviveState(val title: String, val expiresAtMs: Long)

data class ReviveProgress(val reviverIds: List<UUID>, val reviverNames: List<String>, val targetId: UUID, val targetEntityId: Int, val targetName: String, val expiresAtMs: Long)

data class ReviveCompleteNotice(val reviverIds: List<UUID>, val reviverNames: List<String>, val startedAtMs: Long, val expiresAtMs: Long)
