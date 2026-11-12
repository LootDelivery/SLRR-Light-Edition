# TODO — Localization and language switching

## Goal

Add a game-wide localization layer to SLRR-Light-Edition. Language packs must live in external, human-editable files so translators can add or update a language without editing Java source files.

The localization system must be safe for partially translated language packs: any missing, empty, or unusable translation must show the original English string supplied by the Java caller.

This document records the intended architecture and the current proof-of-concept state.

## Design principles

1. **Stable string IDs.** Java code requests strings by IDs such as `menu.main.title`, not by translated text or by searching for the English phrase.
2. **Original text is the fallback.** Every call passes its original English text: `Localization.get("menu.main.title", "MAIN MENU")`. The original text remains visible when a key is absent.
3. **External language files.** Translation files are kept outside compiled Java classes, under `Languages/`.
4. **Partial translations are valid.** A translation file may contain only a few keys. Missing keys do not blank out the interface.
5. **Bad entries are ignored.** Empty values and malformed lines must not break the game.
6. **Language changes are presentation-only.** They must not alter career saves, vehicle data, resource IDs, gameplay logic, or control bindings.
7. **Do not assume standard Java APIs.** SLRR uses a modified Java VM with limitations. Any Java I/O, collections, or encoding API used by the loader must be verified in the target game build.

## Intended layout

```text
Languages/
    en.ini
    ru.ini
    de.ini
save/
    game/
        language.ini
sl/
    Scripts/
        game/
            src/
                Localization.java
                MainMenu.java
```

- `Languages/<locale>.ini` contains translations.
- `save/game/language.ini` will eventually store the selected locale, for example `language=ru`.
- English is the built-in source language. An `en.ini` file is optional and should not be required for the fallback to work.

## INI format

The prototype uses a simple UTF-8 INI subset:

```ini
# Full-line comments may start with # or ;
[menu.main]
title=ГЛАВНОЕ МЕНЮ
new_career=НОВАЯ КАРЬЕРА
load_career=ЗАГРУЗИТЬ КАРЬЕРУ
```

Rules:
- UTF-8 text; test both with and without a BOM before declaring encoding support complete.
- Blank lines are ignored.
- Lines beginning with `#` or `;` are comments.
- Section names are prepended to keys with a dot: `[menu.main]` + `title=` becomes `menu.main.title`.
- The first `=` separates key from value; additional equals signs belong to the value.
- Whitespace around keys and values is trimmed in the current prototype.
- Malformed lines and empty keys are ignored.
- A missing key or an empty translation uses the Java fallback string.
- If a key appears more than once, the last occurrence wins.
- Inline comments, escape sequences, multiline values, and plural rules are not part of the first prototype.

## Java API

The intended call site is:

```java
osd.createHeader(
    Localization.get( "menu.main.title", "MAIN MENU" )
);
```

The public API should remain small:

- `Localization.load(fileName)` — load a language file and clear any previous table.
- `Localization.get(key, fallback)` — return a non-empty translation or the caller's original string.
- `Localization.isLoaded()` — expose whether the most recent file load completed successfully.

The implementation may change internally as engine compatibility is tested, but callers should not need to change.

## Runtime architecture and language switching

The final implementation should:

1. Determine the selected locale from a small settings file.
2. Load that locale's language pack once during startup.
3. Fall back to English if the setting is missing, the language file does not exist, or loading fails.
4. Build all OSD strings through `Localization.get()`.
5. When the player changes language in Options, load the new pack and rebuild or explicitly refresh affected OSD objects. Calling `changeMode()` alone is not sufficient for objects whose text was already created.
6. Save the selected locale separately from career saves.
7. Avoid changing the language selection during a career load/save operation.

A future Options entry named `LANGUAGE` should show a list of available language packs. Do not add this menu entry until language-file loading and OSD refresh are verified.

## Fallback and error-handling requirements

- No language file: use English.
- File cannot be opened or decoded: use English.
- Missing key: use the specific fallback string at the Java call site.
- Empty translation: use the fallback string.
- Malformed line: ignore that line.
- Duplicate key: last valid entry wins.
- Partial language pack: translated entries appear in the selected language; all other entries remain English.
- No logging should be required for normal fallback operation. A failed load must not flood the runtime log.

One caveat: if a referenced Java I/O class is absent from the SLRR VM, the script may fail at compile/link time before ordinary exception fallback can run. This is why the I/O probe must be verified against the actual game executable.

## File I/O research and current decision

The repository contains `system/Scripts/io/File.class`. Inspection of its exposed method names found:

- `open`, `close`
- `write` overloads
- `readInt`, `readFloat`, `readResID`, `readString`
- `delete`, `copy`, `move`, `exists`

No `readLine()` or raw-byte `read()` method is exposed by this SLRR `java.io.File` wrapper.

The existing `system/Scripts/util/src/Config.java` reads its options file as a typed binary sequence (`readInt()`, `readFloat()`, etc.) and writes the same way. Career save code uses the matching typed file methods as well. This is not evidence that `File.readString()` can read an ordinary plain-text INI file; do not treat it as a text-file reader without a runtime test.

