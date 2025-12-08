# DuckyIDE for Android

**DuckyIDE** is a native Android application that turns a rooted device into a powerful USB attack platform. It allows you to write, compile, and inject Ducky Script payloads over USB HID, as well as manage complex USB gadget compositions (Mass Storage, RNDIS, ADB).

## Features

*   **Ducky Script Editor:** Syntax highlighting and fast compilation.
*   **LED Status Monitor:** Real-time UI indicators for Caps Lock, Num Lock, and Scroll Lock status, reading directly from the HID interface.
*   **Enhanced Logging:**
    *   Timestamped logs for precise debugging.
    *   Scrollable log view with history.
    *   Verbose status updates for HID connection and LED monitoring.
*   **USB Arsenal:**
    *   **Composite Devices:** Run ADB, Mass Storage, and HID simultaneously.
    *   **Total Control:** Bypasses Android's USB limitations by manually orchestrating the ConfigFS gadget.
    *   **ISO Mounting:** Mount raw disk images as USB Mass Storage (CD-ROM or Flash Drive).
*   **High-Performance Injection:**
    *   Uses a custom **Base64 Stream Pipelining** engine.
    *   Compiles Ducky Script into a raw binary stream, encoded as Base64, and piped directly to the HID driver (`/dev/hidg0`) using `dd`.
    *   Zero process overhead per keystroke—blazing fast execution.
*   **Compatibility:**
    *   Implements **Dynamic Function Enumeration** (`f1`, `f2`...) to support strict kernels (Samsung OneUI, Pixel Stock).
    *   VID/PID spoofing.

## Requirements

*   **Root Access:** Essential for modifying USB gadget configurations and writing to `/dev/hidg0`.
*   **Kernel Support:** The device kernel must support **ConfigFS** and the relevant USB functions (`hid`, `mass_storage`, etc.). Most modern kernels (Android 10+) support this, especially Nethunter kernels.

## Installation

1.  Build the project using Android Studio or `./gradlew assembleDebug`.
2.  Install the APK on your rooted device.
3.  Grant Root permissions when prompted.

## Usage

### 1. Writing Scripts
*   Open the editor.
*   Write standard Ducky Script 1.0 code (e.g., `DELAY 1000`, `STRING Hello World`, `GUI r`).
*   Tap **RUN** to inject immediately.

### 2. USB Arsenal
> **⚠️ WORK IN PROGRESS:** The USB Arsenal feature is currently experimental and varies heavily by kernel. If you experience stability issues or if gadgets fail to enumerate, we highly recommend setting up your ConfigFS environment using the standard **Kali Nethunter** app or boot scripts first, then using DuckyIDE purely for script injection.

*   Navigate to the "USB Arsenal" tab.
*   Select your desired USB interfaces (e.g., "ADB + Mass Storage + HID").
*   Tap **APPLY CONFIG**.
    *   *Note:* This will momentarily disconnect ADB as the USB stack is reset.
*   To mount an image, select a file from the dropdown (files must be in `/sdcard/`) and tap **MOUNT**.

## Technical Architecture

For a deep dive into the driver interactions, gadget orchestration, and injection strategies, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).