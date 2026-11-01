<div align="center">

# ⌨️ OpenRidmik

**A fast, modern and open-source Bangla keyboard for Android**

Avro phonetic · Probhat · Emoji · Clipboard manager · Number pad · Themes

<br>

[![Release](https://img.shields.io/github/v/release/devfahim00/OpenRidmik?style=for-the-badge&logo=github&color=1A73E8)](https://github.com/devfahim00/OpenRidmik/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/devfahim00/OpenRidmik/total?style=for-the-badge&logo=android&color=3DDC84)](https://github.com/devfahim00/OpenRidmik/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/devfahim00/OpenRidmik/build.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=build)](https://github.com/devfahim00/OpenRidmik/actions)
[![License: MIT](https://img.shields.io/badge/license-MIT-F5C518?style=for-the-badge)](LICENSE)

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![No ads](https://img.shields.io/badge/ads-none-success?style=flat-square)
![No tracking](https://img.shields.io/badge/tracking-none-success?style=flat-square)
[![Stars](https://img.shields.io/github/stars/devfahim00/OpenRidmik?style=flat-square&logo=github)](https://github.com/devfahim00/OpenRidmik/stargazers)
[![Last commit](https://img.shields.io/github/last-commit/devfahim00/OpenRidmik?style=flat-square)](https://github.com/devfahim00/OpenRidmik/commits)

<br>

[**⬇️ Download latest APK**](https://github.com/devfahim00/OpenRidmik/releases/latest) &nbsp;•&nbsp;
[**💬 Telegram channel**](https://t.me/projectredfox) &nbsp;•&nbsp;
[**🐞 Report a bug**](https://github.com/devfahim00/OpenRidmik/issues)

</div>

---

## ✨ Highlights

| | |
|---|---|
| 🇧🇩 **Two Bangla layouts** | Avro phonetic (`ami` → আমি) and the fixed Probhat (প্রভাত) layout |
| 🧠 **Learns as you type** | On-device word frequencies and word pairs power suggestions and next-word prediction |
| 😀 **Full emoji panel** | Every emoji your device can draw, categorised, with recents and skin tones |
| 📋 **Clipboard manager** | History, pinning, one-tap paste; sensitive clips are skipped |
| 🔢 **Number pad** | Dedicated numpad with a Bangla digit row, one tap from the toolbar |
| 🎨 **Themes** | System, Light, Dark, AMOLED, Ocean, Forest, Rose, Lavender, Sunset or your own colours |
| 🔄 **Built-in updates** | Silent update check; a dot appears on the toolbar gear when a new release is out |
| 🔒 **Private by design** | No ads, no analytics, no accounts. Typing data never leaves your phone |

## 📲 Install

1. Download the latest **`OpenRidmik-vX.Y.Z.apk`** from the [Releases](https://github.com/devfahim00/OpenRidmik/releases/latest) page and install it.
2. Open **OpenRidmik** and tap **Enable OpenRidmik**, then switch it on in the system keyboard list.
3. Tap **Switch keyboard** and choose OpenRidmik.

> [!NOTE]
> Releases are signed with the same key, so you can always update in place. Debug builds from CI (`debug-dev`, `debug-latest`) are pre-releases for testing and use a different signature.

## 🕹️ Using the keyboard

| Gesture | What it does |
|---|---|
| **Tap 🌐** | Cycle **English → Avro → Probhat** |
| **Hold 🌐** | Open the system keyboard picker |
| **Hold a letter** | Type its alternate (for example a Bangla letter, or the English digit on the Bangla number row). The popup shows the alternate while you hold |
| **Hold `X` / `C` / `V`** | Cut / copy / paste (with no selection, cut and copy act on the whole field) |
| **Hold `,`** | Open the emoji panel |
| **Toolbar** | Emoji · Clipboard · Number pad · Theme · Settings |

### Phonetic cheat sheet (Avro)

| Type | Get | Type | Get |
|------|-----|------|-----|
| `a` | া / আ | `T` `D` `N` | ট ড ণ |
| `i` `I` | ি ই / ী ঈ | `S` `Sh` | শ ষ |
| `u` `U` | ু উ / ূ ঊ | `R` `Rh` | ড় ঢ় |
| `e` `O` | ে এ / ো ও | `ng` | ং |
| `oi` `OI` | ৈ ঐ / ৌ ঔ | `:` `^` | ঃ ঁ |
| `rri` | ঋ ৃ | `` ` `` | break a conjunct |
| `qq` | ঁ | `aa` | আ / া |

Examples: `ami` → আমি · `bangla` → বাংলা · `kkh` → ক্ষ · `baqqsh` → বাঁশ

From the symbols page, `$` types ৳, and `:` / `^` type ঃ / ঁ right after a Bangla letter. Hold the key for the plain character.

The complete rule table is plain text in [`avro_rules.txt`](app/src/main/assets/avro_rules.txt), so you can tweak it without touching any code.

## 🏗️ Build from source

```bash
git clone https://github.com/devfahim00/OpenRidmik.git
cd OpenRidmik
gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK (API 34), Gradle 8.9.

<details>
<summary><b>🚀 Releasing a new version (maintainers)</b></summary>

<br>

Signed releases are built by GitHub Actions when a `v*` tag is pushed:

```bash
git tag v0.0.4
git push origin v0.0.4
```

The workflow builds a signed `OpenRidmik-v0.0.4.apk` and publishes it as a GitHub Release. The version name comes from the tag and the version code from the workflow run number.

Required repository secrets:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

</details>

<details>
<summary><b>🗂️ Project layout</b></summary>

<br>

```
app/src/main/
├── assets/            avro_rules.txt, words_bn.txt, emoji.txt
└── java/com/devfahim00/openridmik/
    ├── OpenRidmikService.kt   input method service
    ├── KeyboardView.kt        canvas-drawn keyboard
    ├── Layouts.kt             Avro / Probhat / symbols / numpad
    ├── AvroEngine.kt          phonetic engine + dictionary suggester
    ├── UserDict.kt            on-device learning
    ├── EmojiData.kt           emoji catalogue and recents
    ├── ClipHistory.kt         clipboard history
    ├── Updater.kt             GitHub release update check
    └── SettingsActivity.kt    settings screen
```

</details>

## 🤝 Contributing

Issues and pull requests are welcome. Good places to start: improving the Avro rules, extending the word list, or adding themes. Please open an issue first for larger changes.

## 💬 Community

Updates, builds and discussion happen on Telegram: **[t.me/projectredfox](https://t.me/projectredfox)**

## 📄 License

Released under the [MIT License](LICENSE) © 2026 devfahim00.

OpenRidmik is an independent, original implementation. It contains no code, dictionaries or assets from any other keyboard app.

<div align="center">

<sub>Made with ❤️ for Bangla typists</sub>

</div>
