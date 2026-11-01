# OpenRidmik

A modern, Gboard-style Bangla keyboard for Android (`com.devfahim00.openridmik`).

## Features

- **Two Bangla layouts**: Avro phonetic and the fixed **Probhat** (প্রভাত) layout (switch from the toolbar or settings)
- Press and hold **X = cut, C = copy, V = paste**
- **Clipboard manager** (history, pin, delete) opened from the 📋 toolbar button
- **Themes**: system, light, dark, AMOLED, ocean, forest, rose, lavender, sunset and a fully custom colour theme
- Dedicated **emoji key** with every emoji your device can draw, grouped by category, recents and skin tones
- Avro-style **phonetic Bangla** typing (`ami` -> আমি, `bangla` -> বাংলা, conjuncts like `kkh` -> ক্ষ)
- Live composing text with a 3-slot suggestion bar and a small built-in word list
- English QWERTY with auto-capitalisation, shift / caps lock
- Number row (Bangla digits optional), symbols pages, emoji panel
- Gboard-like rounded keys, key previews, key repeat on backspace, light/dark theme
- Long-press a letter to type its Bangla letter directly (except X/C/V, which cut/copy/paste); long-press the globe key for the keyboard picker
- Input-type aware (numeric/phone fields open symbols, email/URL/password force English)
- Settings screen with setup shortcuts and preferences

## Phonetic cheat sheet

| Type | Get | Type | Get |
|------|-----|------|-----|
| `a` | া / আ | `T` `D` `N` | ট ড ণ |
| `i` `I` | ি ই / ী ঈ | `S` `Sh` | শ ষ |
| `u` `U` | ু উ / ূ ঊ | `R` `Rh` | ড় ঢ় |
| `e` `O` | ে এ / ো ও | `ng` | ং |
| `oi` `OI` | ৈ ঐ / ৌ ঔ | `:` `^` | ঃ ঁ |
| `rri` | ঋ ৃ | `` ` `` | break conjunct |

The full rule table lives in [`app/src/main/assets/avro_rules.txt`](app/src/main/assets/avro_rules.txt) and can be edited without touching any code.

## Build

CI builds a debug APK on every push to `main` and `dev` (see `.github/workflows/build.yml`) and publishes it under the
**debug-latest** (main) or **debug-dev** (dev) pre-release. Locally:

```
gradle assembleDebug
```

## Notes

This is an independent, original implementation. It does not contain code, dictionaries or assets from any other keyboard app.
