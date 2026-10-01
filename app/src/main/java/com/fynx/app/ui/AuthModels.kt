package com.fynx.app.ui

enum class AuthState { SIGNED_OUT, SIGNED_IN }

data class AuthSession(
    val state: AuthState = AuthState.SIGNED_OUT,
    val username: String? = null
)