Current finding (runtime report, 2026-11-12):
- The game's compiler reports `catch statements not supported yet`. Exception-based error handling must not be used in game scripts.
- The initial prototype also tried `FileInputStream`, `InputStreamReader`, and `BufferedReader`. This approach is rejected: those standard Java stream APIs have not been verified in SLRR and must not be assumed to exist.
- The exposed SLRR `java.io.File` wrapper has typed/binary string operations but no exposed raw-byte or raw-line reader. Its `readString()` is used by existing code to read strings written by `File.write(String)`; this does not establish compatibility with a plain-text INI.
- Therefore, a safe plain-text INI reader is **not yet implemented**. The next proper route is a narrowly scoped native helper that reads a UTF-8 text file and exposes its contents to Java, or another engine-supported raw-text API discovered and tested first.
- Do not call `File.readString()` on an arbitrary plain-text INI until its exact on-disk format is understood; the file may be interpreted as a serialized string and fail.
- Do not add try/catch, try-with-resources, FileInputStream, InputStreamReader, or BufferedReader back into SLRR Java source unless the exact target build has independently demonstrated support.

Useful repository references:
- `system/Scripts/io/File.class` — available SLRR file wrapper.
- `system/Scripts/util/src/Config.java` — current binary settings persistence.
- `sl/Scripts/game/src/GameLogic.java` — career-save file I/O.

## Current proof of concept

Branch: `feature/localization-ini-prototype`

Files added/changed:
- `sl/Scripts/game/src/Localization.java` — compile-safe localization API smoke test. It currently returns a small set of hard-coded Russian strings and otherwise returns the supplied English fallback. It deliberately contains no file I/O and no exception handling.
- `Languages/ru.ini` — intended external language-pack format and sample strings. **The current Java smoke test does not read this file yet.**
- `sl/Scripts/game/src/MainMenu.java` — routes the main-screen title and primary menu labels through `Localization.get()`, and initializes the temporary Russian smoke-test strings before OSD groups are built.

The current test only verifies that MainMenu calls the localization layer, that the fallback API compiles in SLRR, and that the TTF backend can display Cyrillic. It does **not** verify INI reading. The rest of the game is intentionally untouched.

## Manual runtime test checklist

Run this branch with the target SLRR executable and inspect the game's compile/runtime error log if needed.

- [ ] Game compiles the current `Localization.java` smoke test and opens the main menu without localization-related errors.
- [ ] Main menu title displays `ГЛАВНОЕ МЕНЮ`.
- [ ] Primary menu labels display their temporary hard-coded Russian values.
- [ ] Unknown keys return the original English fallback.
- [ ] Confirm Cyrillic characters render correctly with the TTF backend in the target EXE.
- [ ] Check menu width/alignment; Russian labels may need shorter wording or adjusted layout.
- [ ] Separately record whether the pre-existing `java.game.cars.Mirror` class-resolution errors persist with the compile-safe smoke test.

Do not mark the INI reader, external-file fallback, or UTF-8 file decoding as verified until a native/raw-text reader exists and these tests have been run in-game.

## Follow-up work after the prototype

- [ ] Design and implement a minimal native raw-text file reader for the target SLRR executable; keep it separate from the TTF drawing hooks where possible.
- [ ] Expose the native reader through a verified SLRR Java/native bridge or another engine-supported API.
- [ ] Read a small UTF-8 INI file from disk; test with and without a BOM.
- [ ] Confirm behaviour on each target SLRR build (especially 2.3.1).
- [ ] Add a persisted locale setting in `save/game/language.ini`, with a safe default of English.
- [ ] Add an Options language selector after runtime validation.
- [ ] Rebuild or refresh the current OSD when the language changes.
- [ ] Move all Java-created interface strings to stable localization IDs incrementally.
- [ ] Cover dialogs, tooltips, notifications, garage, inventory, shops, race menus, and gameplay messages.
- [ ] Identify text coming from RPK/resource data separately; Java string replacement will not localize those resources automatically.
- [ ] Define placeholder conventions for dynamic values (money, quantities, names) before translating long messages.
- [ ] Provide a language-pack template and a key-list/export workflow for translators.


## Separate startup diagnostic: Mirror class resolution

The supplied runtime log also reports `java.game.cars.Mirror` missing and a field referencing `java.game.cars.Nonus_L_mirror`. This is not referenced by the localization API or the MainMenu strings changed in this branch.

The repository's visible `parts/scripts/bodypart/src/Mirror.java` declares package `java.game.parts.bodypart`, not `java.game.cars`. These are different fully qualified class names; the existing body-part `Mirror` cannot satisfy a request for `java.game.cars.Mirror`.

The source-tree diff for the localization branch does not introduce any `Mirror` reference. Treat this as a separate class/resource compatibility issue until it is reproduced with the localization changes removed. Do not add a dummy `java.game.cars.Mirror` class as a speculative workaround: first locate the compiled class or resource that requests that exact package name.
