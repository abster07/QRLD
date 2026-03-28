// ScanFragment.java - Fixed version with single scan and detailed dialog
package com.qrmaster.app.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;        // BUG FIX #1a: was missing
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;                     // BUG FIX #1b: was missing
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrmaster.app.R;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.util.ArrayList;                           // BUG FIX #1c: was missing
import java.util.concurrent.ExecutionException;

public class ScanFragment extends Fragment {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private PreviewView previewView;
    private MaterialButton btnFlash, btnGallery;
    private Camera camera;
    private boolean flashEnabled = false;
    private QRViewModel viewModel;
    private boolean isScanning = true;
    private long lastScanTime = 0;
    private static final long SCAN_COOLDOWN = 2000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan, container, false);

        previewView  = view.findViewById(R.id.preview_view);
        btnFlash     = view.findViewById(R.id.btn_flash);
        btnGallery   = view.findViewById(R.id.btn_gallery);

        viewModel = new ViewModelProvider(this).get(QRViewModel.class);

        btnFlash.setOnClickListener(v -> toggleFlash());
        btnGallery.setOnClickListener(v -> openGallery());

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        return view;
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission required",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bindCameraUseCases(provider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(requireContext(), "Error starting camera",
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), image -> {
            if (isScanning) {
                processImage(image);
            } else {
                image.close();
            }
        });

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Camera binding failed",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_COOLDOWN) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        BarcodeScanning.getClient().process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String content = barcode.getRawValue();
                        if (content != null && isScanning) {
                            isScanning = false;
                            lastScanTime = System.currentTimeMillis();
                            handleScannedCode(content, barcode);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleScannedCode(String content, Barcode barcode) {
        String qrType = getQRType(barcode.getValueType(), content);
        requireActivity().runOnUiThread(() -> showQRDetailDialog(content, qrType, barcode));
    }

    private void showQRDetailDialog(String content, String type, Barcode barcode) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_detail, null);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("QR Code Scanned")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    saveQRCode(content, type);
                    isScanning = true;
                })
                .setNegativeButton("Cancel", (dialog, which) -> isScanning = true)
                .setOnDismissListener(dialog -> isScanning = true)
                .show();

        setupDialogContent(dialogView, content, type, barcode);
    }

    private void setupDialogContent(View view, String content, String type, Barcode barcode) {
        android.widget.TextView tvType    = view.findViewById(R.id.tv_qr_type);
        android.widget.TextView tvContent = view.findViewById(R.id.tv_qr_content);
        android.widget.LinearLayout actionButtons = view.findViewById(R.id.action_buttons);

        tvType.setText(type);
        actionButtons.removeAllViews();

        switch (type) {
            case "URL":
                tvContent.setText(content);
                addActionButton(actionButtons, "Open in Browser", () -> openUrl(content));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(content));
                break;
            case "WiFi":
                tvContent.setText(parseWiFiInfo(content));
                addActionButton(actionButtons, "Connect", () -> connectToWiFi(content));
                addActionButton(actionButtons, "Copy Password",
                        () -> copyToClipboard(extractWiFiPassword(content)));
                break;
            case "Email":
                String email = extractEmail(barcode);
                tvContent.setText(email);
                addActionButton(actionButtons, "Send Email", () -> sendEmail(email));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(email));
                break;
            case "Phone":
                String phone = extractPhone(barcode);
                tvContent.setText(phone);
                addActionButton(actionButtons, "Call", () -> dialPhone(phone));
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(phone));
                break;
            case "SMS":
                tvContent.setText(parseSMSInfo(barcode));
                addActionButton(actionButtons, "Send SMS", () -> sendSMS(barcode));
                break;
            case "Contact":
                tvContent.setText(parseContactInfo(barcode));
                addActionButton(actionButtons, "Add to Contacts", () -> addContact(barcode));
                break;
            case "Location":
                tvContent.setText(parseLocationInfo(barcode));
                addActionButton(actionButtons, "Open in Maps", () -> openLocation(barcode));
                break;
            default:
                tvContent.setText(content);
                addActionButton(actionButtons, "Copy", () -> copyToClipboard(content));
                break;
        }
    }

    private void addActionButton(android.widget.LinearLayout container,
                                 String text, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setOnClickListener(v -> action.run());

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        container.addView(button);
    }

    // ── WiFi helpers ──────────────────────────────────────────────────────────

    private String parseWiFiInfo(String content) {
        String ssid = "", password = "", security = "";
        if (content.startsWith("WIFI:")) {
            for (String part : content.substring(5).split(";")) {
                if (part.startsWith("S:"))      ssid     = part.substring(2);
                else if (part.startsWith("P:")) password = part.substring(2);
                else if (part.startsWith("T:")) security = part.substring(2);
            }
        }
        return "Network: " + ssid + "\nPassword: " + password + "\nSecurity: " + security;
    }

    private String extractWiFiPassword(String content) {
        if (content.startsWith("WIFI:")) {
            for (String part : content.substring(5).split(";")) {
                if (part.startsWith("P:")) return part.substring(2);
            }
        }
        return "";
    }

    private void connectToWiFi(String content) {
        String ssid = "", password = "", security = "";
        if (content.startsWith("WIFI:")) {
            for (String part : content.substring(5).split(";")) {
                if (part.startsWith("S:"))      ssid     = part.substring(2);
                else if (part.startsWith("P:")) password = part.substring(2);
                else if (part.startsWith("T:")) security = part.substring(2);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWiFiAndroid10Plus(ssid, password, security);
        } else {
            connectWiFiLegacy(ssid, password, security);
        }
    }

    // BUG FIX #1 — rebuilt with correct imports & proper API usage
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectWiFiAndroid10Plus(String ssid, String password, String security) {
        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder()
                .setSsid(ssid);

        if ("WPA".equalsIgnoreCase(security) || "WPA2".equalsIgnoreCase(security)) {
            builder.setWpa2Passphrase(password);
        } else if ("WPA3".equalsIgnoreCase(security)) {
            builder.setWpa3Passphrase(password);
        }
        // "None" / WEP → open network, no passphrase needed

        ArrayList<WifiNetworkSuggestion> suggestions = new ArrayList<>();
        suggestions.add(builder.build());

        Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
        intent.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, suggestions);
        startActivity(intent);
    }

    @SuppressWarnings("deprecation")
    private void connectWiFiLegacy(String ssid, String password, String security) {
        WifiManager wifiManager = (WifiManager)
                requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + ssid + "\"";

        if (security.equalsIgnoreCase("WPA") || security.equalsIgnoreCase("WPA2")) {
            wifiConfig.preSharedKey = "\"" + password + "\"";
        } else if (security.equalsIgnoreCase("WEP")) {
            wifiConfig.wepKeys[0] = "\"" + password + "\"";
            wifiConfig.wepTxKeyIndex = 0;
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else {
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int netId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();

        Toast.makeText(requireContext(), "Connecting to WiFi…", Toast.LENGTH_SHORT).show();
    }

    // ── Other barcode helpers ─────────────────────────────────────────────────

    private String extractEmail(Barcode barcode) {
        return barcode.getEmail() != null
                ? barcode.getEmail().getAddress()
                : barcode.getRawValue();
    }

    private String extractPhone(Barcode barcode) {
        return barcode.getPhone() != null
                ? barcode.getPhone().getNumber()
                : barcode.getRawValue();
    }

    private String parseSMSInfo(Barcode barcode) {
        if (barcode.getSms() != null) {
            return "To: " + barcode.getSms().getPhoneNumber()
                    + "\nMessage: " + barcode.getSms().getMessage();
        }
        return barcode.getRawValue();
    }

    private String parseContactInfo(Barcode barcode) {
        if (barcode.getContactInfo() != null) {
            Barcode.ContactInfo contact = barcode.getContactInfo();
            StringBuilder info = new StringBuilder();
            if (contact.getName() != null)
                info.append("Name: ").append(contact.getName().getFormattedName()).append("\n");
            if (contact.getPhones() != null && !contact.getPhones().isEmpty())
                info.append("Phone: ").append(contact.getPhones().get(0).getNumber()).append("\n");
            if (contact.getEmails() != null && !contact.getEmails().isEmpty())
                info.append("Email: ").append(contact.getEmails().get(0).getAddress());
            return info.toString();
        }
        return barcode.getRawValue();
    }

    private String parseLocationInfo(Barcode barcode) {
        if (barcode.getGeoPoint() != null) {
            return "Latitude: "  + barcode.getGeoPoint().getLat()
                    + "\nLongitude: " + barcode.getGeoPoint().getLng();
        }
        return barcode.getRawValue();
    }

    // ── Intent actions ────────────────────────────────────────────────────────

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void sendEmail(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        startActivity(intent);
    }

    private void dialPhone(String phone) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    private void sendSMS(Barcode barcode) {
        if (barcode.getSms() != null) {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + barcode.getSms().getPhoneNumber()));
            intent.putExtra("sms_body", barcode.getSms().getMessage());
            startActivity(intent);
        }
    }

    private void addContact(Barcode barcode) {
        if (barcode.getContactInfo() != null) {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
            startActivity(intent);
        }
    }

    private void openLocation(Barcode barcode) {
        if (barcode.getGeoPoint() != null) {
            String uri = "geo:" + barcode.getGeoPoint().getLat()
                    + "," + barcode.getGeoPoint().getLng();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cb = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("QR Code", text));
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void saveQRCode(String content, String type) {
        QRItem item = new QRItem(content, type, System.currentTimeMillis());
        item.setGenerated(false);
        viewModel.insert(item);
        Toast.makeText(requireContext(), "Saved to history", Toast.LENGTH_SHORT).show();
    }

    private String getQRType(int barcodeType, String content) {
        switch (barcodeType) {
            case Barcode.TYPE_URL:          return "URL";
            case Barcode.TYPE_WIFI:         return "WiFi";
            case Barcode.TYPE_EMAIL:        return "Email";
            case Barcode.TYPE_PHONE:        return "Phone";
            case Barcode.TYPE_SMS:          return "SMS";
            case Barcode.TYPE_CONTACT_INFO: return "Contact";
            case Barcode.TYPE_GEO:          return "Location";
            default:
                if (content.startsWith("http://") || content.startsWith("https://")) return "URL";
                if (content.startsWith("WIFI:"))        return "WiFi";
                if (content.startsWith("mailto:"))      return "Email";
                if (content.startsWith("tel:"))         return "Phone";
                if (content.startsWith("smsto:"))       return "SMS";
                if (content.startsWith("BEGIN:VCARD"))  return "Contact";
                if (content.startsWith("geo:"))         return "Location";
                return "Text";
        }
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            flashEnabled = !flashEnabled;
            camera.getCameraControl().enableTorch(flashEnabled);
            btnFlash.setIcon(ContextCompat.getDrawable(requireContext(),
                    flashEnabled ? R.drawable.ic_flash_on : R.drawable.ic_flash_off));
        }
    }

    private void openGallery() {
        Toast.makeText(requireContext(), "Gallery feature coming soon",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        isScanning = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        isScanning = false;
    }
}