package dev.gisketch.chowkingdom.shipping

object ShippingBinClientState {
    data class SaleNotification(val itemCount: Int, val amount: Long)

    private val pendingSales: MutableList<SaleNotification> = mutableListOf()

    fun enqueueSale(itemCount: Int, amount: Long) {
        if (itemCount <= 0 || amount <= 0L) return
        pendingSales += SaleNotification(itemCount, amount)
    }

    fun drainSaleNotifications(): List<SaleNotification> = pendingSales.toList().also { pendingSales.clear() }
}