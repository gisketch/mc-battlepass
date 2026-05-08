package dev.gisketch.chowkingdom.shipping

object ShippingBinClientState {
    data class SaleNotification(val itemCount: Int, val amount: Long)

    private val pendingSales: MutableList<SaleNotification> = mutableListOf()
    private var access: ShippingBinAccess = ShippingBinAccess()
    private var weeklyQuota: Int = ShippingBinRules.WEEKLY_ITEM_QUOTA
    private var periodKey: String = ""
    private val weeklyCounts: MutableMap<String, Int> = linkedMapOf()

    fun enqueueSale(itemCount: Int, amount: Long) {
        if (itemCount <= 0 || amount <= 0L) return
        pendingSales += SaleNotification(itemCount, amount)
    }

    fun drainSaleNotifications(): List<SaleNotification> = pendingSales.toList().also { pendingSales.clear() }

    fun updateState(level: Int, unlockedSlots: Int, maxStackSize: Int, quota: Int, period: String, counts: Map<String, Int>) {
        access = ShippingBinAccess(level.coerceAtLeast(1), unlockedSlots.coerceIn(1, ShippingBinRules.MAX_UNLOCKED_SLOTS), maxStackSize.coerceIn(1, 64))
        weeklyQuota = quota.coerceAtLeast(1)
        periodKey = period
        weeklyCounts.clear()
        weeklyCounts.putAll(counts.filterValues { count -> count > 0 })
    }

    fun access(): ShippingBinAccess = access

    fun weeklyQuota(): Int = weeklyQuota

    fun periodKey(): String = periodKey

    fun quotaUsed(itemKey: String): Int = weeklyCounts[itemKey] ?: 0
}