package com.example.duckyide;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetCopier {

    public static void extractAssets(Context context) {
        copyAsset(context, "hid-keyboard", "hid-keyboard", true);
    }

    private static void copyAsset(Context context, String assetName, String destName, boolean executable) {
        File destFile = new File(context.getFilesDir(), destName);
        if (destFile.exists()) {
            // Ideally check version/hash, but for now assume existing is fine or overwrite
            // Force overwrite to ensure update
            destFile.delete(); 
        }

        try (InputStream in = context.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            
            if (executable) {
                destFile.setExecutable(true, false); // rwxr-xr-x
            }
            Logger.log("Extracted asset: " + destName);
            
        } catch (IOException e) {
            Logger.log("Failed to extract asset: " + assetName + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static String getToolPath(Context context, String toolName) {
        return new File(context.getFilesDir(), toolName).getAbsolutePath();
    }
}
