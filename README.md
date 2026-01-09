# Fencing Spaced Repetition App

An Android app for practicing fencing techniques using delayed choice spaced repetition with both FSRS and SM-2 algorithms.

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
- **Practice Sessions**: View 3 cards during practice, then grade them all at once
- **Card Management**: Add, edit, and organize fencing technique cards
- **Categories**: Organize cards by technique type (e.g., Basic Stance, Footwork, Attacks)
- **Progress Tracking**: View due cards and total card count
- **Sample Data**: Load sample fencing technique cards to get started

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite)
- **Algorithms**:
  - FSRS-4.5 implementation
  - SM-2 implementation

## Project Structure

```
app/src/main/java/com/fencing/spacedrepetition/
├── algorithm/              # Spaced repetition algorithms
│   ├── FSRSAlgorithm.kt   # FSRS implementation
│   └── SM2Algorithm.kt    # SM-2 implementation
├── data/                   # Data layer
│   ├── model/             # Data models
│   ├── dao/               # Room DAOs
│   └── repository/        # Repository classes
├── ui/                     # UI layer
│   ├── screen/            # Composable screens
│   ├── viewmodel/         # ViewModels
│   ├── navigation/        # Navigation setup
│   └── theme/             # App theme
└── MainActivity.kt         # Main activity
```

## How It Works

### Practice Flow
1. Start a practice session from the home screen
2. View and study 3 cards during your training
3. Navigate through cards, revealing answers as needed
4. After finishing practice, grade each card:
   - **Again**: Complete failure
   - **Hard**: Difficult recall
   - **Good**: Correct with effort
   - **Easy**: Perfect recall
5. Submit grades to update card schedules

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

# Run tests
./gradlew test
```

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

## Future Enhancements

Potential features for future versions:
- Statistics and analytics dashboard
- Export/import card decks
- Cloud sync
- Custom algorithm parameters
- Image support for cards
- Audio pronunciation guides
- Multiple deck support
- Study reminders/notifications

## License

This project is open source and available for educational purposes.

## Credits

- FSRS algorithm based on the open-source FSRS project
- SM-2 algorithm from SuperMemo research
- Designed for fencing practice but adaptable to any sport or learning domain
