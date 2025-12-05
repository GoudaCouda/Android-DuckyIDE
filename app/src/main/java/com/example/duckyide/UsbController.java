package com.example.duckyide;

import java.io.File;
import java.io.IOException;

public class UsbController {

    private static final String GADGET_PATH = "/config/usb_gadget/g1";
    // Standard 8-byte Keyboard Report Descriptor
    private static final String KEYBOARD_REPORT_DESC = 
        "05010906a101050719e029e71500250175019508810295017508810395057501050819012905910295017503910395067508150025650507190029658100c0";
    // Standard Mouse Report Descriptor
    private static final String MOUSE_REPORT_DESC = 
        "05010902a1010901a100050919012903150025019503750181029501750581030501093009311581257f750895028106c0c0";

    public static String getEnabledFunctions() {
        // Return a list of what is actually linked in configs/b.1
        // PLUS the system state property to correctly detect ADB
        String links = RootShell.exec("ls " + GADGET_PATH + "/configs/b.1 2>&1");
        String state = RootShell.exec("getprop sys.usb.state");
        return links + "," + state;
    }

    /**
     * Applies USB configuration.
     * @param functions Comma-separated functions (e.g. "hid,mass_storage,adb")
     * @param vid Vendor ID (e.g. "0x1d6b")
     * @param pid Product ID (e.g. "0x0104")
     * @return Log string. Starts with "SUCCESS" if successful, otherwise contains error details.
     */
    public static String setUsbFunctions(String functions, String vid, String pid) {
        StringBuilder log = new StringBuilder();
        log.append("Target: ").append(functions).append(" [").append(vid).append(":").append(pid).append("]\n");

        try {
            boolean enableHid = functions.contains("hid");
            boolean enableStorage = functions.contains("mass_storage");
            boolean enableRndis = functions.contains("rndis");
            boolean enableMtp = functions.contains("mtp");
            boolean enableAdb = functions.contains("adb");
            
            String baseConfig = enableAdb ? "adb" : "none";
            log.append("Base Config: ").append(baseConfig).append("\n");

            // 1. Cleanup Custom Gadgets
            cleanupGadgets(log);

            // 2. Reset System State
            String currentBase = RootShell.exec("getprop sys.usb.config").trim();
            if (!currentBase.equals(baseConfig) || baseConfig.equals("none")) {
                log.append("Resetting USB stack...\n");
                logExec(log, "setprop sys.usb.config none");
                pollState("none", log);
                
                if (!baseConfig.equals("none")) {
                    log.append("Setting base: ").append(baseConfig).append("\n");
                    logExec(log, "setprop sys.usb.config " + baseConfig);
                    pollState(baseConfig, log);
                }
            } else {
                log.append("Base config already match.\n");
            }

            if (functions.equals("none")) return "SUCCESS\n" + log.toString();

            // 3. Locate Config Path
            String configPath = GADGET_PATH + "/configs/b.1";
            if (!exists(configPath)) {
                // Try to find fallback
                String find = RootShell.exec("ls " + GADGET_PATH + "/configs/ | head -n 1").trim();
                if (!find.isEmpty()) configPath = GADGET_PATH + "/configs/" + find;
                log.append("Config path resolved: ").append(configPath).append("\n");
            }

            // 4. Link Gadgets (Order: Storage -> RNDIS -> MTP -> Mouse -> Keyboard)
            if (enableStorage) {
                // Prevent auto-mount of previous image
                String msFunc = findFunction("mass_storage");
                if (!msFunc.isEmpty()) {
                    logExec(log, "echo '' > " + GADGET_PATH + "/functions/" + msFunc + "/lun.0/file");
                }
                linkFunction(log, "mass_storage", configPath);
            }
            if (enableRndis) linkFunction(log, "rndis", configPath);
            if (enableMtp) linkFunction(log, "mtp", configPath);

            if (enableHid) {
                setupMouseGadget(log);
                logExec(log, "ln -s " + GADGET_PATH + "/functions/hid.1 " + configPath + "/hid.1");
                
                setupKeyboardGadget(log);
                logExec(log, "ln -s " + GADGET_PATH + "/functions/hid.0 " + configPath + "/hid.0");
            }

            // 5. Set Identity (VID/PID)
            if (vid != null && !vid.isEmpty()) logExec(log, "echo " + vid + " > " + GADGET_PATH + "/idVendor");
            if (pid != null && !pid.isEmpty()) logExec(log, "echo " + pid + " > " + GADGET_PATH + "/idProduct");

            // 6. Bounce UDC
            bounceUDC(log);

            // 7. Validation
            if (enableHid) {
                 if (!pollFile("/dev/hidg0", log)) {
                     throw new IOException("Validation Failed: /dev/hidg0 missing.");
                 }
                 logExec(log, "chmod 666 /dev/hidg0");
                 if (pollFile("/dev/hidg1", log)) {
                     logExec(log, "chmod 666 /dev/hidg1");
                 }
            }
            
            return "SUCCESS\n" + log.toString();

        } catch (Exception e) {
            log.append("\nCRITICAL ERROR: ").append(e.getMessage());
            return log.toString();
        }
    }
    
