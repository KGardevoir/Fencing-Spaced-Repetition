# Delayed Choice Spaced Repetition

An Android app for practicing martial arts and fencing techniques using delayed-choice spaced repetition. Practice a set of cards during training, then grade your recall afterwards -- designed for physical disciplines where immediate self-grading isn't practical.

**Status**: Ready for Google Play Store deployment

## Features

### Delayed-Choice Practice Flow
- Start a practice session and study a configurable number of cards (1--6)
- Swipe or tap through cards, revealing descriptions when ready
- After finishing, grade every card at once: **Skip**, **Again**, **Hard**, **Good**, or **Easy**
- The spaced repetition algorithm schedules your next review based on your grades

### Scheduling Algorithm
- **FSRS (Free Spaced Repetition Scheduler)**: Modern algorithm using stability and difficulty parameters for sophisticated scheduling

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

### Privacy
- Fully offline -- no network requests, no analytics, no telemetry
- No permissions required
- All data stored locally on device
- See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite) with migrations
- **Preferences**: DataStore
- **Image loading**: Coil
- **Minimum API**: 24 (Android 7.0)

## Project Structure

```
app/src/main/java/com/fencing/spacedrepetition/
├── algorithm/              # Spaced repetition algorithms
│   └── FSRSAlgorithm.kt   # FSRS-4.5 implementation
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
- Creating store assets
- Privacy policy requirements
- Submission process

## Database Schema

### Cards
Question and description content, category, tags, image paths, FSRS scheduling parameters, and review scheduling timestamps.

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

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for complete privacy details.

## Supporting Development

The app is free software with every feature unlocked -- no ads, no paywalls,
no tracking. If you find it useful, the Settings screen links to
[GitHub Sponsors](https://github.com/sponsors/KGardevoir).

Support links open in your browser. The app deliberately contains no in-app
purchase SDK: Google Play Billing is proprietary, and depending on it would
make the app ineligible for [F-Droid](https://f-droid.org/), which only builds
software whose dependencies are all free software.

## Future Enhancements

- Statistics and analytics dashboard
- Cloud sync
- Custom algorithm parameters fine-tuning
- Audio support for cards
- Study reminders and notifications
- Deck sharing community

## License

Copyright (C) 2026 Enmar Abrams

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for more
details.

Source files carry [SPDX](https://spdx.dev/) identifiers
(`GPL-3.0-or-later`) rather than full license headers.

## Credits

- FSRS algorithm based on the open-source [FSRS project](https://github.com/open-spaced-repetition/fsrs4anki), used under the MIT License
- Built with Jetpack Compose and Material Design 3
- Designed for fencing and martial arts practice but adaptable to any sport or learning domain

## Documentation

- [DEPLOYMENT.md](DEPLOYMENT.md) - Complete Google Play Store deployment guide
- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) - Keystore creation and signing guide
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) - Privacy policy and data handling
