package com.example.duckyide;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static File logFile;

    public static void init(Context context) {
        if (logFile != null) return; // Already initialized
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            logFile = new File(dir, "ducky_debug.log");
        }
    }

    public static synchronized void log(String message) {
        if (logFile == null) return;
        
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        // Ensure message doesn't have weird trailing newlines that mess up formatting
        String cleanMsg = message.trim().replace("\n", "\n    "); 
        String entry = timestamp + ": " + cleanMsg + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(entry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static String getLogPath() {
        return logFile != null ? logFile.getAbsolutePath() : "Not initialized";
    }
}
