# 👋 MyGate Patches

Clean, ad-free experience, UI decluttering, and reliable push notifications via MicroG-RE for [MyGate](https://play.google.com/store/apps/details?id=com.mygate.user) (`com.mygate.user`).

Designed for seamless integration with **[Morphe Manager](https://morphe.software)**.

<a href="https://morphe.software/add-source?github=tokudai-vs/mygate-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

---

## 📱 Supported Versions

| App | Package Name | Compatible Versions |
|---|---|---|
| **MyGate** | `com.mygate.user` | `7.40.1`, `7.39.0`, `7.38.2` |

> [!NOTE]
> Tested and verified on MyGate version **7.40.1** (arm64-v8a).

---

## ✨ Features & Included Patches

### 1. 🛡️ MyGate ad-free & UI cleanup
Provides a streamlined, distraction-free MyGate interface:
- **Comprehensive Ad Suppression**: Short-circuits all `MygateAdLoader` entry points (`loadAd`, `loadGlobalMastheadAd`, `loadImageAd`, `getMyGateAdData`, `getMyGateMultiAdData`, `FloatingBannerAdHandler`) and disables native ad SDK flags on flats and user profiles.
- **Flutter Binary Ad Sinkhole**: Sinks ad network endpoints in `libapp.so` (`cmp.mygate.com`, `classifieds-be.mygate.com`, `metrics.kappa.mgmaglev.xyz`, etc.) to localhost (`127.0.0.1:9099`).
- **Flutter Feed Ad Neutralization**: Injects `USER_PREMIUM_PLAN` / `SOCIETY_PREMIUM_PLAN` into the Flutter MethodChannel payload to eliminate sponsored community notices and feed ads.
- **VIP Crown & Badge Removal**: Permanently suppresses VIP crown icons and promotional badges (`@id/l_plan_badge`, `@id/l_plan_badge_toolbar`, `KotlinUtils.Companion.M`) from the toolbar, flat selector, and home header.
- **Bottom Navigation Bar & Bloat Removal**: Removes bottom navigation bar and bloated Quick Action widgets (Community, Feed, Smart Devices, Marketplace, Settings).
- **Settings Screen Cleanup**: Completely collapses blank spaces, dummy margins, and useless dividers (`@id/clPlanInfo`, `@id/my_plans`, `@id/cl_tell_a_friend`, `@id/clManageCancelSubscription`).
- **Feature Limits Bypass**: Forces resident invite, frequent delivery, and cab limits to active (`freqDelivery`, `surpriseDelivery`, `freqCab`, `freqVisitingHelp`, `freqGuest`).

### 2. 🔔 MyGate GmsCore support
Enables 100% working push notifications, gate approval alerts, and e-Intercom on non-root, re-signed APKs using **MicroG-RE** (`app.morphe.android.gms` or `app.revanced.android.gms`):
- **Package Spoofing & Visibility**: Injects spoofed metadata (`SPOOFED_PACKAGE_NAME` = `com.mygate.user`, `SPOOFED_PACKAGE_SIGNATURE`) and Android 11+ package visibility queries for MicroG-RE.
- **C2DM Redirection**: Rewrites all GCM/C2DM permissions, broadcast receivers, and intent actions to the configured MicroG vendor.
- **Firebase HTTPS Certificate Spoofing**: Patches `X-Android-Cert` HTTP headers during Firebase Installations Service (FIS) token registration to match the original MyGate developer signing certificate (`AAB7E367980DA927FB146E862057CC87CD766987`), avoiding `FIS_AUTH_ERROR`.
- **Approval Notification Routing**: Directly routes visitor approval intents to `NotificationCampaignActivity` rather than `SplashActivity` so instant gate approvals work directly from heads-up alerts.
- **Notification Troubleshooting Diagnostics**: Patches `TestNotificationTroubleshootingViewModel` and `tokenNotFound` checks so the built-in MyGate Notification Troubleshooting screen reports all checks passing.

---

## 📲 How to Use in Morphe Manager

### Step 1: Add Patch Source
1. Open **Morphe Manager** on your Android device.
2. Tap **Settings** (bottom right) → **Sources**.
3. Tap the **`+`** (Add Source) button.
4. Enter the GitHub repository URL:
   ```
   https://github.com/tokudai-vs/mygate-patches
   ```
5. *(Optional)* If using the `dev` branch for latest pre-releases, enable the **Pre-release** toggle for this source.

### Step 2: Patch MyGate
1. Go to the **Patcher** tab.
2. Tap **Select an application** and choose **MyGate** (`com.mygate.user`).
   - If not installed, obtain the APK from a reputable source (e.g., APKMirror) matching supported versions (`7.40.1`).
3. Select your desired patches:
   - ✅ **MyGate ad-free & UI cleanup**
   - ✅ **MyGate GmsCore support**
4. Tap **Patch**.
5. Once patching finishes, tap **Install**.

> [!IMPORTANT]
> **Keystore Warning for Existing Installs**:
> If you already have a patched MyGate installed and are updating it, make sure you import the same custom keystore in Morphe Manager (**Settings** → **Signer** → **Import Keystore**). Otherwise, Android will block the update with an `INSTALL_FAILED_UPDATE_INCOMPATIBLE` signature mismatch.

---

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.1](https://github.com/tokudai-vs/mygate-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`dev`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**2 patches** across **1 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**MyGate**](PATCHES.md#mygate-commygateuser) | 2 | `7.38.2`, `7.39.0`, `7.40.1` | [`com.mygate.user`](https://play.google.com/store/apps/details?id=com.mygate.user) |
<!-- PATCHES_END -->

---

## 🛠️ Building Locally

To build the `.mpp` patch bundle locally:
```bash
git clone https://github.com/tokudai-vs/mygate-patches.git
cd mygate-patches
./gradlew buildAndroid
```
The compiled patch bundle will be generated at:
```
patches/build/libs/patches-<version>.mpp
```

---

## 📜 License

Licensed under the [GNU General Public License v3.0](LICENSE).
