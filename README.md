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

### Opponents and Fight Difficulty
- Record who you practised against: each opponent carries a skill multiplier
  that scales how much stability a successful review earns (1.0 is neutral,
  above is a harder opponent, below an easier one)
- Rate the fight itself from **1** to **5** -- 1 = 0.5x, 2 = 0.75x, 3 = 1.0x
  (neutral), 4 = 1.25x, 5 = 1.5x -- for everything the opponent's rating does
  not capture: tempo, fatigue, a pool bout versus a relaxed drill
- The two multiply: a 1.25 opponent in a rating-5 fight earns 1.25 x 1.5 =
  1.875 of the neutral stability gain
- Both describe the fight rather than any one card, so both are chosen once for
  the practice session and recorded against every card in it -- there is no
  per-card override. Either can be left unset, which is neutral
- Every review records the rating it was graded with and the stability gain it
  earned; the history screen shows both on each entry and summarises the
  session's ratings on its card
- A recorded rating is read-only: it is half of the multiplier the card was
  already scheduled with, so editing it afterwards would leave an entry that
  disagrees with its own schedule. A fight rated wrongly is put right by
  grading the card again

### Session Notes
- Notes and their photos belong to the practice session -- the fight -- not to
  individual cards: one set of notes per session, written on the grading
  screen and editable afterwards from the session in the history
- Sessions written before this kept a note per card; upgrading merges each
  session's notes into one, in the order the cards were reviewed, each under
  the question of the card it was written about, and moves their photos with
  them
- Quick grades from the card editor are not part of a session and keep their
  own notes
- Exports with history carry the sessions and their notes, and an import
  restores them

### Groups and Organization
- Create groups (decks) to organize cards by topic, technique, or training focus
- **Learning groups**: card groups learn against Global progress by default, or share a separate set of progress with the other groups in a learning group
- Each learning group sets how its sessions are drawn -- cards per session, shuffling and its bucket size -- and how its progress is scheduled -- practice days, maximum interval, desired retention, interval fuzzing. The defaults are Global's, edited from the Global row on the Learning Groups screen. A card group's only setting is whether to auto-show the description
- **Learning concepts** (opt-in per learning group, and for Global): tag cards with the concepts they practise; where switched on, tagged cards are scheduled by their concepts (a session draws the due concepts and shows the cards covering the most of them), concept progress is derived from the review history of the cards that carry it, and respects learning groups
- Select which group to practice from the home screen

### Card Management
- Add, edit, and delete flashcards with question and description fields
- Attach images to cards
- Assign categories and tags
- Bulk selection and deletion
- Edit cards inline during practice sessions

### Import and Export
- Export the whole collection as a YAML document, gzipped (`*.yaml.gz`)
- One export carries everything: card content, images (inline base64), group
  membership, per-group and per-learning-group settings, every learning state, opponents, and
  optionally the full review history
- Readable and editable by hand -- a minimal deck is just a `cards:` list of
  `question`/`answer` pairs
- Import auto-detects the format and merges with existing cards; the
  tab-separated exports written by earlier versions (`#FSR_EXPORT_V1` through
  `V4`) still import
- CSV (Concept, Description, Images) remains available for exchanging decks
  with spreadsheets and other tools

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

Every user record (cards, groups, learning groups, opponents, sessions, review logs) carries a stable `uid` that survives renames and travels in exports; row ids are local. See [docs/DESIGN.md](docs/DESIGN.md).

### Cards
Question and description content, category, tags, image paths, FSRS scheduling parameters, review scheduling timestamps, and when its progress was last reset.

### Groups
Named groups, each pointing at a learning group (null = Global), with an optional auto-show override. Cards are linked to groups via a many-to-many relationship. Learning states are kept per card per learning group, so every group in the same learning group shares one set of progress.

### Learning Groups
Named sets of shared learning progress, with optional overrides for cards per session, shuffling, and the settings that schedule it: practice days, maximum interval, desired retention and interval fuzzing.

### Learning Concepts
Global named concepts linked to cards many-to-many. Concept scheduling is opt-in per learning context: each learning group has a `useConcepts` flag and Global has a setting; contexts left off schedule card by card as before. A concept's state is derived, not accumulated: it is rebuilt by replaying the review history of the cards that currently carry it, per learning context (a review counts towards the learning group of the group it names; no group or the card editor counts towards Global). Each practice session is one concept review graded with the rounded mean of its cards' grades (skips excluded); reviews outside a session count singly. Tagging or untagging a card, grading, deleting a card, importing history, or changing groups rederives the affected concepts; a concept whose cards have no history is new. The result is stored (Global on the concept, one row per learning group) so drawing a session needs no replay. Where concept scheduling is on, a session's due concepts pick the cards that cover the most of them.

### Practice Sessions
Session start/end times, associated card IDs, and assigned grades.

### Review Logs
Complete history of every review including grade, algorithm used, state before and after, and scheduling metrics. Each review has its own stable `uid` and names its group and opponent by theirs, so history survives renames, deletions and moves between devices.

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

- [docs/DESIGN.md](docs/DESIGN.md) - Design notes: stable identifiers, learning contexts, resets, history, archive matching
- [DEPLOYMENT.md](DEPLOYMENT.md) - Complete Google Play Store deployment guide
- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) - Keystore creation and signing guide
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) - Privacy policy and data handling
