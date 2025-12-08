package com.example.duckyide;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LedMonitor {

    public interface LedListener {
        void onLedStateChanged(boolean numLock, boolean capsLock, boolean scrollLock);
    }
    
    public interface LogListener {
        void onLog(String message);
    }

    private boolean isRunning = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LedListener listener;
    private LogListener logListener;
    private volatile InputStream currentStream;

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
                    // 1. Check for file existence using su
                    boolean deviceExists = false;
                    try {
                        Process check = Runtime.getRuntime().exec("su -c ls /dev/hidg0");
                        if (check.waitFor() == 0) {
                            deviceExists = true;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }

                    if (!deviceExists) {
                        try {
                            Thread.sleep(2000); 
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }

                    log("LedMonitor: /dev/hidg0 found. Setting permissions...");

                    // 2. chmod 666 to allow direct reading (avoids shell buffering)
                    Process chmod = Runtime.getRuntime().exec("su -c chmod 666 /dev/hidg0");
                    chmod.waitFor();

                    // 3. Open Direct Stream
                    File deviceFile = new File("/dev/hidg0");
                    
                    try {
                        currentStream = new FileInputStream(deviceFile);
                        log("LedMonitor: Stream opened. Monitoring events...");
                        
                        int b;
                        boolean connected = false;
                        
                        // Read byte by byte directly from the character device
                        while (isRunning && (b = currentStream.read()) != -1) {
                            if (!connected) {
                                connected = true;
                                log("LedMonitor: Connected.");
                            }
                            
                            // Bit 0: Num Lock, Bit 1: Caps Lock, Bit 2: Scroll Lock
                            boolean num = (b & 0x01) != 0;
                            boolean caps = (b & 0x02) != 0;
                            boolean scroll = (b & 0x04) != 0;
                            
                            if (listener != null) {
                                listener.onLedStateChanged(num, caps, scroll);
                            }
                        }
                    } catch (IOException e) {
                        // This happens when stream is closed or device error
                        if (isRunning) {
                            log("LedMonitor IO Error: " + e.getMessage());
                        }
                    } finally {
                        if (currentStream != null) {
                            try {
                                currentStream.close();
                            } catch (IOException ignored) {}
                            currentStream = null;
                        }
                    }

                    if (isRunning) {
                        log("LedMonitor: Stream closed or EOF. Retrying...");
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                           break;
                        }
                    }
                    
                } catch (Exception e) {
                    if (isRunning) {
                        log("LedMonitor Error: " + e.getMessage());
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                           break;
                        }
                    }
                }
            }
            log("LedMonitor: Service stopped.");
        });
    }

    public void stop() {
        isRunning = false;
        // Close the stream to unblock the read() call
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        executor.shutdownNow();
    }
}