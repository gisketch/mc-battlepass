package dev.gisketch.chowkingdom.wallets

object ChowcoinClientState {
    private var balance = 0L

    fun apply(payload: ChowcoinSyncPayload) {
        balance = payload.balance.coerceAtLeast(0L)
    }

    fun balance(): Long = balance
}