package com.example.duckyide;

import java.io.File;
import java.io.FileOutputStream;
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
        // This is the source of truth for the kernel state
        return RootShell.exec("ls " + GADGET_PATH + "/configs/b.1 2>&1").stdout;
    }

    /**
     * Applies USB configuration.
     * @param functions Comma-separated functions (e.g. "hid,mass_storage,adb")
     * @return Log string. Starts with "SUCCESS" if successful, otherwise contains error details.
     */
    public static String setUsbFunctions(String functions, String vid, String pid) {
        StringBuilder log = new StringBuilder();
        log.append("Target: ").append(functions).append("\n");
        
        // Function Counter for Nethunter-style "f1", "f2" naming
        int funcIndex = 0;

        try {
            boolean enableHid = functions.contains("hid");
            boolean enableStorage = functions.contains("mass_storage");
            boolean enableRndis = functions.contains("rndis");
            boolean enableMtp = functions.contains("mtp");
            boolean enableAdb = functions.contains("adb");
            
            // 1. DISABLE UDC (Stop Gadget)
            disableUDC(log);

            // 2. CLEANUP & RESET (Total Control Mode)
            logExec(log, "stop adbd");
            logExec(log, "setprop sys.usb.ffs.ready 0");
            //logExec(log, "setprop sys.usb.config none");
           // pollState("none", log);
            
            // Manual cleanup of our config folder
            cleanupGadgets(log);

            if (functions.equals("none")) return "SUCCESS\n" + log.toString();

            // 3. Resolve Paths
            String configPath = GADGET_PATH + "/configs/b.1";
            if (!exists(configPath)) {
                String find = RootShell.exec("ls " + GADGET_PATH + "/configs/ | head -n 1").stdout.trim();
                if (!find.isEmpty()) configPath = GADGET_PATH + "/configs/" + find;
            }

            // 4. LINK GADGETS (Sequential 'f1', 'f2'... naming)
            

            try { Thread.sleep(100); } catch (InterruptedException e) {}

            // HID (Mouse -> Keyboard)
            if (enableHid) {

                setupKeyboardGadget(log);
                funcIndex++;
                logExec(log, "ln -s " + GADGET_PATH + "/functions/hid.0 " + configPath + "/f" + funcIndex);


                setupMouseGadget(log);
                funcIndex++;
                logExec(log, "ln -s " + GADGET_PATH + "/functions/hid.1 " + configPath + "/f" + funcIndex);


            }


            // Mass Storage
            if (enableStorage) {
                String msFunc = findFunction("mass_storage");
                if (!msFunc.isEmpty()) {
                    logExec(log, "echo '' > " + GADGET_PATH + "/functions/" + msFunc + "/lun.0/file");
                }
                funcIndex++;
                linkFunction(log, "mass_storage", configPath, "f" + funcIndex);
            }

            // RNDIS
            if (enableRndis) {
                funcIndex++;
                linkFunction(log, "rndis", configPath, "f" + funcIndex);
            }
            
            // MTP
            if (enableMtp) {
                funcIndex++;
                linkFunction(log, "mtp", configPath, "f" + funcIndex);
            }

            // ADB (Manual Link)
            if (enableAdb) {
                log.append("Linking ADB manually...\n");
                String adbFunc = findFunction("ffs.adb");
                if (adbFunc.isEmpty()) adbFunc = "ffs.adb";

                if (exists(GADGET_PATH + "/functions/" + adbFunc)) {
                    funcIndex++;
                    logExec(log, "ln -s " + GADGET_PATH + "/functions/" + adbFunc + " " + configPath + "/f" + funcIndex);
                } else {
                    log.append("WARN: ffs.adb not found. ADB might fail.\n");
                }
            }
            



            // 5. IDENTITY
            if (vid != null && !vid.isEmpty()) logExec(log, "echo " + vid + " > " + GADGET_PATH + "/idVendor");
            if (pid != null && !pid.isEmpty()) logExec(log, "echo " + pid + " > " + GADGET_PATH + "/idProduct");


            // 7. START ADBD (If enabled)
            if (enableAdb) {
                logExec(log, "start adbd");
                logExec(log, "setprop sys.usb.ffs.ready 1");
            }

            // 6. ENABLE UDC
            enableUDC(log);


            // 8. VALIDATION
            if (enableHid) {
                 if (!pollFile("/dev/hidg0", log)) throw new IOException("Validation Failed: /dev/hidg0 missing.");
                 logExec(log, "chmod 666 /dev/hidg0");
                 if (pollFile("/dev/hidg1", log)) logExec(log, "chmod 666 /dev/hidg1");
            }
            
            return "SUCCESS\n" + log.toString();

        } catch (Exception e) {
            log.append("\nCRITICAL ERROR: ").append(e.getMessage());
            return log.toString();
        }
    }
    
    private static void cleanupGadgets(StringBuilder log) throws IOException {
        String configPath = GADGET_PATH + "/configs/b.1";
        String listing = RootShell.exec("ls " + configPath).stdout;
        
        if (listing.trim().isEmpty() || listing.contains("No such file")) return;

        String[] files = listing.split("\\s+");
        for (String f : files) {
            f = f.trim();
            if (f.isEmpty()) continue;
            
            // Nethunter uses 'f1', 'f2', etc. We clean those up too.
            // Also clean standard names in case they exist from old runs.
            if (f.matches("f\\d+") || // Matches f1, f2, f10
                f.startsWith("hid.") || 
                f.startsWith("mass_storage") || 
                f.startsWith("rndis") || 
                f.startsWith("mtp") ||
                f.startsWith("ffs.adb")) {
                
                logExec(log, "rm " + configPath + "/" + f);
            }
        }
    }

    private static void disableUDC(StringBuilder log) throws IOException {
        log.append("Disabling UDC...\n");
        logExec(log, "echo \"\" > " + GADGET_PATH + "/UDC");
        try { Thread.sleep(500); } catch (Exception e) {}
    }

    private static void enableUDC(StringBuilder log) throws IOException, InterruptedException {
        String udc = RootShell.exec("ls /sys/class/udc").stdout.trim();
        if (udc.isEmpty()) {
            log.append("WARN: No UDC found.\n");
            return;
        }
        log.append("Enabling UDC: ").append(udc).append("\n");
        logExec(log, "echo " + udc + " > " + GADGET_PATH + "/UDC");
        Thread.sleep(1000);
    }

    private static void logExec(StringBuilder log, String cmd) throws IOException {
        log.append("> ").append(cmd).append("\n");
        RootShell.CommandResult res = RootShell.exec(cmd);
        if (!res.stdout.isEmpty()) {
            log.append("  [stdout] ").append(res.stdout).append("\n");
        }
        if (!res.stderr.isEmpty()) {
            log.append("  [stderr] ").append(res.stderr).append("\n");
        }
        if (res.exitCode != 0) {
            log.append("  [exit] ").append(res.exitCode).append("\n");
            throw new IOException("Command failed: " + cmd + " (Exit " + res.exitCode + ")");
        } else {
            log.append("  [exit] ").append(res.exitCode).append("\n");
        }
    }
    
    private static boolean exists(String path) {
        return RootShell.exec("ls -d " + path).isSuccess();
    }

    private static void pollState(String expected, StringBuilder log) throws IOException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) { 
             String curr = RootShell.exec("getprop sys.usb.state").stdout.trim();
             if (curr.equals(expected)) return;
             try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        log.append("WARN: Timeout waiting for state: ").append(expected).append("\n");
        throw new IOException("Timeout waiting for USB state: " + expected);
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

    private static void linkFunction(StringBuilder log, String prefix, String configPath, String linkName) throws IOException {
        String func = findFunction(prefix);
        if (!func.isEmpty()) {
            logExec(log, "ln -s " + GADGET_PATH + "/functions/" + func + " " + configPath + "/" + linkName);
        } else {
            log.append("WARN: Function not found: ").append(prefix).append("\n");
        }
    }

    private static String findFunction(String prefix) {
        RootShell.CommandResult res = RootShell.exec("ls " + GADGET_PATH + "/functions/ | grep " + prefix);
        String output = res.stdout.trim();
        if (output.isEmpty()) return "";
        if (output.contains(prefix + ".gs6")) return prefix + ".gs6";
        if (output.contains(prefix + ".usb0")) return prefix + ".usb0";
        return output.split("\\s+")[0].trim();
    }

    private static void writeDescriptor(String path, String hexString) throws IOException {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                                 + Character.digit(hexString.charAt(i+1), 16));
        }
        
        File tempFile = File.createTempFile("desc", null);
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(data);
        fos.close();

        RootShell.CommandResult catRes = RootShell.exec("cat " + tempFile.getAbsolutePath() + " > " + path + "/report_desc");
        tempFile.delete();
        if (!catRes.isSuccess()) {
             throw new IOException("Failed to write descriptor: " + catRes.stderr);
        }
    }

    private static void setupKeyboardGadget(StringBuilder log) throws IOException {
        String hidPath = GADGET_PATH + "/functions/hid.0";
        log.append("Setting up Keyboard (hid.0)...");
        logExec(log, "mkdir -p " + hidPath);
        logExec(log, "echo 1 > " + hidPath + "/protocol");
        logExec(log, "echo 1 > " + hidPath + "/subclass");
        logExec(log, "echo 8 > " + hidPath + "/report_length");
        
        writeDescriptor(hidPath, KEYBOARD_REPORT_DESC);
    }

    private static void setupMouseGadget(StringBuilder log) throws IOException {
        String hidPath = GADGET_PATH + "/functions/hid.1";
        log.append("Setting up Mouse (hid.1)...");
        logExec(log, "mkdir -p " + hidPath);
        logExec(log, "echo 1 > " + hidPath + "/protocol");
        logExec(log, "echo 2 > " + hidPath + "/subclass");
        logExec(log, "echo 4 > " + hidPath + "/report_length");
        
        writeDescriptor(hidPath, MOUSE_REPORT_DESC);
    }

    // --- Mounting Logic ---

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
        
        RootShell.CommandResult res = RootShell.exec(cmd.toString());
        return res.isSuccess() ? "" : "Error: " + res.stderr;
    }
    
    public static String getMountedImage() {
         String func = findFunction("mass_storage");
         if (func.isEmpty()) return "";
         return RootShell.exec("cat " + GADGET_PATH + "/functions/" + func + "/lun.0/file").stdout;
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
