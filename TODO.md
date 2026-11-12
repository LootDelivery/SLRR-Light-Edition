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
- Lines beginning with `#` or `; are comments.
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

Current prototype approach:
- `Localization.java` attempts UTF-8 text reading through `FileInputStream`, `InputStreamReader`, and `BufferedReader`.
- These are standard Java I/O APIs, but their availability and behavior in the game's modified VM have **not yet been confirmed**.
- If the target VM cannot compile or run this path, investigate another supported stream/reader API or add a narrowly scoped native text-file reader. Do not repurpose the binary save-file reader without proving its behavior.

Useful repository references:
- `system/Scripts/io/File.class` — available SLRR file wrapper.
- `system/Scripts/util/src/Config.java` — current binary settings persistence.
- `sl/Scripts/game/src/GameLogic.java` — career-save file I/O.

## Current proof of concept

Branch: `feature/localization-ini-prototype`

Files added/changed:
- `sl/Scripts/game/src/Localization.java` — simple INI loader, section support, UTF-8 reader attempt, key lookup, and English fallback.
- `Languages/ru.ini` — Russian test strings for the main menu only.
- `sl/Scripts/game/src/MainMenu.java` — loads `Languages/ru.ini` before menu groups are built and routes the main-screen labels through the localization API.

Only the main menu's title and its primary menu labels are localized in this prototype. The rest of the game is intentionally untouched. The Russian file is currently hard-coded for the test; it is not yet a user-selectable language.

## Manual runtime test checklist

Run this branch with the target SLRR executable and inspect the game's compile/runtime error log if needed.

- [ ] Game compiles `Localization.java` and opens the main menu without errors.
- [ ] Main menu title displays `ГЛАВНОЕ МЕНЮ`.
- [ ] Primary menu labels display their Russian values.
- [ ] Rename or temporarily remove `Languages/ru.ini`: all main-screen labels must fall back to their original English strings.
- [ ] Remove one key from `ru.ini`: only that one label must fall back to English.
- [ ] Set one key to an empty value: that label must fall back to English.
- [ ] Add a malformed line: it must be ignored without a crash.
- [ ] Confirm Cyrillic characters render correctly with the TTF backend in the target EXE.
- [ ] Check menu width/alignment; Russian labels may need shorter wording or adjusted layout.
- [ ] Confirm the loader is quiet when the file is absent or invalid.

Do not mark the INI reader or UTF-8 path as verified until these tests have been run in-game.

## Follow-up work after the prototype

- [ ] Confirm whether `FileInputStream`, `InputStreamReader`, and `BufferedReader` are supported by each target SLRR build (especially 2.3.1).
- [ ] Confirm UTF-8 decoding, including a BOM test, with Cyrillic text.
- [ ] Add a persisted locale setting in `save/game/language.ini`, with a safe default of English.
- [ ] Add an Options language selector after runtime validation.
- [ ] Rebuild or refresh the current OSD when the language changes.
- [ ] Move all Java-created interface strings to stable localization IDs incrementally.
- [ ] Cover dialogs, tooltips, notifications, garage, inventory, shops, race menus, and gameplay messages.
- [ ] Identify text coming from RPK/resource data separately; Java string replacement will not localize those resources automatically.
- [ ] Define placeholder conventions for dynamic values (money, quantities, names) before translating long messages.
- [ ] Provide a language-pack template and a key-list/export workflow for translators.
