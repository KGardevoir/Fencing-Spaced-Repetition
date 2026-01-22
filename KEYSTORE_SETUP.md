# Android Keystore Setup Guide

This guide will help you create a keystore for signing your Android app for Google Play Store release.

## Why You Need a Keystore

Google Play Store requires all apps to be signed with a release key. This keystore:
- Proves you are the app's developer
- Ensures app updates come from you
- Is required for Play Store upload

**CRITICAL**: Keep your keystore file and passwords safe! If you lose them, you cannot update your app on the Play Store.

## Creating a Keystore

### Step 1: Generate the Keystore

Run this command in your project root directory:

```bash
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias martial-arts-practice
```

### Step 2: Answer the Prompts

You'll be asked for:
1. **Keystore password**: Choose a strong password (you'll need this for every release)
2. **Key password**: Can be the same as keystore password
3. **First and last name**: Your name or organization name
4. **Organizational unit**: Your team/department (or just press Enter)
5. **Organization**: Your company/organization (or your name)
6. **City/Locality**: Your city
7. **State/Province**: Your state
8. **Country code**: Two-letter country code (e.g., US, UK, CA)

Example:
```
Enter keystore password: [enter secure password]
Re-enter new password: [re-enter password]
What is your first and last name?
  [Unknown]:  John Developer
What is the name of your organizational unit?
  [Unknown]:  Development
What is the name of your organization?
  [Unknown]:  MartialArtsApps
What is the name of your City or Locality?
  [Unknown]:  San Francisco
What is the name of your State or Province?
  [Unknown]:  California
What is the two-letter country code for this unit?
  [Unknown]:  US
Is CN=John Developer, OU=Development, O=MartialArtsApps, L=San Francisco, ST=California, C=US correct?
  [no]:  yes

Enter key password for <martial-arts-practice>
        (RETURN if same as keystore password): [press Enter or enter a different password]
```

## Setting Up Environment Variables

For security, never hardcode passwords in your code. Use environment variables instead:

### On Linux/Mac:

Add to your `~/.bashrc` or `~/.zshrc`:

```bash
export KEYSTORE_FILE="/path/to/your/release-keystore.jks"
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="martial-arts-practice"
export KEY_PASSWORD="your_key_password"
```

Then reload your shell:
```bash
source ~/.bashrc  # or source ~/.zshrc
```

### On Windows (PowerShell):

```powershell
[System.Environment]::SetEnvironmentVariable('KEYSTORE_FILE', 'C:\path\to\release-keystore.jks', 'User')
[System.Environment]::SetEnvironmentVariable('KEYSTORE_PASSWORD', 'your_keystore_password', 'User')
[System.Environment]::SetEnvironmentVariable('KEY_ALIAS', 'martial-arts-practice', 'User')
[System.Environment]::SetEnvironmentVariable('KEY_PASSWORD', 'your_key_password', 'User')
```

### Alternative: gradle.properties (Less Secure)

Create a file `keystore.properties` in your project root (add it to `.gitignore`):

```properties
storeFile=/path/to/release-keystore.jks
storePassword=your_keystore_password
keyAlias=martial-arts-practice
keyPassword=your_key_password
```

Then update `app/build.gradle.kts` to read from this file.

## Building a Release APK/AAB

Once your keystore is set up:

### Build an AAB (recommended for Play Store):
```bash
./gradlew bundleRelease
```

The AAB will be at: `app/build/outputs/bundle/release/app-release.aab`

### Build an APK:
```bash
./gradlew assembleRelease
```

The APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Backup Your Keystore

**IMMEDIATELY** backup your keystore file and passwords:

1. **Keystore file**: `release-keystore.jks`
2. **Store password**
3. **Key alias**: martial-arts-practice
4. **Key password**

Store backups in:
- Secure cloud storage (encrypted)
- Password manager
- Physical secure location

## Google Play App Signing (Recommended)

Google offers Play App Signing which:
- Stores your upload key securely
- Generates and manages your app signing key
- Allows key reset if you lose your upload key

To use it:
1. Upload your first release to Play Console
2. Enroll in Play App Signing
3. Google will manage the final signing key

This is highly recommended for new apps!

## Security Best Practices

✅ **DO**:
- Keep keystore file secure and backed up
- Use strong passwords
- Use environment variables or secure property files
- Add keystore files to `.gitignore`
- Use Play App Signing

❌ **DON'T**:
- Commit keystore to version control
- Share keystore passwords in plain text
- Use weak passwords
- Store passwords in code
- Lose your keystore (you cannot update your app!)

## Verifying Your Keystore

To verify your keystore was created correctly:

```bash
keytool -list -v -keystore release-keystore.jks -alias martial-arts-practice
```

You should see your certificate details and the SHA-256 fingerprint (needed for some Google services).

## Troubleshooting

**"Keystore file does not exist"**
- Make sure the path in KEYSTORE_FILE environment variable is correct
- The build will still work but won't be signed (for local testing)

**"Failed to read key from keystore"**
- Check your passwords are correct
- Verify the key alias matches

**"jarsigner: unable to sign jar"**
- Ensure Java keytool is installed
- Check keystore file permissions

## Next Steps

After creating your keystore:
1. ✅ Backup your keystore and passwords
2. ✅ Set up environment variables
3. ✅ Build a release AAB: `./gradlew bundleRelease`
4. ✅ Test the signed build
5. ✅ Follow the DEPLOYMENT.md guide for Play Store submission
