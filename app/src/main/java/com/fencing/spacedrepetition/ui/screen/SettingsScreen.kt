package com.fencing.spacedrepetition.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fencing.spacedrepetition.billing.BillingManager
import com.fencing.spacedrepetition.BuildConfig
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.RetentionSelector
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.DonationViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    cardViewModel: CardViewModel,
    onNavigateBack: () -> Unit,
    donationViewModel: DonationViewModel = viewModel()
) {
    val totalCards by cardViewModel.cardCount.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val themeMode by settingsViewModel.themeMode.collectAsState()
    val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
    val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
    val randomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
    val maximumInterval by settingsViewModel.maximumInterval.collectAsState()
    val practiceDays by settingsViewModel.practiceDays.collectAsState()
    val fsrsRetention by settingsViewModel.fsrsRetention.collectAsState()
    val practiceScheduleEstimate by cardViewModel.practiceScheduleEstimate.collectAsState()
    val sm2IntervalModifier by settingsViewModel.sm2IntervalModifier.collectAsState()
    val fsrsEnableFuzzing by settingsViewModel.fsrsEnableFuzzing.collectAsState()
    val autoBackupEnabled by settingsViewModel.autoBackupEnabled.collectAsState()
    val autoBackupUri by settingsViewModel.autoBackupUri.collectAsState()
    val autoBackupIntervalDays by settingsViewModel.autoBackupIntervalDays.collectAsState()
    val lastBackupTime by settingsViewModel.lastBackupTime.collectAsState()
    val maxBackupsKept by settingsViewModel.maxBackupsKept.collectAsState()

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

    val bucketPresets = SettingsConstants.BUCKET_PRESETS
    val currentBucketIndex = SettingsConstants.findPresetIndex(bucketPresets, randomizeBucketHours)

    val intervalPresets = SettingsConstants.INTERVAL_PRESETS
    val currentPresetIndex = SettingsConstants.findPresetIndex(intervalPresets, maximumInterval)

    val sm2ModifierPresets = SettingsConstants.SM2_MODIFIER_PRESETS
    val currentSm2ModifierIndex = SettingsConstants.findPresetIndex(sm2ModifierPresets, sm2IntervalModifier)

    val backupIntervalPresets = SettingsConstants.BACKUP_INTERVAL_PRESETS
    val currentBackupIntervalIndex = SettingsConstants.findPresetIndex(backupIntervalPresets, autoBackupIntervalDays)

    val maxBackupsKeptPresets = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS
    val currentMaxBackupsKeptIndex = SettingsConstants.findPresetIndex(maxBackupsKeptPresets, maxBackupsKept)

    val backupFolderName = autoBackupUri?.let { uriString ->
        DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsViewModel.setAutoBackupUri(uri.toString())
        }
    }

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
                    onValueChange = { settingsViewModel.setCardsPerSession(it.roundToInt()) },
                    valueRange = SettingsConstants.CARDS_PER_SESSION_MIN..SettingsConstants.CARDS_PER_SESSION_MAX,
                    steps = SettingsConstants.CARDS_PER_SESSION_STEPS,
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
                            val presetValue = bucketPresets[newIndex.roundToInt()].first
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

            // Practice days
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Practice Days",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SettingsConstants.DAY_LABELS.forEach { (day, label) ->
                        val selected = practiceDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = { settingsViewModel.togglePracticeDay(day) },
                            label = { Text(label) },
                            modifier = Modifier.size(width = 42.dp, height = 36.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (practiceDays.size == 7) "Daily" else "${practiceDays.size} days per week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cards are scheduled to come due on days you practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Algorithm section
            Text(
                "Algorithm (FSRS-6 / SM-2)",
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
                        val presetValue = intervalPresets[newIndex.roundToInt()].first
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

            Spacer(modifier = Modifier.height(8.dp))

            // FSRS desired retention
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "FSRS Desired Retention",
                    style = MaterialTheme.typography.bodyLarge
                )
                RetentionSelector(
                    retentionPercent = fsrsRetention,
                    onRetentionChange = { settingsViewModel.setFsrsRetention(it) },
                    cardsInRotation = totalCards,
                    scheduleDaysPerWeek = practiceDays.size,
                    scheduleSetsPerPractice = cardsPerSession,
                    historyEstimate = practiceScheduleEstimate
                )
                Text(
                    text = "Target probability of remembering a card when it comes due (FSRS only).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FsrsRetentionPreview(retentionPercent = fsrsRetention)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FSRS interval fuzzing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setFsrsEnableFuzzing(!fsrsEnableFuzzing) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FSRS Interval Fuzzing",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Add a small random variance (\u22645\u00a0%) to FSRS intervals to spread reviews and prevent pile-ups (FSRS only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fsrsEnableFuzzing,
                    onCheckedChange = { settingsViewModel.setFsrsEnableFuzzing(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SM-2 interval modifier
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
                        text = "SM-2 Interval Modifier",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = sm2ModifierPresets[currentSm2ModifierIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentSm2ModifierIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = sm2ModifierPresets[newIndex.roundToInt()].first
                        settingsViewModel.setSm2IntervalModifier(presetValue)
                    },
                    valueRange = 0f..(sm2ModifierPresets.size - 1).toFloat(),
                    steps = sm2ModifierPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Scales all SM-2 review intervals (SM-2 only). " +
                        "100\u00a0% = default behaviour. " +
                        "Lower values (e.g.\u00a075\u00a0%) mean shorter intervals and more reviews; " +
                        "higher values (e.g.\u00a0150\u00a0%) mean longer intervals and fewer reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Backup section
            Text(
                "Automatic Backup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setAutoBackupEnabled(!autoBackupEnabled) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Back Up Automatically",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Periodically save a compressed copy of your cards, groups, and history to a folder you choose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { settingsViewModel.setAutoBackupEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (backupFolderName != null) "Change Backup Folder" else "Choose Backup Folder")
            }
            if (backupFolderName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Folder: $backupFolderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Backup frequency
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
                        text = "Backup Frequency",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = backupIntervalPresets[currentBackupIntervalIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentBackupIntervalIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = backupIntervalPresets[newIndex.roundToInt()].first
                        settingsViewModel.setAutoBackupIntervalDays(presetValue)
                    },
                    valueRange = 0f..(backupIntervalPresets.size - 1).toFloat(),
                    steps = backupIntervalPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Max backups kept
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
                        text = "Backups to Keep",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = maxBackupsKeptPresets[currentMaxBackupsKeptIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentMaxBackupsKeptIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = maxBackupsKeptPresets[newIndex.roundToInt()].first
                        settingsViewModel.setMaxBackupsKept(presetValue)
                    },
                    valueRange = 0f..(maxBackupsKeptPresets.size - 1).toFloat(),
                    steps = maxBackupsKeptPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Older backups beyond this count are automatically deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (lastBackupTime > 0) {
                    "Last backup: " + SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        .format(Date(lastBackupTime))
                } else {
                    "No backup has been made yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { settingsViewModel.runBackupNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = backupFolderName != null
            ) {
                Text("Back Up Now")
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

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // About section
            Text(
                "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Build", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            BuildConfig.BUILD_TYPE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Commit", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            BuildConfig.GIT_COMMIT,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone section
            Text(
                "Danger Zone",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showDeleteAllDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = totalCards > 0
            ) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Delete All Cards",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permanently remove all cards, review history, and group assignments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Delete All Cards confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete All Cards?") },
            text = {
                Text(
                    "This will permanently delete all $totalCards cards, " +
                        "their review history, and all group assignments. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        cardViewModel.deleteAllCards()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
