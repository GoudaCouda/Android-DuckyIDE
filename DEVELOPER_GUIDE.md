# DuckyIDE Developer Guide

This document provides a technical overview of the **DuckyIDE** project, a native Android application designed to turn a rooted device into a powerful USB HID injection tool (BadUSB) and USB Gadget manager (USB Arsenal).

## 1. Project Architecture

The application is built using standard Android Java (native) to ensure minimal overhead and maximum performance on low-end devices. It relies heavily on **Root Access (`su`)** to interact with the Linux Kernel's USB Gadget ConfigFS.

### Core Components

*   **`MainActivity.java`**:
    *   **Role:** The primary entry point.
    *   **Functions:**
        *   Provides the Code Editor for Ducky Script.
        *   Handles File I/O (Load/Save scripts).
        *   Initiates the **Injection** workflow.
        *   Checks for Root access on startup.

*   **`UsbArsenalActivity.java`**:
    *   **Role:** The UI for managing USB Gadget functions (Nethunter-style USB Arsenal).
    *   **Functions:**
        *   Allows enabling/disabling specific USB functions: `HID` (Keyboard/Mouse), `Mass Storage`, `RNDIS` (Network), `MTP`, and `ADB`.
        *   Manages **Device Identity** (Vendor ID / Product ID) spoofing.
        *   Handles **Disk Image Mounting** (`.img`/`.iso` files) for Mass Storage.
        *   Persists user settings (VID/PID) via `SharedPreferences`.

*   **`DuckyParser.java`**:
    *   **Role:** The compiler/translator.
    *   **Functions:**
        *   Parses Ducky Script syntax (e.g., `STRING`, `GUI r`, `DELAY`).
        *   Translates commands into raw shell scripts that write byte reports to the HID device file (`/dev/hidg0`).
    *   **Critical Implementation Detail:**
        *   Uses **7-byte HID Reports** (1 Modifier + 1 Reserved + 1 Key + 4 Padding).
        *   Uses `echo -ne` for writing binary data.
        *   Optimizes execution by keeping the file descriptor (`fd 3`) open for the duration of the script to prevent driver resets between keystrokes.

*   **`UsbController.java`**:
    *   **Role:** The backend logic for USB Gadget manipulation.
    *   **Functions:**
        *   Interacts with Android's `ConfigFS` subsystem at `/config/usb_gadget/g1`.
        *   **Setup Logic:**
            1.  **Reset:** Disables UDC and sets `sys.usb.config` to `none` or `adb`.
            2.  **Cleanup:** Manually unlinks old functions from `configs/b.1`.
            3.  **Link:** Manually symlinks desired functions (`mass_storage`, `hid.0`, etc.) into `configs/b.1`.
            4.  **Identity:** Writes custom VID/PID to `idVendor`/`idProduct`.
            5.  **Bounce:** Toggles the UDC (USB Device Controller) to force the host PC to re-enumerate the device.
    *   **Mounting:** Writes image paths to the `lun.0/file` node of the Mass Storage gadget.

*   **`RootShell.java`**:
    *   **Role:** A helper utility for executing shell commands.
    *   **Functions:**
        *   `exec(String cmd)`: Runs a command and returns output (blocking).
        *   `runScriptFile(String path)`: Executes a standalone shell script file using `su -c "sh <path>"`.

## 2. Key Workflows

### The Injection Process
1.  **User** types script in `MainActivity` and clicks **INJECT**.
2.  **`DuckyParser`** validates the syntax and generates a shell script string.
    *   *Example Output:*
        ```bash
        #!/bin/sh
        HID_DEV=/dev/hidg0
        exec 3> $HID_DEV
        printf "\x00\x00\x04\x00\x00\x00\x00" >&3  # Press 'a'
        sleep 0.02
        printf "\x00\x00\x00\x00\x00\x00\x00" >&3  # Release
        exec 3>&-
        ```
3.  **`MainActivity`** writes this string to a temporary file: `cache/payload.sh`.
4.  **`RootShell`** executes this file as root.
5.  **Kernel** receives the writes to `/dev/hidg0` and sends keystrokes to the victim PC.

### The USB Configuration Process
1.  **User** selects functions (e.g., HID + Storage) in `UsbArsenalActivity`.
2.  **`UsbController.setUsbFunctions`** is called.
3.  **Step 1 (Clean):** It waits for the USB stack to stabilize and removes old symlinks.
4.  **Step 2 (Link):** It finds the correct gadget names (e.g., `mass_storage.gs6` vs `mass_storage.0`) and links them.
    *   *Order matters:* Storage -> RNDIS -> Mouse -> Keyboard.
5.  **Step 3 (Bounce):** It writes an empty string to `UDC`, waits, and writes the controller name back. This physically disconnects and reconnects the USB device logically.

## 3. Troubleshooting & Maintenance

*   **Driver Disconnects during Injection:**
    *   This usually means the HID file descriptor was closed and reopened too quickly. Ensure `DuckyParser` uses `exec 3> $HID_DEV` redirection.
    *   Check that the Report Size matches the kernel driver. Currently set to **8 bytes** (via `echo -ne` padding).

*   **Mass Storage Not Visible:**
    *   The kernel often cannot read `/sdcard/`. The app uses a helper `resolvePhysicalPath` to translate this to `/data/media/0/`, which is the actual block device path.
    *   Ensure the image file is not mounted by the OS itself.

*   **ADB Disappears:**
    *   The app attempts to preserve ADB by setting it as the "Base Config". If ADB breaks, toggling the "Enable ADB" switch in Arsenal and re-applying usually fixes it.

## 4. Recent Changes (Parser Reversion)
*   **Date:** December 2025
*   **Change:** Reverted `printf` back to `echo -ne` and adjusted HID report padding.
*   **Reasoning:** The optimization to use `printf` or send strictly 8 bytes caused compatibility issues with certain Android shells/kernels, leading to "stuck" modifier keys (like Ctrl). The parser now strictly follows the established 7-byte payload format that was verified to work.
