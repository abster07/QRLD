// CreateFragment.java - Fixed
package com.qrmaster.app.fragments;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CreateFragment extends Fragment {
    private static final int STORAGE_PERMISSION_CODE = 101;

    private AutoCompleteTextView typeSpinner;
    private LinearLayout dynamicFieldsContainer;
    private View colorForeground, colorBackground;
    private MaterialButton btnGenerate, btnSave;
    private QRViewModel viewModel;
    private Bitmap currentQRBitmap;
    private String currentFgColor = "#000000";
    private String currentBgColor = "#FFFFFF";
    private String currentContent = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create, container, false);

        typeSpinner             = view.findViewById(R.id.qr_type_spinner);
        dynamicFieldsContainer  = view.findViewById(R.id.dynamic_fields_container);
        colorForeground         = view.findViewById(R.id.color_foreground);
        colorBackground         = view.findViewById(R.id.color_background);
        btnGenerate             = view.findViewById(R.id.btn_generate);
        btnSave                 = view.findViewById(R.id.btn_save);

        viewModel = new ViewModelProvider(this).get(QRViewModel.class);

        setupTypeSpinner();
        setupClickListeners();
        updateFieldsForType("Text");

        return view;
    }

    private void setupTypeSpinner() {
        String[] types = {"Text", "URL", "WiFi", "Contact", "Email", "Phone", "SMS", "Payment"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, types);
        typeSpinner.setAdapter(adapter);
        typeSpinner.setText("Text", false);

        typeSpinner.setOnItemClickListener((parent, v, position, id) ->
                updateFieldsForType(types[position]));
    }

    private void updateFieldsForType(String type) {
        dynamicFieldsContainer.removeAllViews();

        switch (type) {
            case "Text":
            case "URL":
                addTextField("Content", "Enter " + type.toLowerCase());
                break;
            case "WiFi":
                addTextField("Network Name (SSID)", "Enter WiFi network name");
                addTextField("Password", "Enter WiFi password");
                addSecurityDropdown();
                break;
            case "Contact":
                addTextField("Name", "Enter contact name");
                addTextField("Phone", "Enter phone number");
                addTextField("Email", "Enter email address");
                break;
            case "Email":
                addTextField("Email Address", "Enter email");
                addTextField("Subject", "Enter subject (optional)");
                addTextField("Message", "Enter message (optional)");
                break;
            case "Phone":
                addTextField("Phone Number", "Enter phone number");
                break;
            case "SMS":
                addTextField("Phone Number", "Enter phone number");
                addTextField("Message", "Enter SMS message");
                break;
            case "Payment":
                addTextField("Payment Info", "Enter payment information");
                break;
        }
    }

    private void addTextField(String hint, String helperText) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setHelperText(helperText);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 16, 0, 0);
        layout.setLayoutParams(params);

        TextInputEditText editText = new TextInputEditText(layout.getContext());
        layout.addView(editText);

        dynamicFieldsContainer.addView(layout);
    }

    private void addSecurityDropdown() {
        // BUG FIX #2: Use TextInputLayout style that supports ExposedDropdownMenu
        // so that getEditText() returns the AutoCompleteTextView correctly.
        TextInputLayout layout = new TextInputLayout(
                requireContext(),
                null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        layout.setHint("Security Type");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 16, 0, 0);
        layout.setLayoutParams(params);

        AutoCompleteTextView dropdown = new AutoCompleteTextView(layout.getContext());
        String[] securities = {"WPA", "WPA2", "WEP", "None"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, securities);
        dropdown.setAdapter(adapter);
        dropdown.setText("WPA2", false);
        dropdown.setInputType(0); // disable keyboard

        layout.addView(dropdown);
        dynamicFieldsContainer.addView(layout);
    }

    private void setupClickListeners() {
        btnGenerate.setOnClickListener(v -> generateQR());
        btnSave.setOnClickListener(v -> saveToGallery());

        colorForeground.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Color picker coming soon",
                        Toast.LENGTH_SHORT).show());
        colorBackground.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Color picker coming soon",
                        Toast.LENGTH_SHORT).show());
    }

    private void generateQR() {
        String type = typeSpinner.getText().toString();
        String content = buildContentFromFields(type);

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all required fields",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            currentContent   = content;
            currentQRBitmap  = generateQRBitmap(content, 512, 512);
            showQRPreviewDialog();
        } catch (WriterException e) {
            Toast.makeText(requireContext(), "Error generating QR", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildContentFromFields(String type) {
        StringBuilder content = new StringBuilder();

        switch (type) {
            case "Text":
            case "URL":
                content.append(getFieldValue(0));
                break;
            case "WiFi":
                String ssid     = getFieldValue(0);
                String password = getFieldValue(1);
                String security = getFieldValue(2);
                if (!ssid.isEmpty()) {
                    content.append("WIFI:T:").append(security)
                           .append(";S:").append(ssid)
                           .append(";P:").append(password).append(";;");
                }
                break;
            case "Contact":
                String name  = getFieldValue(0);
                String phone = getFieldValue(1);
                String email = getFieldValue(2);
                if (!name.isEmpty()) {
                    content.append("BEGIN:VCARD\n")
                           .append("VERSION:3.0\n")
                           .append("FN:").append(name).append("\n");
                    if (!phone.isEmpty()) content.append("TEL:").append(phone).append("\n");
                    if (!email.isEmpty()) content.append("EMAIL:").append(email).append("\n");
                    content.append("END:VCARD");
                }
                break;
            case "Email":
                String emailAddr = getFieldValue(0);
                String subject   = getFieldValue(1);
                String message   = getFieldValue(2);
                if (!emailAddr.isEmpty()) {
                    content.append("mailto:").append(emailAddr);
                    if (!subject.isEmpty() || !message.isEmpty()) {
                        content.append("?");
                        if (!subject.isEmpty()) content.append("subject=").append(subject);
                        if (!subject.isEmpty() && !message.isEmpty()) content.append("&");
                        if (!message.isEmpty()) content.append("body=").append(message);
                    }
                }
                break;
            case "Phone":
                String phoneNum = getFieldValue(0);
                if (!phoneNum.isEmpty()) content.append("tel:").append(phoneNum);
                break;
            case "SMS":
                String smsPhone = getFieldValue(0);
                String smsMsg   = getFieldValue(1);
                if (!smsPhone.isEmpty()) {
                    content.append("smsto:").append(smsPhone);
                    if (!smsMsg.isEmpty()) content.append(":").append(smsMsg);
                }
                break;
            case "Payment":
                content.append(getFieldValue(0));
                break;
        }

        return content.toString();
    }

    /**
     * BUG FIX #2: Previously tried `layout.getChildAt(0)` to find an
     * AutoCompleteTextView, but TextInputLayout wraps its child in an
     * internal FrameLayout, so getChildAt(0) returns that wrapper — not
     * the edit text. Using `layout.getEditText()` works for both
     * TextInputEditText and AutoCompleteTextView.
     */
    private String getFieldValue(int index) {
        if (index >= dynamicFieldsContainer.getChildCount()) return "";

        View child = dynamicFieldsContainer.getChildAt(index);
        if (child instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) child;
            // getEditText() correctly returns the inner edit text regardless of type
            if (layout.getEditText() != null) {
                return layout.getEditText().getText().toString().trim();
            }
        }
        return "";
    }

    private void showQRPreviewDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_preview, null);
        android.widget.ImageView qrImage = dialogView.findViewById(R.id.qr_preview_image);
        qrImage.setImageBitmap(currentQRBitmap);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("QR Code Generated")
                .setView(dialogView)
                .setPositiveButton("Save to History", (d, w) -> saveToHistory())
                .setNeutralButton("Save to Gallery",  (d, w) -> saveToGallery())
                .setNegativeButton("Close", null)
                .show();
    }

    private void saveToHistory() {
        if (currentQRBitmap == null) {
            Toast.makeText(requireContext(), "Generate QR first", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = typeSpinner.getText().toString();
        QRItem item = new QRItem(currentContent, type, System.currentTimeMillis());
        item.setGenerated(true);
        item.setSaved(false);
        item.setColorForeground(currentFgColor);
        item.setColorBackground(currentBgColor);
        viewModel.insert(item);
        Toast.makeText(requireContext(), "Saved to history", Toast.LENGTH_SHORT).show();
    }

    private void saveToGallery() {
        if (currentQRBitmap == null) {
            Toast.makeText(requireContext(), "Generate QR first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGalleryAndroid10Plus();
        } else {
            if (checkStoragePermission()) {
                saveToGalleryLegacy();
            } else {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_CODE);
            }
        }
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToGalleryLegacy();
            } else {
                Toast.makeText(requireContext(), "Storage permission required",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private void saveToGalleryAndroid10Plus() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "QR_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/QR Master");

        Uri uri = requireContext().getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream out =
                         requireContext().getContentResolver().openOutputStream(uri)) {
                currentQRBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Toast.makeText(requireContext(), "Saved to gallery",
                        Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(requireContext(), "Error saving to gallery",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveToGalleryLegacy() {
        File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File qrDir = new File(picturesDir, "QR Master");
        if (!qrDir.exists()) qrDir.mkdirs();

        File imageFile = new File(qrDir, "QR_" + System.currentTimeMillis() + ".png");

        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            currentQRBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            android.media.MediaScannerConnection.scanFile(requireContext(),
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{"image/png"}, null);
            Toast.makeText(requireContext(), "Saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error saving to gallery",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap generateQRBitmap(String content, int width, int height)
            throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        int fg = Color.parseColor(currentFgColor);
        int bg = Color.parseColor(currentBgColor);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? fg : bg);
            }
        }
        return bitmap;
    }
}