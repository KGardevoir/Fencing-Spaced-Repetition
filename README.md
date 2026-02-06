# Martial Arts Practice

An Android app for mastering martial arts techniques using spaced repetition flashcards with both FSRS and SM-2 algorithms.

**Status**: Ready for Google Play Store deployment

## Features

### Delayed Choice Spaced Repetition
- Practice 3 cards during your training session
- Grade all cards at the end based on recall
- Optimal for sports practice where immediate grading isn't practical

### Dual Algorithm Support
- **FSRS (Free Spaced Repetition Scheduler)**: Modern algorithm with improved predictions
- **SM-2 (SuperMemo 2)**: Classic, proven spaced repetition algorithm
- Choose the algorithm per card based on your preference

### Core Functionality
- **Practice Sessions**: View cards during practice, then grade them all at once
- **Card Management**: Add, edit, and organize technique cards with optional images
- **Groups**: Organize cards by technique type with independent learning states
- **Progress Tracking**: View due cards and track your progress over time
- **Import/Export**: Import and export card decks as CSV files
- **Settings**: Customizable theme, practice settings, and algorithm parameters
- **Donation System**: Support development with optional in-app donations

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite) with migrations
- **Monetization**: Google Play Billing (optional donations)
- **Image Loading**: Coil
- **Algorithms**:
  - FSRS-4.5 implementation
  - SM-2 implementation

## Project Structure

```
app/src/main/java/com/fencing/spacedrepetition/
├── algorithm/              # Spaced repetition algorithms
│   ├── FSRSAlgorithm.kt   # FSRS implementation
│   └── SM2Algorithm.kt    # SM-2 implementation
├── billing/                # Google Play Billing
│   └── BillingManager.kt  # Donation handling
├── data/                   # Data layer
│   ├── model/             # Data models
│   ├── dao/               # Room DAOs
│   ├── repository/        # Repository classes
│   └── preferences/       # DataStore preferences
├── ui/                     # UI layer
│   ├── screen/            # Composable screens
│   ├── viewmodel/         # ViewModels
│   ├── navigation/        # Navigation setup
│   ├── components/        # Reusable UI components
│   └── theme/             # App theme
├── util/                   # Utilities
│   └── CardImportExport.kt # CSV import/export
└── MainActivity.kt         # Main activity
```

## How It Works

### Practice Flow
1. Start a practice session from the home screen (or specific group)
2. View and study cards during your training
3. Navigate through cards, revealing answers as needed (or use auto-show)
4. After finishing practice, grade each card:
   - **Again**: Complete failure - card needs more work
   - **Hard**: Difficult recall - barely remembered
   - **Good**: Correct with effort - standard recall
   - **Easy**: Perfect recall - very easy
5. Submit grades to update card schedules
6. Cards are scheduled based on the chosen algorithm (FSRS or SM-2)

### Algorithm Selection
Each card can use either FSRS or SM-2:
- **FSRS**: Uses stability and difficulty parameters for sophisticated scheduling
- **SM-2**: Uses ease factor and repetition count for classic scheduling

## Building and Running

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK with API level 24+ (Android 7.0+)

### Build Steps
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on emulator or physical device (API 24+)

### Gradle Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Build release AAB (for Play Store)
./gradlew bundleRelease

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test
```

### For Google Play Store Deployment
See [DEPLOYMENT.md](DEPLOYMENT.md) for complete deployment instructions, including:
- Creating a keystore for signing (see [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md))
- Setting up in-app donation products
- Creating store assets
- Privacy policy requirements
- Submission process

## Database Schema

### Cards Table
- Question and answer content
- Category and tags
- Algorithm type (FSRS or SM-2)
- Algorithm-specific parameters
- Review scheduling data

### Practice Sessions Table
- Session timing
- Associated card IDs
- Grades assigned

### Review Logs Table
- Complete review history
- Performance tracking
- Algorithm state changes

## Privacy & Data

This app is privacy-first:
- ✅ All data stored locally on your device
- ✅ No data collection or tracking
- ✅ No analytics or telemetry
- ✅ No account required
- ✅ Works completely offline
- ✅ Optional donations processed securely by Google Play

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for complete privacy details.

## Supporting Development

The app is completely free with all features unlocked. If you find it helpful, you can support development through optional in-app donations:
- Small Coffee ($0.99)
- Big Coffee ($2.99)
- Generous Support ($4.99)

Donations help keep the app free, ad-free, and actively maintained!

## Future Enhancements

Potential features for future versions:
- Statistics and analytics dashboard
- Cloud sync (optional)
- Custom algorithm parameters fine-tuning
- Audio pronunciation guides
- Study reminders/notifications
- Additional algorithm options
- Deck sharing community

## License

This project is open source and available for educational purposes.

## Credits

- FSRS algorithm based on the open-source [FSRS project](https://github.com/open-spaced-repetition/fsrs4anki)
- SM-2 algorithm from [SuperMemo research](https://www.supermemo.com/en/archives1990-2015/english/ol/sm2)
- Built with Jetpack Compose and Material Design 3
- Originally designed for martial arts practice but adaptable to any learning domain

## Documentation

- [DEPLOYMENT.md](DEPLOYMENT.md) - Complete Google Play Store deployment guide
- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) - Keystore creation and signing guide
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) - Privacy policy and data handling
