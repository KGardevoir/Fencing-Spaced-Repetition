package com.fencing.spacedrepetition.ui.screen

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fencing.spacedrepetition.billing.BillingManager
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.ui.viewmodel.DonationViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    donationViewModel: DonationViewModel = viewModel()
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
    val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
    val randomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
    val maximumInterval by settingsViewModel.maximumInterval.collectAsState()
    val practicesPerWeek by settingsViewModel.practicesPerWeek.collectAsState()

    // Donation state
    val context = LocalContext.current
    val activity = context as? Activity
    val billingState by donationViewModel.billingState.collectAsState()
    val donationProducts by donationViewModel.donationProducts.collectAsState()
    val purchaseResult by donationViewModel.purchaseResult.collectAsState()

    // Show success/error messages
    LaunchedEffect(purchaseResult) {
        purchaseResult?.let { result ->
            when (result) {
                is BillingManager.PurchaseResult.Success -> {
                    // Could show a snackbar here
                }
                is BillingManager.PurchaseResult.Cancelled -> {
                    // User cancelled
                }
                is BillingManager.PurchaseResult.Error -> {
                    // Could show error snackbar
                }
            }
        }
    }

    // Presets for randomization bucket size
    val bucketPresets = listOf(
        24 to "1 day",
        72 to "3 days",
        168 to "1 week",
        336 to "2 weeks",
        672 to "4 weeks"
    )
    val currentBucketIndex = bucketPresets.indexOfFirst { it.first >= randomizeBucketHours }
        .let { if (it == -1) bucketPresets.size - 1 else it }

    // Preset values for maximum interval with better granularity
    val intervalPresets = listOf(
        7 to "1 week",
        14 to "2 weeks",
        30 to "1 month",
        60 to "2 months",
        90 to "3 months",
        180 to "6 months",
        365 to "1 year",
        730 to "2 years",
        1825 to "5 years",
        3650 to "10 years"
    )

    // Find closest preset index to current value
    val currentPresetIndex = intervalPresets.indexOfFirst { it.first >= maximumInterval }
        .let { if (it == -1) intervalPresets.size - 1 else it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Theme section
            Text(
                "Theme",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ThemeMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { settingsViewModel.setThemeMode(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { settingsViewModel.setThemeMode(mode) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "System Default"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "Follow device settings"
                                ThemeMode.LIGHT -> "Always use light theme"
                                ThemeMode.DARK -> "Always use dark theme"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Practice section
            Text(
                "Practice",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Cards per session
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cards per Session",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "$cardsPerSession",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = cardsPerSession.toFloat(),
                    onValueChange = { settingsViewModel.setCardsPerSession(it.toInt()) },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Number of cards to practice each session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-show answer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setAutoShowAnswer(!autoShowAnswer) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-show Description",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Show description immediately when viewing cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoShowAnswer,
                    onCheckedChange = { settingsViewModel.setAutoShowAnswer(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Randomize due cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setRandomizeDueCards(!randomizeDueCards) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Randomize Due Cards",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Shuffle cards with similar due dates for variety",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = randomizeDueCards,
                    onCheckedChange = { settingsViewModel.setRandomizeDueCards(it) }
                )
            }

            // Randomization bucket size (only shown when randomization is enabled)
            if (randomizeDueCards) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sampling Bucket Size",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = bucketPresets[currentBucketIndex].second,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentBucketIndex.toFloat(),
                        onValueChange = { newIndex ->
                            val presetValue = bucketPresets[newIndex.toInt()].first
                            settingsViewModel.setRandomizeBucketHours(presetValue)
                        },
                        valueRange = 0f..(bucketPresets.size - 1).toFloat(),
                        steps = bucketPresets.size - 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Cards due within the same bucket are shuffled randomly. Smaller buckets preserve due-date ordering more strictly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Scheduling section
            Text(
                "Scheduling",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Practices per week
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Practices per Week",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (practicesPerWeek == 7) "Daily" else "$practicesPerWeek",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = practicesPerWeek.toFloat(),
                    onValueChange = { settingsViewModel.setPracticesPerWeek(it.toInt()) },
                    valueRange = 1f..7f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Adjusts review spacing to match your practice frequency. Cards are scheduled to come due on days you practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Algorithm section
            Text(
                "Algorithm",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Maximum interval
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Maximum Interval",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = intervalPresets[currentPresetIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentPresetIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = intervalPresets[newIndex.toInt()].first
                        settingsViewModel.setMaximumInterval(presetValue)
                    },
                    valueRange = 0f..(intervalPresets.size - 1).toFloat(),
                    steps = intervalPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Maximum time between reviews",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Support section
            Text(
                "Support Development",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "If you find this app helpful, consider supporting its development with a donation. Your support helps keep the app free and ad-free!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Show donation buttons based on billing state
            when (billingState) {
                is BillingManager.BillingState.Connected -> {
                    if (donationProducts.isEmpty()) {
                        Text(
                            text = "Loading donation options...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        donationProducts.forEach { product ->
                            val oneTimePriceOffer = product.oneTimePurchaseOfferDetails
                            val price = oneTimePriceOffer?.formattedPrice ?: "N/A"

                            val displayName = when (product.productId) {
                                BillingManager.DONATION_SMALL -> "Small Coffee"
                                BillingManager.DONATION_MEDIUM -> "Big Coffee"
                                BillingManager.DONATION_LARGE -> "Generous Support"
                                else -> product.name
                            }

                            val description = when (product.productId) {
                                BillingManager.DONATION_SMALL -> "Buy me a small coffee"
                                BillingManager.DONATION_MEDIUM -> "Buy me a big coffee"
                                BillingManager.DONATION_LARGE -> "Support development generously"
                                else -> product.description
                            }

                            ElevatedButton(
                                onClick = {
                                    activity?.let {
                                        donationViewModel.launchDonationFlow(it, product)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                enabled = activity != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(displayName)
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    price,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Show purchase result
                    purchaseResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        when (result) {
                            is BillingManager.PurchaseResult.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Thank you for your support!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(3000)
                                    donationViewModel.clearPurchaseResult()
                                }
                            }
                            is BillingManager.PurchaseResult.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Error: ${result.message}",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            is BillingManager.PurchaseResult.Cancelled -> {
                                // Don't show anything for cancelled
                                LaunchedEffect(Unit) {
                                    donationViewModel.clearPurchaseResult()
                                }
                            }
                        }
                    }
                }
                is BillingManager.BillingState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connecting to payment service...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is BillingManager.BillingState.Error -> {
                    Text(
                        text = "Payment service unavailable. Please try again later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is BillingManager.BillingState.Disconnected -> {
                    Text(
                        text = "Connecting to payment service...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
