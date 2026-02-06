# Google Play Store Deployment Guide

This guide walks you through deploying the Martial Arts Practice app to the Google Play Store.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Preparing Your Build](#preparing-your-build)
3. [Creating Store Assets](#creating-store-assets)
4. [Setting Up In-App Products](#setting-up-in-app-products)
5. [Creating Your Play Console Listing](#creating-your-play-console-listing)
6. [Uploading Your App](#uploading-your-app)
7. [Publishing](#publishing)
8. [Post-Launch](#post-launch)

## Prerequisites

### 1. Google Play Console Account
- [ ] Create a Google Play Developer account at https://play.google.com/console
- [ ] Pay the one-time $25 registration fee
- [ ] Complete account verification (may take 1-2 days)

### 2. Keystore Setup
- [ ] Follow the [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) guide to create your signing key
- [ ] Backup your keystore file and passwords securely
- [ ] Set up environment variables for signing

### 3. Privacy Policy
- [ ] Host the privacy policy (PRIVACY_POLICY.md) on a publicly accessible URL
  - Options: GitHub Pages, your website, Google Sites, etc.
  - Example: https://yourusername.github.io/privacy-policy.html
- [ ] The URL must be accessible and not require login

## Preparing Your Build

### Step 1: Update Version Information

Edit `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.fencing.spacedrepetition"
    versionCode = 1  // Increment for each release (1, 2, 3, ...)
    versionName = "1.0.0"  // User-visible version (1.0.0, 1.0.1, 1.1.0, ...)
    // ...
}
```

**Version Strategy:**
- `versionCode`: Integer that increases with each release (1, 2, 3...)
- `versionName`: Semantic versioning (MAJOR.MINOR.PATCH)
  - MAJOR: Breaking changes
  - MINOR: New features
  - PATCH: Bug fixes

### Step 2: Set Up Signing

Ensure your environment variables are set (see KEYSTORE_SETUP.md):
- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

### Step 3: Build Release AAB

Google Play requires Android App Bundle (AAB) format:

```bash
# Clean previous builds
./gradlew clean

# Build release AAB
./gradlew bundleRelease
```

The AAB will be at: `app/build/outputs/bundle/release/app-release.aab`

### Step 4: Test the Release Build

Build and install APK to test locally:

```bash
# Build APK from bundle
./gradlew assembleRelease

# Install on connected device
adb install app/build/outputs/apk/release/app-release.apk
```

**Critical Testing:**
- [ ] Test all main features
- [ ] Verify theme switching works
- [ ] Test card creation, editing, deletion
- [ ] Test practice sessions
- [ ] Test import/export functionality
- [ ] **DO NOT** test donations (they won't work until published)
- [ ] Check ProGuard hasn't broken anything

## Creating Store Assets

### Required Graphics

#### 1. App Icon
- ✅ Already present in `app/src/main/res/mipmap-*/`
- Must be 512×512 PNG for Play Console

Create high-res icon:
```bash
# If you have icon.svg, convert to 512x512 PNG
# Upload this to Play Console
```

#### 2. Feature Graphic (Required)
- **Size**: 1024 × 500 pixels
- **Format**: PNG or JPEG
- **Content**: Showcases your app, visible in Play Store
- **No borders**: Google adds the border

**Tips:**
- Include app name or logo
- Show key features visually
- Use brand colors
- Keep text minimal and readable

#### 3. Screenshots (Minimum 2, Maximum 8)
- **Phone**: At least 2 screenshots
  - Minimum: 320px
  - Maximum: 3840px
  - Recommended: 1080 × 1920 (portrait) or 1920 × 1080 (landscape)

**What to Screenshot:**
- Home screen with cards
- Practice/grading screen
- Card editing screen
- Settings screen
- Import/export features

**Tips:**
- Use actual app content (real cards)
- Show key features
- Use consistent device frame (optional)
- Add brief captions in images if helpful

#### 4. Promotional Graphics (Optional but Recommended)

**Promo Graphic**: 180 × 120 pixels (PNG/JPEG)
**TV Banner**: 1280 × 720 pixels (if targeting Android TV)

### Screenshot Tools

**Capture on Android:**
```bash
# Take screenshot of connected device
adb exec-out screencap -p > screenshot.png
```

**Or use Android Studio:**
1. Run app on emulator/device
2. Use screenshot button in emulator
3. Or use Device File Explorer

**Frame Screenshots (Optional):**
- Use: https://screenshots.pro/
- Or: https://www.figma.com/ with device mockups

## Setting Up In-App Products

The app has 3 donation tiers that need to be configured in Play Console.

### Step 1: Access In-App Products

1. Go to Play Console → Your App
2. Select "Monetize" → "In-app products"
3. Click "Create product" (do this 3 times)

### Step 2: Create Donation Products

**Product 1: Small Donation**
- Product ID: `donation_small`
- Name: "Small Coffee"
- Description: "Buy me a small coffee - support app development"
- Price: $0.99 (or equivalent in your currency)
- Status: Active

**Product 2: Medium Donation**
- Product ID: `donation_medium`
- Name: "Big Coffee"
- Description: "Buy me a big coffee - support app development"
- Price: $2.99
- Status: Active

**Product 3: Large Donation**
- Product ID: `donation_large`
- Name: "Generous Support"
- Description: "Generous support for app development"
- Price: $4.99
- Status: Active

**Important Notes:**
- Product IDs MUST match exactly what's in BillingManager.kt
- These are consumable products (users can purchase multiple times)
- You can adjust prices for different countries

### Step 3: Testing Donations (Before Launch)

1. Add test account emails in Play Console → Settings → License testing
2. Build a test release (internal testing track)
3. Install and test donation flow
4. Test accounts can make purchases without being charged

## Creating Your Play Console Listing

### Step 1: App Details

**App name**: "Martial Arts Practice" (or your chosen name)
- Must be unique in Play Store
- 30 character limit

**Short description** (80 characters max):
```
Master martial arts techniques with spaced repetition flashcards
```

**Full description** (4000 characters max):
```
Master your martial arts techniques with the power of spaced repetition!

Martial Arts Practice is a flashcard app designed specifically for martial artists who want to memorize and retain techniques, forms, terminology, and strategies effectively.

✨ KEY FEATURES

📚 Smart Spaced Repetition
• Uses the scientifically-proven FSRS (Free Spaced Repetition Scheduler) algorithm
• Also supports classic SM-2 algorithm
• Cards appear at optimal intervals for maximum retention
• Forget less, remember more

🥋 Built for Martial Artists
• Practice techniques, forms, combinations, and strategies
• Add images to visualize movements
• Organize cards into groups (kicks, punches, forms, etc.)
• Track your progress over time

⚙️ Customizable Practice
• Choose how many cards per session
• Set maximum review intervals
• Randomize due cards for variety
• Auto-show answers or test yourself

🎨 Beautiful & Fast
• Modern Material Design 3 interface
• Dark mode support
• Smooth animations
• Works completely offline

📤 Import & Export
• Export cards to CSV
• Import cards in bulk
• Backup your progress
• Share card decks

🔒 Privacy First
• All data stored locally on your device
• No account required
• No tracking or analytics
• No ads, ever

💝 Support Development
• Optional donations to support development
• Keep the app free and ad-free
• 100% of features available to everyone

Perfect for practicing:
• Karate, Taekwondo, Kung Fu, Judo, Jiu-Jitsu
• Muay Thai, Boxing, Kickboxing
• Krav Maga, Wing Chun, Aikido
• Any martial art or combat sport!

The spaced repetition method is scientifically proven to improve long-term retention by reviewing material at increasing intervals. Instead of cramming, you'll study efficiently and remember for life.

Whether you're a beginner learning basic stances or an advanced practitioner mastering complex forms, Martial Arts Practice helps you build lasting knowledge.

🆓 100% Free & Open Source
This app is completely free with all features unlocked. No subscriptions, no paywalls, no locked features. Support development with optional donations if you find it helpful!

Download now and start mastering your martial arts techniques today! 🥋
```

### Step 2: Categorization

**App category**: Education (or Health & Fitness)

**Tags** (up to 5):
- Education
- Sports
- Learning
- Martial Arts
- Fitness

### Step 3: Contact Details

- **Email**: Your contact email (required, visible to users)
- **Phone**: Optional
- **Website**: Your website or GitHub repo
  - Example: https://github.com/KGardevoir/Fencing-Spaced-Repetition
- **Privacy Policy**: URL to your hosted privacy policy (REQUIRED)

### Step 4: Store Listing

**Upload Assets:**
- [ ] App icon (512×512 PNG)
- [ ] Feature graphic (1024×500)
- [ ] At least 2 screenshots
- [ ] Promo graphic (optional)

### Step 5: Content Rating

Complete the content rating questionnaire:

1. Click "Start questionnaire"
2. Select category: "Education" or "Sports"
3. Answer questions:
   - Violence: None
   - Sexual content: None
   - Profanity: None
   - Controlled substances: None
   - Gambling: None
   - User interaction: No (unless you add social features)
   - Personal info collection: No
   - In-app purchases: Yes (donations)

Result should be: **Everyone** or **E (Everyone)**

### Step 6: Target Audience and Content

**Target age group**:
- Check "13+" or "Everyone"

**Ads**: No (app has no ads)

**In-app purchases**: Yes
- Price range: $0.99 - $4.99

### Step 7: Data Safety

This is critical and reviewed by Google:

**Data Collection**:
- "Does your app collect or share any of the required user data types?"
  - Answer: **No**

**Security Practices**:
- "Is data encrypted in transit?": Not applicable (no network data)
- "Can users request data deletion?": Yes (uninstall app)

Justify your answers:
```
This app stores all data locally on the user's device and does not collect,
transmit, or share any user data. Donations are processed through Google Play
Billing, which is handled entirely by Google. We do not have access to any
payment or personal information.
```

## Uploading Your App

### Step 1: Choose a Release Track

**Internal Testing** (recommended first):
- Up to 100 testers
- Fast review (~1 hour)
- Test everything before public release

**Closed Testing** (optional):
- Up to 10,000 testers
- Longer review
- Beta testing

**Open Testing** (optional):
- Anyone can join
- Public beta

**Production**:
- Public release on Play Store
- Longer review (2-7 days)

### Step 2: Create a Release

1. Go to "Release" → Choose your track
2. Click "Create new release"
3. Enroll in Play App Signing (HIGHLY RECOMMENDED)
   - Google securely stores your app signing key
   - You can reset your upload key if lost
   - More secure

### Step 3: Upload AAB

1. Upload `app/build/outputs/bundle/release/app-release.aab`
2. Google will analyze it and show:
   - Supported devices
   - APK sizes for different configurations
   - Warnings or errors (fix these)

### Step 4: Release Notes

Write what's new in this version:

**Version 1.0.0**
```
🎉 Initial release!

• Spaced repetition flashcards for martial arts
• FSRS and SM-2 algorithms
• Card organization with groups
• Import/export to CSV
• Dark mode support
• Completely offline
• Privacy-focused
```

### Step 5: Review and Rollout

1. Review all information
2. Click "Save"
3. Click "Review release"
4. Fix any errors or warnings
5. Click "Start rollout to [track]"

## Publishing

### Internal Testing Timeline
- Upload: Immediate
- Review: ~1 hour
- Available to testers: After review

### Production Timeline
- Upload: Immediate
- Review: 2-7 days (sometimes up to 14)
- Available to public: After approval

### What Google Reviews

- App functionality
- Privacy policy compliance
- Metadata accuracy
- Content rating appropriateness
- Permissions usage
- Data safety declarations
- Compliance with policies

### Common Rejection Reasons

1. **Privacy policy issues**
   - Not accessible
   - Doesn't match data collection claims
   - Missing required information

2. **Metadata violations**
   - Misleading descriptions
   - Inappropriate content
   - Keyword stuffing

3. **Functionality issues**
   - App crashes on launch
   - Core features don't work
   - Placeholder content

4. **Permissions misuse**
   - Requesting unnecessary permissions
   - Not explaining permission usage

**For this app**: Should pass review easily as it's straightforward and privacy-friendly.

## Post-Launch

### Monitor Performance

**Play Console Dashboard:**
- Crash reports
- ANRs (App Not Responding)
- User feedback
- Ratings and reviews
- Installation metrics

### Respond to Reviews

- Reply to user reviews (especially negative ones)
- Address bugs and issues
- Thank users for positive feedback

### Update Strategy

**When to update:**
- Bug fixes: Patch version (1.0.1)
- New features: Minor version (1.1.0)
- Major changes: Major version (2.0.0)

**Update process:**
1. Increment versionCode and versionName
2. Build new AAB
3. Create new release in Play Console
4. Write release notes
5. Submit for review

### Monitoring Donations

Play Console → Monetize → In-app products:
- View purchase statistics
- See revenue (after Google's 15-30% fee)
- Track which donation tiers are popular

## Checklist Before Submitting

### Build
- [ ] Version code and name updated
- [ ] Release AAB built successfully
- [ ] App tested on release build
- [ ] ProGuard/R8 not causing issues
- [ ] Keystore backed up securely

### Store Listing
- [ ] App name decided
- [ ] Short description (80 chars)
- [ ] Full description written
- [ ] App icon (512×512)
- [ ] Feature graphic (1024×500)
- [ ] At least 2 screenshots
- [ ] Category selected
- [ ] Content rating completed
- [ ] Privacy policy URL set

### In-App Products
- [ ] donation_small created ($0.99)
- [ ] donation_medium created ($2.99)
- [ ] donation_large created ($4.99)
- [ ] All products activated

### Privacy & Compliance
- [ ] Privacy policy hosted publicly
- [ ] Data safety section completed
- [ ] Content rating completed
- [ ] Target audience set
- [ ] BILLING permission in manifest

### Final Checks
- [ ] All metadata accurate
- [ ] No test/placeholder content
- [ ] Contact email set
- [ ] Release notes written
- [ ] No obvious bugs

## Useful Links

- **Play Console**: https://play.google.com/console
- **Policy Center**: https://play.google.com/about/developer-content-policy/
- **Developer Docs**: https://developer.android.com/distribute
- **App Bundle Docs**: https://developer.android.com/guide/app-bundle
- **Content Rating**: https://support.google.com/googleplay/android-developer/answer/9859655

## Getting Help

If you encounter issues:

1. **Check Play Console Help**: Detailed explanations for most issues
2. **Stack Overflow**: Tag questions with `google-play` and `android`
3. **Reddit**: r/androiddev community
4. **Play Console Support**: For policy/account issues

## Cost Summary

- **Play Developer Account**: $25 one-time fee
- **Google Play Fee**: 15% on first $1M revenue, 30% after
  - For donations: Google takes 15-30% of each donation
- **Hosting**: Free (use GitHub Pages for privacy policy)
- **Total upfront**: $25

## Tips for Success

1. **Polish Your Listing**: Great screenshots and description matter
2. **Respond to Reviews**: Shows you care about users
3. **Update Regularly**: Keeps app relevant and ranks better
4. **Monitor Crashes**: Fix issues quickly
5. **Be Patient**: Reviews can take time
6. **Test Thoroughly**: Better to delay than release buggy app
7. **Backup Everything**: Keystore, source code, assets

## Privacy-Focused App Benefits

Your app's privacy-first approach is a major selling point:
- ✅ No data collection = easier compliance
- ✅ No analytics = simpler data safety form
- ✅ No accounts = fewer security concerns
- ✅ No ads = better user experience
- ✅ Offline = works anywhere

Emphasize this in your listing!

---

**Good luck with your launch! 🚀**

If you have questions, refer to:
- [KEYSTORE_SETUP.md](KEYSTORE_SETUP.md) for signing issues
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for privacy questions
- [README.md](README.md) for app functionality

Remember: Take your time, test thoroughly, and don't rush. A polished first release makes a great impression!
