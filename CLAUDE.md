# Agent Instructions

## Branch Naming Convention

Always prefix new branch names with the date in the form `YYYY-MM-DD` so that new branches can be discovered easily.

Example: `2026-02-08/feature-name` or `2026-02-08/fix-slider-alignment`

## Project Overview

This is an Android app (Kotlin + Jetpack Compose) for practicing martial arts and fencing techniques using spaced repetition. It uses a delayed-choice workflow where users practice cards first and grade them afterward.

## Build & Test

- Build: `./gradlew assembleDebug`
- Test: `./gradlew testDebugUnitTest`
- Do NOT attempt to build or test after a rebase. The CI environment may not have network access for Gradle dependencies.

## Architecture

- MVVM with Repository pattern
- Database: Room (SQLite)
- Preferences: Android DataStore
- UI: Jetpack Compose with Material Design 3
- Algorithms: FSRS-6

## Key Directories

- `app/src/main/java/com/fencing/spacedrepetition/algorithm/` - Spaced repetition engines
- `app/src/main/java/com/fencing/spacedrepetition/data/` - Models, DAOs, repositories, preferences
- `app/src/main/java/com/fencing/spacedrepetition/ui/` - Screens, viewmodels, components
- `app/src/test/` - Unit tests
