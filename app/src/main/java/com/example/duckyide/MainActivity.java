package com.example.duckyide;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class MainActivity extends AppCompatActivity {

    private EditText editor;
    private TextView statusLog;
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Logger.init(this);
        Logger.log("DuckyIDE Started");
        
        // Extract helper binaries
        AssetCopier.extractAssets(this);

        editor = findViewById(R.id.editor);
        statusLog = findViewById(R.id.status_log);
        Button btnRun = findViewById(R.id.btn_run);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnLoad = findViewById(R.id.btn_load);
        Button btnArsenal = findViewById(R.id.btn_usb_arsenal);

        // Initialize File Picker
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    loadFromUri(uri);
                }
            }
        );

        checkRoot();

        btnRun.setOnClickListener(v -> runScript());
        btnSave.setOnClickListener(v -> saveScript());
        btnLoad.setOnClickListener(v -> loadScript());
        btnArsenal.setOnClickListener(v -> {
            startActivity(new Intent(this, UsbArsenalActivity.class));
        });
    }

    private void checkRoot() {
        new Thread(() -> {
            boolean root = RootShell.isRootAvailable();
            Logger.log("Root Check: " + root);
            runOnUiThread(() -> {
                if (root) {
                    statusLog.setText("> Root access: GRANTED.\n> Ready to inject.");
                } else {
                    statusLog.setText("> Root access: DENIED.\n> App requires root for injection.");
                }
            });
        }).start();
    }

    private void runScript() {
        String code = editor.getText().toString();
        if (code.isEmpty()) return;

        statusLog.setText("> Compiling...");
        
        DuckyParser.ParseResult result = DuckyParser.parseToShell(code);
        
        if (!result.errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("> Compilation Errors:\n");
            for (String err : result.errors) {
                sb.append("  - ").append(err).append("\n");
            }
            statusLog.setText(sb.toString());
            Logger.log("Compilation Failed: " + result.errors.toString());
            return;
        }
        
        statusLog.append("\n> Injecting...");
        Logger.log("Starting Injection...");
        new Thread(() -> {
            try {
                // Write script to temp file for reliable execution
                File payloadFile = new File(getCacheDir(), "payload.sh");
                try (FileWriter writer = new FileWriter(payloadFile)) {
                    writer.write(result.shellScript);
                }
                
                RootShell.runScriptFile(payloadFile.getAbsolutePath());
                Logger.log("Injection Complete");
                runOnUiThread(() -> statusLog.append("\n> Injection Complete."));
            } catch (IOException e) {
                Logger.log("Injection Error: " + e.getMessage());
                runOnUiThread(() -> statusLog.append("\n> Error: " + e.getMessage()));
            }
        }).start();
    }

    private void saveScript() {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, "payload.txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(editor.getText().toString());
            Toast.makeText(this, "Saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            statusLog.setText("> Saved: " + file.getName());
            Logger.log("Saved script to " + file.getAbsolutePath());
        } catch (IOException e) {
             Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
             Logger.log("Save Failed: " + e.getMessage());
        }
    }

    private void loadScript() {
        // Launch system file picker for text/plain or any file
        filePickerLauncher.launch("text/*");
    }

    private void loadFromUri(Uri uri) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
            }
            editor.setText(stringBuilder.toString());
            statusLog.setText("> Loaded file from storage.");
            Logger.log("Loaded script from URI: " + uri.toString());
        } catch (IOException e) {
            Toast.makeText(this, "Error loading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Logger.log("Load Error: " + e.getMessage());
        }
    }
}