    private static void cleanupGadgets(StringBuilder log) throws IOException {
        String configPath = GADGET_PATH + "/configs/b.1";
        String listing = RootShell.exec("ls " + configPath);
        
        if (listing.trim().isEmpty() || listing.contains("No such file")) return;

        String[] files = listing.split("[\\s\\n]+");
        for (String f : files) {
            f = f.trim();
            if (f.isEmpty()) continue;
            
            // Remove our custom gadgets
            if (f.startsWith("hid.") || 
                f.startsWith("mass_storage") || 
                f.startsWith("rndis") || 
                f.startsWith("mtp")) { // Added missing closing parenthesis
                
                logExec(log, "rm " + configPath + "/" + f);
            }
        }
    }

    private static void logExec(StringBuilder log, String cmd) throws IOException {
        log.append("> ").append(cmd).append("\n");
        // Capture stderr too
        String out = RootShell.exec(cmd + " 2>&1"); 
        if (out != null && !out.trim().isEmpty()) {
            log.append("  ").append(out.trim()).append("\n");
        }
    }
    
    private static boolean exists(String path) {
        String out = RootShell.exec("ls -d " + path + " 2>&1");
        return !out.contains("No such file");
    }

    private static void pollState(String expected, StringBuilder log) throws IOException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) { 
             String curr = RootShell.exec("getprop sys.usb.state").trim();
             if (curr.equals(expected)) return;
             try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        throw new IOException("Timeout waiting for state: " + expected);
    }
    
    private static boolean pollFile(String path, StringBuilder log) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
             if (exists(path)) return true;
             try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        log.append("Timeout waiting for file: ").append(path).append("\n");
        return false;
    }

    private static void linkFunction(StringBuilder log, String prefix, String configPath) throws IOException {
        String func = findFunction(prefix);
        if (!func.isEmpty()) {
            logExec(log, "ln -s " + GADGET_PATH + "/functions/" + func + " " + configPath + "/" + func);
        } else {
            log.append("WARN: Function not found: ").append(prefix).append("\n");
        }
    }

    private static String findFunction(String prefix) {
        try {
            String output = RootShell.exec("ls " + GADGET_PATH + "/functions/ | grep " + prefix).trim();
            if (output.isEmpty()) return "";
            
            // Prioritize vendor-specific naming (e.g. .gs6 for Pixel/Samsung, .usb0)
            if (output.contains(prefix + ".gs6")) return prefix + ".gs6";
            if (output.contains(prefix + ".usb0")) return prefix + ".usb0";
            
            // Fallback to first found
            return output.split("[\\s\\n]+")[0].trim();
        } catch (Exception e) { return ""; }
    }

    private static void bounceUDC(StringBuilder log) throws IOException {
        String udc = RootShell.exec("ls /sys/class/udc").trim();
        if (udc.isEmpty()) {
            log.append("WARN: No UDC found, skipping bounce.\n");
            return;
        }
        log.append("Bouncing UDC: ").append(udc).append("\n");
        logExec(log, "echo '' > " + GADGET_PATH + "/UDC");
        try { Thread.sleep(100); } catch (Exception e) {} 
        logExec(log, "echo " + udc + " > " + GADGET_PATH + "/UDC");
    }

    private static void setupKeyboardGadget(StringBuilder log) throws IOException {
        String hidPath = GADGET_PATH + "/functions/hid.0";
        log.append("Setting up Keyboard (hid.0)...\n");
        
        logExec(log, "mkdir -p " + hidPath);
        logExec(log, "echo 1 > " + hidPath + "/protocol");
        logExec(log, "echo 1 > " + hidPath + "/subclass");
        logExec(log, "echo 8 > " + hidPath + "/report_length");
        
        StringBuilder hexEscaped = new StringBuilder();
        for (int i = 0; i < KEYBOARD_REPORT_DESC.length(); i += 2) {
            hexEscaped.append("\\\\x").append(KEYBOARD_REPORT_DESC.substring(i, i + 2));
        }
        RootShell.exec("echo -ne \"" + hexEscaped.toString() + "\" > " + hidPath + "/report_desc");
    }

    private static void setupMouseGadget(StringBuilder log) throws IOException {
        String hidPath = GADGET_PATH + "/functions/hid.1";
        log.append("Setting up Mouse (hid.1)...\n");
        
        logExec(log, "mkdir -p " + hidPath);
        logExec(log, "echo 1 > " + hidPath + "/protocol");
        logExec(log, "echo 2 > " + hidPath + "/subclass");
        logExec(log, "echo 4 > " + hidPath + "/report_length");
        
        StringBuilder hexEscaped = new StringBuilder();
        for (int i = 0; i < MOUSE_REPORT_DESC.length(); i += 2) {
            hexEscaped.append("\\\\x").append(MOUSE_REPORT_DESC.substring(i, i + 2));
        }
        RootShell.exec("echo -ne \"" + hexEscaped.toString() + "\" > " + hidPath + "/report_desc");
    }

    // --- Mounting Logic (Kept strict but robust) ---

    public static String mountImage(String imagePath, boolean readOnly, boolean cdrom) {
        String func = findFunction("mass_storage");
        if (func.isEmpty()) return "Error: Mass Storage function not found (Enable it first?)";
        
        String lunPath = GADGET_PATH + "/functions/" + func + "/lun.0";
        String physicalPath = resolvePhysicalPath(imagePath);
        
        StringBuilder cmd = new StringBuilder();
        cmd.append("echo '' > ").append(lunPath).append("/file; ");
        cmd.append("echo '").append(readOnly ? "1" : "0").append("' > ").append(lunPath).append("/ro; ");
        cmd.append("echo '").append(cdrom ? "1" : "0").append("' > ").append(lunPath).append("/cdrom; ");
        cmd.append("echo '").append(physicalPath).append("' > ").append(lunPath).append("/file");
        
        return RootShell.exec(cmd.toString() + " 2>&1");
    }
    
    public static String getMountedImage() {
         String func = findFunction("mass_storage");
         if (func.isEmpty()) return "";
         return RootShell.exec("cat " + GADGET_PATH + "/functions/" + func + "/lun.0/file");
    }
    
    public static void unmountImage() {
        String func = findFunction("mass_storage");
        if (func.isEmpty()) return;
        String lunPath = GADGET_PATH + "/functions/" + func + "/lun.0";
        RootShell.exec("echo '' > " + lunPath + "/file");
    }
    
    private static String resolvePhysicalPath(String path) {
        if (path.startsWith("/sdcard/")) {
            return path.replace("/sdcard/", "/data/media/0/");
        } else if (path.startsWith("/storage/emulated/0/")) {
            return path.replace("/storage/emulated/0/", "/data/media/0/");
        }
        return path;
    }
}
