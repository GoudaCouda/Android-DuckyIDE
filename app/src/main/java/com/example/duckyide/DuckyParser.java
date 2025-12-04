package com.example.duckyide;

import java.util.HashMap;
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
    }

    public static String parseToShell(String duckyScript) {
        StringBuilder shellScript = new StringBuilder();
        shellScript.append("HID_DEV=/dev/hidg0\n");
        
        // Helper function to write report
        // Mod Reserved Key1 ...
        shellScript.append("write_report() {\n");
        shellScript.append("  echo -ne \"$1\" > $HID_DEV\n");
        shellScript.append("  sleep 0.02\n");
        shellScript.append("  echo -ne \"\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\" > $HID_DEV\n");
        shellScript.append("}\n\n");

        String[] lines = duckyScript.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("REM")) continue;

            if (line.startsWith("DELAY")) {
                try {
                    int delay = Integer.parseInt(line.substring(6).trim());
                    // Minimum sleep to prevent choking
                    double sleepTime = Math.max(delay / 1000.0, 0.05); 
                    shellScript.append("sleep ").append(sleepTime).append("\n");
                } catch (Exception e) { /* Ignore */ } 
            } else if (line.startsWith("STRING")) {
                if (line.length() > 7) {
                    String text = line.substring(7);
                    for (char c : text.toCharArray()) {
                        boolean shift = Character.isUpperCase(c);
                        Byte code = CHAR_MAP.get(Character.toLowerCase(c));
                        
                        // Handle basic symbols if needed (omitted for brevity except space)
                        if (code == null && c == ' ') code = (byte)0x2C;

                        if (code != null) {
                            String mod = shift ? "\\x02" : "\\x00";
                            String hexCode = String.format("\\x%02x", code);
                            shellScript.append("write_report \"").append(mod).append("\\x00").append(hexCode).append("\\x00\\x00\\x00\\x00\"
");
                        }
                    }
                }
            } else if (line.equals("ENTER")) {
                 shellScript.append("write_report \"\\x00\\x00\\x28\\x00\\x00\\x00\\x00\\x00\"
");
            } else if (line.startsWith("GUI") || line.startsWith("WINDOWS")) {
                 // Simple GUI key support
                 shellScript.append("write_report \"\\x08\\x00\\x00\\x00\\x00\\x00\\x00\\x00\"
");
            }
        }
        
        return shellScript.toString();
    }
}
