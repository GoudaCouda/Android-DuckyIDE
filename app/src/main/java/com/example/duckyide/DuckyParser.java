package com.example.duckyide;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuckyParser {

    // Structure to hold Key + Modifier for a single character
    private static class HidCode {
        byte key;
        byte mod;
        HidCode(int key, int mod) { this.key = (byte)key; this.mod = (byte)mod; }
        HidCode(int key) { this(key, 0); }
    }

    private static final Map<Character, HidCode> ASCII_MAP = new HashMap<>();
    private static final Map<String, Byte> KEY_COMMANDS = new HashMap<>();
    private static final Map<String, Byte> MODIFIERS = new HashMap<>();
    
    // HID Modifier Constants
    private static final byte MOD_CTRL  = 0x01;
    private static final byte MOD_SHIFT = 0x02;
    private static final byte MOD_ALT   = 0x04;
    private static final byte MOD_GUI   = 0x08; // Win/Command

    static {
        // --- ASCII MAP (Char -> Key + Default Mod) ---
        
        // a-z
        for (int i = 0; i < 26; i++) {
            ASCII_MAP.put((char)('a' + i), new HidCode(0x04 + i));
            ASCII_MAP.put((char)('A' + i), new HidCode(0x04 + i, MOD_SHIFT));
        }
        // 1-9, 0
        // 1 starts at 0x1E. 0 is 0x27.
        for (int i = 1; i <= 9; i++) {
            ASCII_MAP.put((char)('0' + i), new HidCode(0x1E + i - 1));
        }
        ASCII_MAP.put('0', new HidCode(0x27));

        // Symbols (US Layout)
        mapSymbol('!', '1', MOD_SHIFT);
        mapSymbol('@', '2', MOD_SHIFT);
        mapSymbol('#', '3', MOD_SHIFT);
        mapSymbol('$', '4', MOD_SHIFT);
        mapSymbol('%', '5', MOD_SHIFT);
        mapSymbol('^', '6', MOD_SHIFT);
        mapSymbol('&', '7', MOD_SHIFT);
        mapSymbol('*', '8', MOD_SHIFT);
        mapSymbol('(', '9', MOD_SHIFT);
        mapSymbol(')', '0', MOD_SHIFT);

        ASCII_MAP.put(' ', new HidCode(0x2C)); // Space
        ASCII_MAP.put('\n', new HidCode(0x28)); // Enter
        ASCII_MAP.put('\t', new HidCode(0x2B)); // Tab

        ASCII_MAP.put('-', new HidCode(0x2D));
        ASCII_MAP.put('_', new HidCode(0x2D, MOD_SHIFT));
        ASCII_MAP.put('=', new HidCode(0x2E));
        ASCII_MAP.put('+', new HidCode(0x2E, MOD_SHIFT));
        ASCII_MAP.put('[', new HidCode(0x2F));
        ASCII_MAP.put('{', new HidCode(0x2F, MOD_SHIFT));
        ASCII_MAP.put(']', new HidCode(0x30));
        ASCII_MAP.put('}', new HidCode(0x30, MOD_SHIFT));
        ASCII_MAP.put('\\', new HidCode(0x31));
        ASCII_MAP.put('|', new HidCode(0x31, MOD_SHIFT));
        ASCII_MAP.put(';', new HidCode(0x33));
        ASCII_MAP.put(':', new HidCode(0x33, MOD_SHIFT));
        ASCII_MAP.put((char)0x27, new HidCode(0x34)); // Single Quote
        ASCII_MAP.put((char)0x22, new HidCode(0x34, MOD_SHIFT)); // Double Quote
        ASCII_MAP.put('`', new HidCode(0x35));
        ASCII_MAP.put('~', new HidCode(0x35, MOD_SHIFT));
        ASCII_MAP.put(',', new HidCode(0x36));
        ASCII_MAP.put('<', new HidCode(0x36, MOD_SHIFT));
        ASCII_MAP.put('.', new HidCode(0x37));
        ASCII_MAP.put('>', new HidCode(0x37, MOD_SHIFT));
        ASCII_MAP.put('/', new HidCode(0x38));
        ASCII_MAP.put('?', new HidCode(0x38, MOD_SHIFT));

        // --- KEY COMMANDS (Keywords -> Key Code) ---
        KEY_COMMANDS.put("ENTER", (byte)0x28);
        KEY_COMMANDS.put("ESCAPE", (byte)0x29);
        KEY_COMMANDS.put("ESC", (byte)0x29);
        KEY_COMMANDS.put("BACKSPACE", (byte)0x2A);
        KEY_COMMANDS.put("TAB", (byte)0x2B);
        KEY_COMMANDS.put("SPACE", (byte)0x2C);
        KEY_COMMANDS.put("CAPSLOCK", (byte)0x39);
        KEY_COMMANDS.put("PRINTSCREEN", (byte)0x46);
        KEY_COMMANDS.put("SCROLLLOCK", (byte)0x47);
        KEY_COMMANDS.put("PAUSE", (byte)0x48);
        KEY_COMMANDS.put("BREAK", (byte)0x48);
        KEY_COMMANDS.put("INSERT", (byte)0x49);
        KEY_COMMANDS.put("HOME", (byte)0x4A);
        KEY_COMMANDS.put("PAGEUP", (byte)0x4B);
        KEY_COMMANDS.put("DELETE", (byte)0x4C);
        KEY_COMMANDS.put("END", (byte)0x4D);
        KEY_COMMANDS.put("PAGEDOWN", (byte)0x4E);
        KEY_COMMANDS.put("RIGHTARROW", (byte)0x4F);
        KEY_COMMANDS.put("RIGHT", (byte)0x4F);
        KEY_COMMANDS.put("LEFTARROW", (byte)0x50);
        KEY_COMMANDS.put("LEFT", (byte)0x50);
        KEY_COMMANDS.put("DOWNARROW", (byte)0x51);
        KEY_COMMANDS.put("DOWN", (byte)0x51);
        KEY_COMMANDS.put("UPARROW", (byte)0x52);
        KEY_COMMANDS.put("UP", (byte)0x52);
        KEY_COMMANDS.put("MENU", (byte)0x65);
        KEY_COMMANDS.put("APP", (byte)0x65);

        for (int i = 1; i <= 12; i++) {
            KEY_COMMANDS.put("F" + i, (byte)(0x3A + i - 1));
        }

        // --- MODIFIERS (Keywords -> Mod Bitmap) ---
        MODIFIERS.put("CTRL", MOD_CTRL);
        MODIFIERS.put("CONTROL", MOD_CTRL);
        MODIFIERS.put("SHIFT", MOD_SHIFT);
        MODIFIERS.put("ALT", MOD_ALT);
        MODIFIERS.put("GUI", MOD_GUI);
        MODIFIERS.put("WINDOWS", MOD_GUI);
        MODIFIERS.put("COMMAND", MOD_GUI);

        // Combos
        MODIFIERS.put("CTRL-ALT", (byte)(MOD_CTRL | MOD_ALT));
        MODIFIERS.put("CTRL-SHIFT", (byte)(MOD_CTRL | MOD_SHIFT));
        MODIFIERS.put("ALT-SHIFT", (byte)(MOD_ALT | MOD_SHIFT));
        MODIFIERS.put("COMMAND-CTRL", (byte)(MOD_GUI | MOD_CTRL));
        MODIFIERS.put("COMMAND-CTRL-SHIFT", (byte)(MOD_GUI | MOD_CTRL | MOD_SHIFT));
        MODIFIERS.put("COMMAND-OPTION", (byte)(MOD_GUI | MOD_ALT)); // Option is Alt
        MODIFIERS.put("COMMAND-OPTION-SHIFT", (byte)(MOD_GUI | MOD_ALT | MOD_SHIFT));
    }

    private static void mapSymbol(char symbol, char baseChar, byte mod) {
        if (ASCII_MAP.containsKey(baseChar)) {
            ASCII_MAP.put(symbol, new HidCode(ASCII_MAP.get(baseChar).key, mod));
        }
    }

    public static class ParseResult {
        public String shellScript;
        public List<String> errors;
        
        public ParseResult(String shellScript, List<String> errors) {
            this.shellScript = shellScript;
            this.errors = errors;
        }
    }
    
    private static class CustomDef {
        byte mod;
        byte key;
        CustomDef(byte mod, byte key) { this.mod = mod; this.key = key; }
    }

    public static ParseResult parseToShell(String duckyScript) {
        StringBuilder shellScript = new StringBuilder();
        List<String> errors = new ArrayList<>();
        Map<String, CustomDef> customDefs = new HashMap<>();
        ByteArrayOutputStream currentChunk = new ByteArrayOutputStream();

        shellScript.append("#!/bin/sh\n");
        shellScript.append("HID_DEV=/dev/hidg0\n");
        // Optimized Base64 piping approach:
        // echo "BASE64" | base64 -d | dd of=$HID_DEV bs=8

        String[] lines = duckyScript.split("\n");
        int lineNum = 0;
        
        for (String line : lines) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("REM")) continue;

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toUpperCase();

            // 1. DELAY
            if (cmd.equals("DELAY")) {
                if (parts.length < 2) {
                    errors.add("Line " + lineNum + ": DELAY missing duration");
                    continue;
                }
                try {
                    // Flush current buffer before delay
                    appendBlobCommand(shellScript, currentChunk.toByteArray());
                    currentChunk.reset();
                    
                    int delay = Integer.parseInt(parts[1].trim());
                    double sleepTime = Math.max(delay / 1000.0, 0.02); 
                    shellScript.append("sleep ").append(sleepTime).append("\n");
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNum + ": Invalid DELAY number");
                }
                continue;
            }

            // 2. STRING
            if (cmd.equals("STRING")) {
                if (parts.length < 2) {
                    errors.add("Line " + lineNum + ": STRING missing text");
                    continue;
                }
                String text = line.substring(7);
                for (char c : text.toCharArray()) {
                    HidCode mapping = ASCII_MAP.get(c);
                    if (mapping != null) {
                        addPressRelease(currentChunk, mapping.mod, mapping.key);
                    }
                }
                continue;
            }

            // 3. DEFINE
            if (cmd.equals("DEFINE")) {
                String[] defParts = line.split("\\s+");
                if (defParts.length != 4) {
                    errors.add("Line " + lineNum + ": Usage: DEFINE [NAME] [MOD_HEX] [KEY_HEX]");
                    continue;
                }
                try {
                    String name = defParts[1].toUpperCase();
                    int mod = Integer.decode(defParts[2]);
                    int key = Integer.decode(defParts[3]);
                    customDefs.put(name, new CustomDef((byte)mod, (byte)key));
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNum + ": Invalid Hex in DEFINE");
                }
                continue;
            }

            // 4. CUSTOM DEF
            if (customDefs.containsKey(cmd)) {
                CustomDef def = customDefs.get(cmd);
                addPressRelease(currentChunk, def.mod, def.key);
                continue;
            }

            // 5. MODIFIERS (CMD [Key])
            if (MODIFIERS.containsKey(cmd)) {
                byte mod = MODIFIERS.get(cmd);
                if (parts.length > 1) {
                    String arg = parts[1].trim();
                    Byte key = null;
                    
                    if (KEY_COMMANDS.containsKey(arg.toUpperCase())) {
                        key = KEY_COMMANDS.get(arg.toUpperCase());
                    } 
                    else if (arg.length() == 1) {
                        HidCode mapping = ASCII_MAP.get(arg.charAt(0));
                        if (mapping != null) {
                            key = mapping.key;
                            mod |= mapping.mod; // Combine mods
                        }
                    }
                    
                    if (key != null) {
                        addPressRelease(currentChunk, mod, key);
                    } else {
                        errors.add("Line " + lineNum + ": Unknown key '" + arg + "'");
                    }
                } else {
                    // Modifier alone (tap modifier)
                    addPressRelease(currentChunk, mod, (byte)0x00);
                }
                continue;
            }

            // 6. KEY COMMANDS
            if (KEY_COMMANDS.containsKey(cmd)) {
                byte key = KEY_COMMANDS.get(cmd);
                addPressRelease(currentChunk, (byte)0x00, key);
                continue;
            }

            errors.add("Line " + lineNum + ": Unknown command '" + cmd + "'\n");
        }
        
        // Flush remaining bytes
        appendBlobCommand(shellScript, currentChunk.toByteArray());
        
        return new ParseResult(shellScript.toString(), errors);
    }
    
    private static void addPressRelease(ByteArrayOutputStream os, byte mod, byte key) {
        // PRESS (8 bytes)
        os.write(mod); os.write(0); os.write(key);
        os.write(0); os.write(0); os.write(0); os.write(0); os.write(0);

        // RELEASE (8 bytes)
        os.write(0); os.write(0); os.write(0);
        os.write(0); os.write(0); os.write(0); os.write(0); os.write(0);
    }

    private static void appendBlobCommand(StringBuilder script, byte[] data) {
        if (data.length == 0) return;
        
        String b64 = Base64.getEncoder().encodeToString(data);
        
        // This is the "Atomic" trick using dd
        // We decode base64, then use dd to push it to the HID gadget
        // bs=8 ensures we write in 8-byte chunks (HID requirement)
        script.append("echo \"" + b64 + "\" | base64 -d | dd of=$HID_DEV bs=8 2>/dev/null\n");
    }
}
