// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.fencing.spacedrepetition.billing.BillingManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DonationViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager(application)

    val billingState: StateFlow<BillingManager.BillingState> =
        billingManager.billingState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BillingManager.BillingState.Disconnected
        )

    val donationProducts: StateFlow<List<ProductDetails>> =
        billingManager.donationProducts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val purchaseResult: StateFlow<BillingManager.PurchaseResult?> =
        billingManager.purchaseResult.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        initializeBilling()
    }

    private fun initializeBilling() {
        viewModelScope.launch {
            billingManager.initialize()
        }
    }

    fun launchDonationFlow(activity: Activity, productDetails: ProductDetails) {
        billingManager.launchDonationFlow(activity, productDetails)
    }

    fun clearPurchaseResult() {
        billingManager.clearPurchaseResult()
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.disconnect()
    }
}
