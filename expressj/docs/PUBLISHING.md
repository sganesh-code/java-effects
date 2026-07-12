# ExpressJ Library Publishing Guide

This guide describes how to configure, sign, and publish the `expressj` library to Maven Central using standard GPG signing and our automated GitHub Actions release workflow.

---

## 1. Prerequisites

Before publishing, you need:
1. **Sonatype Central Account:** Register and claim your namespace (e.g., `io.github.sganesh-code`) on the [Sonatype Central Portal](https://central.sonatype.org/).
2. **GPG Installed:** GnuPG installed locally (e.g., `brew install gnupg` on macOS).

---

## 2. GPG Key Generation and Export

Maven Central requires all publications to be signed by a valid GPG key.

### Step 2.1: Generate a Key Pair
Run the following command to generate a new RSA 2048-bit key pair:
```bash
gpg --gen-key
```
Follow the prompts (enter your name, email, and a secure **Passphrase**). Note down the passphrase; you will need it later.

### Step 2.2: Find Your Key ID
Run the following to list your keys and find the 8-character or 16-character key fingerprint:
```bash
gpg --list-keys
```
Example output:
```text
pub   rsa2048 2026-07-12 [SC] [expires: 2028-07-12]
      9ABCDEF123456789BCDEF123456789AB12345678
uid           [ultimate] ExpressJ Contributors <contributors@expressj.io>
sub   rsa2048 2026-07-12 [E] [expires: 2028-07-12]
```
The key fingerprint is `12345678` (the last 8 characters of the public key ID).

### Step 2.3: Publish Your Public Key
Maven Central verification servers must be able to resolve your public key. Publish it to a major keyserver:
```bash
gpg --keyserver keys.openpgp.org --send-keys 9ABCDEF123456789BCDEF123456789AB12345678
```

### Step 2.4: Export Your Private Key Block
For GitHub Actions in-memory signing, we need to export the armor-encoded ASCII block of your private subkey:
```bash
gpg --export-secret-keys --armor 9ABCDEF123456789BCDEF123456789AB12345678
```
Copy the entire block (including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----` lines).

---

## 3. Configure GitHub Repository Secrets

Under your GitHub repository settings, navigate to **Settings > Secrets and variables > Actions** and create the following **Repository Secrets**:

| Secret Name | Description |
| :--- | :--- |
| `MAVEN_USERNAME` | Your Sonatype Central portal username or user token ID. |
| `MAVEN_PASSWORD` | Your Sonatype Central portal password or user token password. |
| `GPG_PRIVATE_KEY` | The full ASCII-armored private key block exported in Step 2.4. |
| `GPG_PASSPHRASE` | The GPG passphrase you defined in Step 2.1. |

---

## 4. Release Process (Step-by-Step)

The Continuous Delivery (CD) release pipeline is fully automated and triggers on tag push.

### Step 4.1: Update Version
Update the library version inside `expressj/core/build.gradle.kts` (e.g., change `0.1.0-SNAPSHOT` to `0.1.0`):
```kotlin
version = "0.1.0"
```

### Step 4.2: Run Validation Checks
Ensure all tests and code coverage gates pass cleanly before releasing:
```bash
mise run test
mise run coverage-check
```

### Step 4.3: Commit and Tag
Commit your version change and create a git release tag:
```bash
git add expressj/core/build.gradle.kts
git commit -m "chore: release version 0.1.0"
git tag -a v0.1.0 -m "Release version 0.1.0"
```

### Step 4.4: Push to Trigger Release
Push the commit and tag to GitHub. This triggers the `.github/workflows/publish.yml` CD release workflow:
```bash
git push origin main --tags
```
The GHA CD pipeline will build the library, sources jar, javadoc jar, sign them using the in-memory keys, and deploy them directly to Maven Central.

---

## 5. Using the Published Dependency

Once published and synchronized (usually takes 15–30 minutes), users can add the library to their builds using the following coordinate:

### Gradle (Kotlin DSL)
```kotlin
implementation("io.github.sganesh-code:core:0.1.0")
```

### Maven
```xml
<dependency>
    <groupId>io.github.sganesh-code</groupId>
    <artifactId>core</artifactId>
    <version>0.1.0</version>
</dependency>
```
