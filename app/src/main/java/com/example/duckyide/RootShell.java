package com.example.duckyide;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class RootShell {

    public static class CommandResult {
        public String stdout;
        public String stderr;
        public int exitCode;
        public CommandResult(String out, String err, int code) {
            stdout = out; stderr = err; exitCode = code;
        }
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static boolean isRootAvailable() {
        RootShell.CommandResult res = exec("ls"); // Simple command to check root access
        return res.isSuccess();
    }

    public static CommandResult runCommand(String command) {
        return exec(command); // Delegate to exec for full result
    }
    
    public static CommandResult runScriptFile(String filePath) {
        return exec("chmod 777 " + filePath + " && sh " + filePath);
    }
    
    // Deprecated: Use runScriptFile or exec for more robust execution.
    // Kept for historical reasons if needed, but not used now.
    public static void executeScript(String script) throws IOException {
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes(script + "\n");
        os.writeBytes("exit\n");
        os.flush();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static CommandResult exec(String command) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = -1; 
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("echo ::::EXITCODE::::$?::::\n"); // Custom marker for exit code
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader br_stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br_stdout.readLine()) != null) {
                if (line.startsWith("::::EXITCODE::::")) {
                    try { exitCode = Integer.parseInt(line.replace("::::EXITCODE::::", "").trim()); } 
                    catch (NumberFormatException nfe) {}
                    break; 
                }
                stdout.append(line).append("\n");
            }
            
            // Read stderr (non-blocking after stdout is consumed)
            BufferedReader br_stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = br_stderr.readLine()) != null) {
                stderr.append(line).append("\n");
            }
            
            p.waitFor();
            if (exitCode == -1) { 
                exitCode = p.exitValue();
            }
        } catch (Exception e) {
            stderr.append("Exception in exec: ").append(e.getMessage()).append("\n");
        }
        return new CommandResult(stdout.toString().trim(), stderr.toString().trim(), exitCode);
    }
}
