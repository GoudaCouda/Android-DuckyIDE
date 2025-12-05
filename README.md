Written by Gemini 

# DuckyIDE - Android HID Injector

A native Android application that allows you to write, save, and execute Ducky Scripts directly from your device.

## Features
- **Hacker Theme:** Dark mode with green terminal font.
- **Ducky Script Support:** Compiles standard Ducky Script (STRING, DELAY, GUI, ENTER) into HID injection commands.
- **Root Injection:** Uses `su` access to write directly to `/dev/hidg0`.
- **Efficiency:** Built in native Java to ensure minimal overhead on low-end devices.

## Prerequisites
1.  **Rooted Android Device:** The app requires `su` binary availability.
2.  **HID Kernel Support:** Your device kernel must have HID Gadget support enabled and available at `/dev/hidg0`. This is common in Nethunter kernels.

## Build Instructions
1.  Open this folder in **Android Studio**.
2.  Sync Gradle.
3.  Build and Install APK to your device.

## Usage
1.  Grant Root permissions when prompted.
2.  Type your script in the editor:
    ```
    DELAY 1000
    GUI r
    DELAY 500
    STRING notepad
    ENTER
    DELAY 1000
    STRING Hello from DuckyIDE!
    ```
3.  Click **INJECT**.

## Keymap Note
This prototype supports basic alphanumeric keys (a-z, 0-9), Space, Enter, and GUI. Complex modifiers or special symbols may need to be added to `DuckyParser.java`.
