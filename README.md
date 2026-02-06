# Delayed Choice Spaced Repetition

An Android app for practicing martial arts and fencing techniques using delayed-choice spaced repetition. Practice a set of cards during training, then grade your recall afterwards -- designed for physical disciplines where immediate self-grading isn't practical.

**Status**: Ready for Google Play Store deployment

## Features

### Delayed-Choice Practice Flow
- Start a practice session and study a configurable number of cards (1--6)
- Swipe or tap through cards, revealing descriptions when ready
- After finishing, grade every card at once: **Skip**, **Again**, **Hard**, **Good**, or **Easy**
- The spaced repetition algorithm schedules your next review based on your grades

### Dual Algorithm Support
- **FSRS (Free Spaced Repetition Scheduler)**: Modern algorithm using stability and difficulty parameters for sophisticated scheduling
- **SM-2 (SuperMemo 2)**: Classic algorithm using ease factor and repetition count
- Algorithm is selectable per card

### Groups and Organization
- Create groups (decks) to organize cards by topic, technique, or training focus
- **Independent learning states**: optionally track separate progress for the same card across different groups
- Select which group to practice from the home screen

### Card Management
- Add, edit, and delete flashcards with question and description fields
- Attach images to cards
- Assign categories and tags
- Bulk selection and deletion
- Edit cards inline during practice sessions

### Import and Export
- Export cards as tab-separated (TSV) files with optional GZIP compression
- Three export formats with increasing detail:
  - **V1**: Card content + full learning state
  - **V2**: V1 + group-specific learning states
  - **V3**: V2 + base64-encoded images
- Import auto-detects format and merges with existing cards
- Simple two-column (question/answer) import also supported

### Configurable Scheduling
- **Practices per week** (1--7): adjusts review intervals to match your training frequency so cards come due on days you actually practice
- **Maximum interval**: caps the longest gap between reviews (1 week to 10 years)
- **Randomization**: shuffle due cards within configurable time buckets (1 hour to 1 week) to add variety while preserving spaced repetition priority

### Settings
- Theme: System, Light, or Dark
- Cards per session: 1--6
- Auto-show description: reveal card descriptions immediately without tapping
- Randomize due cards with configurable bucket size
- Practices per week for scheduling adjustment
- Maximum review interval

### Donation System
- Support development with optional in-app donations via Google Play Billing
- Small Coffee ($0.99), Big Coffee ($2.99), Generous Support ($4.99)
- All features are free -- donations are entirely optional

### Privacy
- Fully offline -- no network requests, no analytics, no telemetry
- No permissions required (except billing for optional donations)
- All data stored locally on device
- See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite) with migrations
- **Preferences**: DataStore
- **Monetization**: Google Play Billing (optional donations)
- **Image loading**: Coil
- **Minimum API**: 24 (Android 7.0)

## Project Structure

```
app/src/main/java/com/fencing/spacedrepetition/
├── algorithm/              # Spaced repetition algorithms
│   ├── FSRSAlgorithm.kt   # FSRS-4.5 implementation
│   └── SM2Algorithm.kt    # SM-2 implementation
├── billing/                # Google Play Billing
│   └── BillingManager.kt  # Donation handling
├── data/
│   ├── model/             # Room entities and data classes
│   ├── dao/               # Database access objects
│   ├── repository/        # Business logic and scheduling
│   └── preferences/       # DataStore settings
├── ui/
│   ├── screen/            # Composable screens
│   ├── viewmodel/         # ViewModels
│   ├── components/        # Reusable UI components
│   ├── navigation/        # Navigation graph
│   └── theme/             # Material 3 theme
├── util/                  # Import/export utilities
└── MainActivity.kt
```

## How It Works

### Practice Flow
1. Select a practice group from the home screen
2. Tap **Start Practice** to begin a session
3. Study each card -- swipe left/right or use navigation buttons
4. Tap **Show Description** to reveal the card's description
5. After all cards, grade each one:
   - **Skip**: exclude from scheduling
   - **Again**: complete failure to recall
   - **Hard**: difficult recall
   - **Good**: correct with effort
   - **Easy**: perfect recall
6. Grades are submitted and the algorithm schedules the next review for each card

### Scheduling
The algorithm calculates how many days until a card should next be reviewed. Two additional settings adjust this:

- **Practices per week**: if you train 3 days a week, a card due in 5 days is snapped to the nearest practice-day interval (~4.7 days) so it comes due when you'll actually be training
- **Maximum interval**: hard cap on the longest gap (e.g., 6 months means no card waits longer than 6 months)

### Randomization
When enabled, due cards are grouped into time buckets (configurable from 1 hour to 1 week). Cards within the same bucket are shuffled; ordering between buckets is preserved. This gives variety within a session while still prioritizing the most overdue cards.

## Building and Running

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK API 24+

### Build
```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew bundleRelease
./gradlew assembleRelease
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

### Cards
Question and description content, category, tags, image paths, algorithm type (FSRS or SM-2), algorithm-specific parameters, and review scheduling timestamps.

### Groups
Named groups with optional independent learning mode. Cards are linked to groups via a many-to-many relationship. Group-specific learning states allow tracking separate progress per group.

### Practice Sessions
Session start/end times, associated card IDs, and assigned grades.

### Review Logs
Complete history of every review including grade, algorithm used, state before and after, and scheduling metrics.

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

- Statistics and analytics dashboard
- Cloud sync
- Custom algorithm parameters fine-tuning
- Audio support for cards
- Study reminders and notifications
- Deck sharing community

## License

This project is open source and available for educational purposes.

## Credits

- FSRS algorithm based on the open-source [FSRS project](https://github.com/open-spaced-repetition/fsrs4anki)
- SM-2 algorithm from [SuperMemo research](https://www.supermemo.com/en/archives1990-2015/english/ol/sm2)
- Built with Jetpack Compose and Material Design 3
- Designed for fencing and martial arts practice but adaptable to any sport or learning domain

## Documentation

- [DEPLOYMENT.md](DEPLOYMENT.md) - Complete Google Play Store deployment guide
- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) - Keystore creation and signing guide
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) - Privacy policy and data handling
