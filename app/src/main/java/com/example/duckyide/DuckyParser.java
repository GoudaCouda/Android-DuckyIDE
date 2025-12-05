package com.example.duckyide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuckyParser {

    private static final Map<Character, Byte> CHAR_MAP = new HashMap<>();
    
    static {
        // Lowercase a-z (HID 0x04 - 0x1D)
        for (int i = 0; i < 26; i++) {
            CHAR_MAP.put((char)('a' + i), (byte)(0x04 + i));
        }
        // Numbers 1-9, 0 (HID 0x1E - 0x27)
        for (int i = 1; i <= 9; i++) {
            CHAR_MAP.put((char)('0' + i), (byte)(0x1E + i - 1)); // 1 starts at 1E
        }
        CHAR_MAP.put('0', (byte)0x27);
        
        CHAR_MAP.put(' ', (byte)0x2C);
        CHAR_MAP.put('\n', (byte)0x28);
        // Add common symbols for convenience
        CHAR_MAP.put('.', (byte)0x37);
        CHAR_MAP.put('-', (byte)0x2D);
        CHAR_MAP.put('/', (byte)0x38);
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
        String mod;
        String key;
        CustomDef(String mod, String key) { this.mod = mod; this.key = key; }
    }

    public static ParseResult parseToShell(String duckyScript) {
        StringBuilder shellScript = new StringBuilder();
        List<String> errors = new ArrayList<>();
        Map<String, CustomDef> customDefs = new HashMap<>();

        shellScript.append("HID_DEV=/dev/hidg0\n");
        shellScript.append("write_report() {\n");
        shellScript.append("  echo -ne \"$1\" > $HID_DEV\n");
        shellScript.append("  sleep 0.02\n");
        shellScript.append("  echo -ne \"\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\" > $HID_DEV\n");
        shellScript.append("}\n\n");

        String[] lines = duckyScript.split("\n");
        int lineNum = 0;
        
        for (String line : lines) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("REM")) continue;

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toUpperCase();

            if (cmd.equals("DELAY")) {
                if (parts.length < 2) {
                    errors.add("Line " + lineNum + ": DELAY missing duration");
                    continue;
                }
                try {
                    int delay = Integer.parseInt(parts[1].trim());
                    double sleepTime = Math.max(delay / 1000.0, 0.05); 
                    shellScript.append("sleep ").append(sleepTime).append("\n");
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNum + ": Invalid DELAY number");
                }
                
            } else if (cmd.equals("STRING")) {
                if (parts.length < 2) {
                    errors.add("Line " + lineNum + ": STRING missing text");
                    continue;
                }
                String text = line.substring(7); // Preserve spaces after STRING
                for (char c : text.toCharArray()) {
                    boolean shift = Character.isUpperCase(c);
                    Byte code = CHAR_MAP.get(Character.toLowerCase(c));
                    if (code == null && c == ' ') code = (byte)0x2C;

                    if (code != null) {
                        String mod = shift ? "\\x02" : "\\x00";
                        String hexCode = String.format("\\x%02x", code);
                        shellScript.append("write_report \"").append(mod).append("\\x00").append(hexCode).append("\\x00\\x00\\x00\\x00\"\\n");
                    } else {
                        // Warn but don't fail for unknown chars yet, just skip
                    }
                }
                
            } else if (cmd.equals("ENTER")) {
                 shellScript.append("write_report \"\\x00\\x00\\x28\\x00\\x00\\x00\\x00\\x00\"\\n");
                 
            } else if (cmd.equals("GUI") || cmd.equals("WINDOWS")) {
                 if (parts.length > 1) {
                     // Handle GUI r, GUI d, etc.
                     String key = parts[1].trim().toLowerCase();
                     if (key.length() == 1 && CHAR_MAP.containsKey(key.charAt(0))) {
                         Byte code = CHAR_MAP.get(key.charAt(0));
                         String hexCode = String.format("\\x%02x", code);
                         shellScript.append("write_report \"\\x08\\x00").append(hexCode).append("\\x00\\x00\\x00\\x00\"\\n");
                     } else {
                         errors.add("Line " + lineNum + ": Invalid GUI key");
                     }
                 } else {
                     shellScript.append("write_report \"\\x08\\x00\\x00\\x00\\x00\\x00\\x00\\x00\"\\n");
                 }
                 
            } else if (cmd.equals("DEFINE")) {
                // Syntax: DEFINE [NAME] [MOD] [KEY]  (e.g. DEFINE F13 00 68)
                String[] defParts = line.split("\\s+");
                if (defParts.length != 4) {
                    errors.add("Line " + lineNum + ": Usage: DEFINE [NAME] [MOD_HEX] [KEY_HEX]");
                    continue;
                }
                String name = defParts[1].toUpperCase();
                String mod = defParts[2];
                String key = defParts[3];
                
                if (!isHex(mod) || !isHex(key)) {
                    errors.add("Line " + lineNum + ": Invalid HEX format (use 00 or 0x00)");
                    continue;
                }
                // Clean hex for script
                mod = "\\x" + mod.replace("0x", "");
                key = "\\x" + key.replace("0x", "");
                customDefs.put(name, new CustomDef(mod, key));
                
            } else {
                // Check Custom Defs
                if (customDefs.containsKey(cmd)) {
                    CustomDef def = customDefs.get(cmd);
                    shellScript.append("write_report \"" + def.mod + "\\x00" + def.key + "\\x00\\x00\\x00\\x00\"\\n");
                } else {
                    errors.add("Line " + lineNum + ": Unknown command '" + cmd + "'" );
                }
            }
        }
        
        return new ParseResult(shellScript.toString(), errors);
    }
    
    private static boolean isHex(String s) {
        return s.matches("0x[0-9A-Fa-f]{2}") || s.matches("[0-9A-Fa-f]{2}");
    }
}