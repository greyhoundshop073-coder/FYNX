package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxBuyerWalletCurrencyPresentationTest {
    @Test
    fun walletBalanceAndTransactionAmountsUseTheirRecordedCurrency() {
        assertEquals("₦1,234.50", fynxWalletCurrencyMoney(1_234.5, "NGN"))
        assertEquals("\$1,234.50", fynxWalletCurrencyMoney(1_234.5, "USD"))
    }

    @Test
    fun walletCurrencyHeadingsMatchTheReturnedCurrency() {
        assertEquals("Nigerian Naira", fynxWalletCurrencyLabel("NGN"))
        assertEquals("US Dollar", fynxWalletCurrencyLabel("USD"))
        assertEquals("₦", fynxWalletCurrencySymbol("NGN"))
        assertEquals("\$", fynxWalletCurrencySymbol("USD"))
    }
}
