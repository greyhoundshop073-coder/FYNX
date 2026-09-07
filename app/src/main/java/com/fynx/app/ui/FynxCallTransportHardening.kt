package com.fynx.app.ui

/** Small call-transport guards shared by realtime/call screens. */
object FynxCallTransportHardening {
    fun shouldRetrySocket(closeCode: Int): Boolean = closeCode != 1000 && closeCode != 1008 && closeCode != 1003
    fun isAuthFailure(httpCode: Int?): Boolean = httpCode == 401 || httpCode == 403
}
