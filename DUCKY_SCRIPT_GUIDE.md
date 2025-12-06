# Ducky Script Language Guide for DuckyIDE

DuckyIDE supports a robust subset of the Ducky Script 1.0 language, optimized for Android HID injection.

## Core Commands

### `STRING`
Types a sequence of characters.
```ducky
STRING Hello World!
STRING https://example.com
```

### `DELAY`
Pauses execution for a specific duration in milliseconds.
```ducky
DELAY 500   REM Wait 0.5 seconds
DELAY 2000  REM Wait 2 seconds
```

### `REM`
Comments. Lines starting with `REM` are ignored.
```ducky
REM This is a comment
```

## Key Commands
Presses a single special key.

*   `ENTER`
*   `TAB`
*   `ESCAPE` / `ESC`
*   `BACKSPACE`
*   `SPACE`
*   `CAPSLOCK`
*   `DELETE`
*   `INSERT`
*   `HOME` / `END`
*   `PAGEUP` / `PAGEDOWN`
*   `UP` / `DOWN` / `LEFT` / `RIGHT` (Arrow keys)
*   `F1` - `F12`
*   `PRINTSCREEN`
*   `SCROLLLOCK`
*   `PAUSE` / `BREAK`
*   `MENU` / `APP`

## Modifiers & Combinations
You can combine modifiers with keys or other commands.

### Single Modifiers
*   `GUI` / `WINDOWS` / `COMMAND`
*   `CTRL` / `CONTROL`
*   `ALT`
*   `SHIFT`

### Usage
```ducky
GUI r           REM Press Windows+R
CTRL c          REM Press Ctrl+C
ALT F4          REM Press Alt+F4
SHIFT a         REM Types 'A' (Same as STRING A)
CTRL-ALT DELETE REM Combo
```

### Supported Combinations
*   `CTRL-ALT`
*   `CTRL-SHIFT`
*   `ALT-SHIFT`
*   `COMMAND-CTRL`
*   `COMMAND-CTRL-SHIFT`
*   `COMMAND-OPTION` (Mac)
*   `COMMAND-OPTION-SHIFT` (Mac)

## Advanced Features

### `DEFINE`
Map a custom key name to a specific HID Modifier and Keycode (in Hex).
Useful for mapping non-standard keys or international layouts manually.

**Syntax:** `DEFINE [NAME] [MODIFIER_HEX] [KEY_HEX]`

```ducky
REM Define a custom key "MY_KEY"
REM Mod: 0x00 (None), Key: 0x04 ('a')
DEFINE MY_KEY 0x00 0x04

REM Use it
MY_KEY
```

## Example Scripts

### Windows "Run" Injection
```ducky
REM Open Run Dialog
GUI r
DELAY 500
STRING notepad.exe
ENTER
DELAY 1000
STRING Hello from DuckyIDE!
```

### Browser Prank
```ducky
GUI r
DELAY 500
STRING https://www.youtube.com/watch?v=dQw4w9WgXcQ
ENTER
```
