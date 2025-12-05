package com.example.duckyide;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class MainActivity extends AppCompatActivity {

    private EditText editor;
    private TextView statusLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editor = findViewById(R.id.editor);
        statusLog = findViewById(R.id.status_log);
        Button btnRun = findViewById(R.id.btn_run);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnLoad = findViewById(R.id.btn_load);
        Button btnArsenal = findViewById(R.id.btn_usb_arsenal);

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
            return;
        }
        
        statusLog.append("\n> Injecting...");
        new Thread(() -> {
            try {
                RootShell.executeScript(result.shellScript);
                runOnUiThread(() -> statusLog.append("\n> Injection Complete."));
            } catch (IOException e) {
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
        } catch (IOException e) {
             Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadScript() {
         File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
         File file = new File(path, "payload.txt");
         if (!file.exists()) {
             Toast.makeText(this, "No payload.txt in Downloads", Toast.LENGTH_SHORT).show();
             return;
         }
         try {
             String content = new String(Files.readAllBytes(file.toPath()));
             editor.setText(content);
             statusLog.setText("> Loaded: " + file.getName());
         } catch (IOException e) {
             Toast.makeText(this, "Load Failed", Toast.LENGTH_SHORT).show();
         }
    }
}
