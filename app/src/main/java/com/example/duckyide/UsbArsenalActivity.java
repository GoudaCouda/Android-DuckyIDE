package com.example.duckyide;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import  java.io.IOException;
public class UsbArsenalActivity extends AppCompatActivity {

    private CheckBox chkHid, chkStorage, chkRndis, chkMtp;
    private Switch switchAdb;
    private Spinner spinnerImages;
    private EditText etVid, etPid;
    private TextView tvMountedFile;
    private TextView tvImagesPath;
    private TextView statusLog;
    private CheckBox chkRo;
    private CheckBox chkCdrom;
    
    private final String IMAGE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DuckyImages";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_arsenal);
        
        // Request Storage Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please grant 'All Files Access' to manage images", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        chkHid = findViewById(R.id.chk_func_hid);
        chkStorage = findViewById(R.id.chk_func_storage);
        chkRndis = findViewById(R.id.chk_func_rndis);
        chkMtp = findViewById(R.id.chk_func_mtp);
        switchAdb = findViewById(R.id.switch_adb);
        
        etVid = findViewById(R.id.et_vid);
        etPid = findViewById(R.id.et_pid);
        
        Spinner spinnerPreset = findViewById(R.id.spinner_vid_pid_preset);
        String[] presets = {
            "Linux (Default) - 0x1d6b:0x0104",
            "Windows (Generic) - 0x046d:0xc31c", 
            "Android (Pixel) - 0x18d1:0x4ee7",
            "Mac (Apple) - 0x05ac:0x024f",
            "Custom"
        };
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, presets);
        spinnerPreset.setAdapter(presetAdapter);
        
        spinnerPreset.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                switch (position) {
                    case 0: // Linux
                        etVid.setText("0x1d6b"); etPid.setText("0x0104"); break;
                    case 1: // Windows
                        etVid.setText("0x046d"); etPid.setText("0xc31c"); break;
                    case 2: // Android
                        etVid.setText("0x18d1"); etPid.setText("0x4ee7"); break;
                    case 3: // Mac
                        etVid.setText("0x05ac"); etPid.setText("0x024f"); break;
                    default: break;
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerImages = findViewById(R.id.spinner_images);
        tvMountedFile = findViewById(R.id.tv_mounted_file);
        tvImagesPath = findViewById(R.id.tv_images_path);
        statusLog = findViewById(R.id.status_log_arsenal);
        statusLog.setMovementMethod(new ScrollingMovementMethod());
        chkRo = findViewById(R.id.chk_ro);
        chkCdrom = findViewById(R.id.chk_cdrom);
        
        Button btnApply = findViewById(R.id.btn_set_usb);
        Button btnMount = findViewById(R.id.btn_mount);
        Button btnUnmount = findViewById(R.id.btn_unmount);
        Button btnOpenFolder = findViewById(R.id.btn_open_folder);

        ensureImageDir();
        refreshImageSpinner();
        refreshStatus();

        btnApply.setOnClickListener(v -> {
            List<String> funcs = new ArrayList<>();
            // Order matters for init.rc triggers! 
            // Usually: Network > Storage > HID > ADB
            if (chkRndis.isChecked()) funcs.add("rndis");
            if (chkStorage.isChecked()) funcs.add("mass_storage");
            if (chkMtp.isChecked()) funcs.add("mtp");
            if (chkHid.isChecked()) funcs.add("hid");
            if (switchAdb.isChecked()) funcs.add("adb");
            
            // Allow empty list (Disable All / Reset)
            String mode = String.join(",", funcs);
            if (mode.isEmpty()) mode = "none";
            
            String vid = etVid.getText().toString().trim();
            String pid = etPid.getText().toString().trim();

            statusLog.setText("> Setting USB config: " + mode);
            String finalMode = mode; // for lambda
            new Thread(() -> {
                String result = UsbController.setUsbFunctions(finalMode, vid, pid);
                runOnUiThread(() -> {
                     if (result != null && result.startsWith("SUCCESS")) {
                        statusLog.setText("> Config applied successfully.\n" + result);
                     } else {
                        statusLog.setText("> ERROR:\n" + result);
                     }
                     refreshStatus();
                });
            }).start();
        });
        
        btnOpenFolder.setOnClickListener(v -> {
             openImageFolder();
        });

        btnMount.setOnClickListener(v -> {
            Object selectedItem = spinnerImages.getSelectedItem();
            if (selectedItem == null) {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                return;
            }
            String filename = selectedItem.toString();
            String path = IMAGE_DIR + "/" + filename;
            boolean ro = chkRo.isChecked();
            boolean cdrom = chkCdrom.isChecked();
            
            statusLog.setText("> Mounting " + filename + "...");
            new Thread(() -> {
                String result = UsbController.mountImage(path, ro, cdrom);
                runOnUiThread(() -> {
                    if (result != null && !result.isEmpty()) {
                        statusLog.append("\n> Error: " + result);
                    } else {
                        statusLog.append("\n> Mount command sent.");
                    }
                    refreshStatus();
                });
            }).start();
        });

        btnUnmount.setOnClickListener(v -> {
            statusLog.setText("> Unmounting...");
             new Thread(() -> {
                UsbController.unmountImage();
                runOnUiThread(() -> {
                    statusLog.append("\n> Unmount command sent.");
                    refreshStatus();
                });
            }).start();
        });
    }
    
    private void ensureImageDir() {
        File dir = new File(IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        tvImagesPath.setText("Path: " + IMAGE_DIR);
    }

    private void openImageFolder() {
        Uri uri = Uri.parse(IMAGE_DIR);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*"); // Try generic first
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Fallback if no file manager handles directory view directly
             Toast.makeText(this, "Could not open file manager automatically.\nPlease manually go to: " + IMAGE_DIR, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshImageSpinner() {
        File dir = new File(IMAGE_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".img") || name.endsWith(".iso"));
        List<String> fileNames = new ArrayList<>();
        
        if (files != null && files.length > 0) {
            for (File f : files) fileNames.add(f.getName());
        } else {
            fileNames.add("No .img/.iso files found");
        }
        
        ArrayAdapter<String> imgAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fileNames);
        spinnerImages.setAdapter(imgAdapter);
    }

    private void refreshStatus() {
        new Thread(() -> {
            // Use getEnabledFunctions to see actual symlinks
            String currentFuncs = UsbController.getEnabledFunctions();
            String currentMount = UsbController.getMountedImage();
            
            runOnUiThread(() -> {
                if (currentMount.trim().isEmpty()) {
                    tvMountedFile.setText("MOUNTED: NONE");
                } else {
                    tvMountedFile.setText("MOUNTED: " + new File(currentMount).getName());
                }
                
                // Sync UI
                chkHid.setChecked(currentFuncs.contains("hid"));
                chkStorage.setChecked(currentFuncs.contains("mass_storage"));
                chkRndis.setChecked(currentFuncs.contains("rndis"));
                chkMtp.setChecked(currentFuncs.contains("mtp"));
                // ADB check matches "adb" (from prop) or "ffs.adb" (from link)
                switchAdb.setChecked(currentFuncs.contains("adb"));
            });
        }).start();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        refreshImageSpinner();
        refreshStatus();
    }
}
