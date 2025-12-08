package com.example.duckyide;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedMonitor {

    public interface LedListener {
        void onLedStateChanged(boolean numLock, boolean capsLock, boolean scrollLock);
    }
    
    public interface LogListener {
        void onLog(String message);
    }

    private Process process;
    private boolean isRunning = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LedListener listener;
    private LogListener logListener;

    public void setListener(LedListener listener) {
        this.listener = listener;
    }
    
    public void setLogListener(LogListener logListener) {
        this.logListener = logListener;
    }
    
    private void log(String msg) {
        if (logListener != null) {
            logListener.onLog(msg);
        }
        Logger.log(msg);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        
        executor.execute(() -> {
            log("LedMonitor: Service started.");
            
            while (isRunning) {
                try {
                    // 1. Poll for file existence first (cheap check)
                    boolean deviceExists = false;
                    try {
                        // 'ls' is a lightweight way to check file existence with root
                        Process check = Runtime.getRuntime().exec("su -c ls /dev/hidg0");
                        if (check.waitFor() == 0) {
                            deviceExists = true;
                        }
                    } catch (Exception e) {
                        // Ignore, assume not exists
                    }

                    if (!deviceExists) {
                        // Wait before checking again
                        Thread.sleep(2000); 
                        continue;
                    }

                    // 2. Device found, start monitoring
                    log("LedMonitor: /dev/hidg0 found. Connecting...");
                    
                    // Using "dd" with bs=1 to read byte-by-byte
                    process = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    
                    os.writeBytes("dd bs=1 if=/dev/hidg0\n");
                    os.flush();

                    // Start a thread to read stderr so buffer doesn't fill
                    new Thread(() -> {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                // Only log interesting errors
                                if (!line.contains("records in") && !line.contains("records out")) {
                                    log("LedMonitor stderr: " + line);
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }).start();

                    InputStream is = process.getInputStream();
                    int b;
                    boolean connected = false;
                    
                    // Read byte by byte
                    while (isRunning && (b = is.read()) != -1) {
                        if (!connected) {
                            connected = true;
                            log("LedMonitor: Connected and reading events.");
                        }
                        
                        // b is the byte from HID.
                        // Standard Keyboard LED report:
                        // Bit 0: Num Lock
                        // Bit 1: Caps Lock
                        // Bit 2: Scroll Lock
                        
                        boolean num = (b & 0x01) != 0;
                        boolean caps = (b & 0x02) != 0;
                        boolean scroll = (b & 0x04) != 0;
                        
                        if (listener != null) {
                            listener.onLedStateChanged(num, caps, scroll);
                        }
                    }
                    
                    log("LedMonitor: Connection lost (stream ended).");
                    // Loop back to polling...
                    
                } catch (Exception e) {
                    if (isRunning) {
                        log("LedMonitor Error: " + e.getMessage());
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            log("LedMonitor: Service stopped.");
        });
    }

    public void stop() {
        isRunning = false;
        if (process != null) {
            process.destroy();
        }
    }
}
