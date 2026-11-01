# OpenRidmik

A modern, Gboard-style Bangla keyboard for Android (`com.devfahim00.openridmik`).

## Features

- Avro-style **phonetic Bangla** typing (`ami` -> আমি, `bangla` -> বাংলা, conjuncts like `kkh` -> ক্ষ)
- Live composing text with a 3-slot suggestion bar and a small built-in word list
- English QWERTY with auto-capitalisation, shift / caps lock
- Number row (Bangla digits optional), symbols pages, emoji panel
- Gboard-like rounded keys, key previews, key repeat on backspace, light/dark theme
- Long-press a letter to type its Bangla letter directly; long-press `,` for emoji; long-press the globe key for the keyboard picker
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

CI builds a debug APK on every push to `main` (see `.github/workflows/build.yml`) and publishes it under the
**debug-latest** pre-release. Locally:

```
gradle assembleDebug
```

## Notes

This is an independent, original implementation. It does not contain code, dictionaries or assets from any other keyboard app.
