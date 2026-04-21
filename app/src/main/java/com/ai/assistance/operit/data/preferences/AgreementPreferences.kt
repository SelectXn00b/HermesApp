package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages agreement-related preferences for the application */
class AgreementPreferences(context: Context) {
    private val PREFS_NAME = "agreement_preferences"
    private val KEY_AGREEMENT_ACCEPTED = "agreement_accepted"

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _agreementAcceptedFlow = MutableStateFlow(isAgreementAccepted())
    val agreementAcceptedFlow: StateFlow<Boolean> = _agreementAcceptedFlow.asStateFlow()

    /** Check if the user has already accepted the agreement */
    fun isAgreementAccepted(): Boolean {
        return prefs.getBoolean(KEY_AGREEMENT_ACCEPTED, false)
    }

    /** Set the agreement as accepted and update the flow */
    fun setAgreementAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_AGREEMENT_ACCEPTED, accepted).apply()
        _agreementAcceptedFlow.value = accepted
    }
}
