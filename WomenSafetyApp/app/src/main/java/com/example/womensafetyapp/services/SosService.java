package com.example.womensafetyapp.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.womensafetyapp.R;
import com.example.womensafetyapp.utils.SosHelper;
import com.example.womensafetyapp.utils.SosPrefs;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class SosService extends Service implements SensorEventListener {

    private static final String CH_ID = "sos_channel";
    private static final int NOTI_ID = 7771;

    // Shake config
    private SensorManager sensorManager;
    private Sensor accel;
    private static final float SHAKE_THRESHOLD_G = 2.3f; // nhạy hơn/ít hơn tùy bạn
    private static final int SHAKE_DEBOUNCE_MS = 2500;
    private long lastShakeTime = 0;

    // Voice
    private SpeechRecognizer recognizer;
    private Intent recogIntent;
    private boolean listening = false;

    private CameraDevice cameraDevice;
    private CameraManager cameraManager;
    private ImageReader imageReader;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelAndStart();

        // KHỞI TẠO SHAKE
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // KHỞI TẠO VOICE
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(recognitionListener);
            recogIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recogIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        }

        // Initialize camera
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(reader -> {
            android.media.Image image = reader.acquireLatestImage();
            if (image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                saveImageAndSendMMS(bytes);
                image.close();
            }
        }, null);

        updateListeners();
    }

    private void updateListeners() {
        // SHAKE
        if (SosPrefs.isShakeEnabled(this) && accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        } else if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // VOICE
        if (SosPrefs.isVoiceEnabled(this) && recognizer != null) {
            startVoiceContinuous();
        } else {
            stopVoice();
        }
    }

    private void startVoiceContinuous() {
        if (!SosPrefs.isVoiceEnabled(this)) return;

        // Cẩn thận quyền micro
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return; // không có quyền thì không start để tránh SecurityException
        }

        // Kiểm tra thiết bị có hỗ trợ SpeechRecognizer
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return; // tránh crash trên máy không hỗ trợ
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(recognitionListener);
            recogIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recogIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        }

        if (listening) return;
        listening = true;
        try {
            recognizer.startListening(recogIntent);
        } catch (Exception e) {
            // một số ROM có thể ném lỗi runtime
            listening = false;
        }
    }

    private void stopVoice() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
    }

    private void createChannelAndStart() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "WomenSafety SOS", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }

        // Fallback icon: dùng ic_sos nếu có, nếu không thì dùng ic_launcher
        int smallIconId;
        try {
            smallIconId = getResources().getIdentifier("ic_sos", "drawable", getPackageName());
        } catch (Exception e) {
            smallIconId = 0;
        }
        if (smallIconId == 0) {
            // đảm bảo luôn có icon, tránh Resources$NotFoundException
            smallIconId = getApplicationInfo().icon; // thường là ic_launcher
            if (smallIconId == 0) smallIconId = android.R.drawable.stat_sys_warning;
        }

        Notification noti = new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(smallIconId)
                .setContentTitle("WomenSafety đang bảo vệ bạn")
                .setContentText("Lắng nghe lắc máy / giọng nói để kích hoạt SOS")
                .setOngoing(true)
                .build();

        // BẮT BUỘC: gọi startForeground ngay khi service khởi tạo (Android O+)
        startForeground(NOTI_ID, noti);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateListeners();
        return START_STICKY;
    }

    @Override

    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopVoice();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    // ===== SensorEventListener (SHAKE) =====
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
            float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
            float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;

            float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);
            if (gForce > SHAKE_THRESHOLD_G) {
                long now = System.currentTimeMillis();
                if (now - lastShakeTime >= SHAKE_DEBOUNCE_MS) {
                    lastShakeTime = now;
                    triggerSilentSOS("Phát hiện lắc thiết bị");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Không cần xử lý
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            // Không hiện thông báo khi sẵn sàng để tránh spam
        }

        @Override
        public void onBeginningOfSpeech() {
            // Không hiện thông báo khi bắt đầu nghe
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            // Không hiện thông báo khi kết thúc nghe
            if (listening) {
                try {
                    recognizer.startListening(recogIntent);
                } catch (Exception e) {
                    // Chỉ hiện lỗi nghiêm trọng
                    showToast("Lỗi: Không thể tiếp tục nghe");
                }
            }
        }

        @Override
        public void onError(int error) {
            // Chỉ hiện một số lỗi quan trọng
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                showToast("Cần cấp quyền micro để sử dụng tính năng này");
            } else if (error == SpeechRecognizer.ERROR_NETWORK) {
                showToast("Cần kết nối mạng để nhận diện giọng nói");
            }

            // Restart nếu là lỗi tạm thời
            if (listening && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                try {
                    recognizer.startListening(recogIntent);
                } catch (Exception e) {
                    listening = false;
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0).toLowerCase();

                // Kích hoạt gửi SMS khi nhận diện từ khóa
                if (text.contains("help") || text.contains("cứu") ||
                    text.contains("cuu") || text.contains("sos")) {
                    triggerSilentSOS("Phát hiện lời kêu cứu");
                }
            }

            // Restart để nghe liên tục
            if (listening) {
                try {
                    recognizer.startListening(recogIntent);
                } catch (Exception e) {
                    listening = false;
                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // Không hiện kết quả tạm thời
        }

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };

    private void saveImageAndSendMMS(byte[] imageData) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "SOS_" + timeStamp + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WomenSafety");

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                // Lưu ảnh
                try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(imageUri)) {
                    fos.write(imageData);
                }

                // Lấy danh sách số điện thoại và gửi MMS
                ArrayList<String> phones = SosHelper.getTopEmergencyPhones(this, 3);
                if (!phones.isEmpty()) {
                    SosHelper.getCurrentLocation(this, location -> {
                        String message = SosHelper.buildEmergencyMessage(this, location, "SOS - Kèm ảnh hiện trường");

                        // Gửi MMS cho từng số điện thoại
                        for (String phone : phones) {
                            try {
                                // Tạo intent để gửi MMS
                                Intent mmsIntent = new Intent(Intent.ACTION_SEND);
                                mmsIntent.putExtra("address", phone);
                                mmsIntent.putExtra("sms_body", message);
                                mmsIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                                mmsIntent.setType("image/jpeg");
                                mmsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                mmsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                // Gửi ngay lập tức
                                startActivity(mmsIntent);

                                // Đợi một chút giữa các lần gửi
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                // Nếu không gửi được MMS, gửi SMS thông báo
                                try {
                                    SmsManager sms = SmsManager.getDefault();
                                    String fallbackMessage = message + "\nẢnh đã được lưu tại: " + imageUri.toString();
                                    ArrayList<String> parts = sms.divideMessage(fallbackMessage);
                                    sms.sendMultipartTextMessage(phone, null, parts, null, null);
                                } catch (Exception smsError) {
                                    // Bỏ qua lỗi SMS
                                }
                            }
                        }
                        showToast("Đã gửi SOS kèm ảnh");
                    });
                }
            }
        } catch (Exception e) {
            showToast("Lỗi xử lý ảnh: " + e.getMessage());
        }
    }

    private void capturePhoto() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showToast("Cần cấp quyền camera để chụp ảnh SOS");
            return;
        }

        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // Sử dụng camera sau
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        captureBuilder.addTarget(imageReader.getSurface());
                        camera.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession session) {
                                        try {
                                            session.capture(captureBuilder.build(), null, null);
                                        } catch (Exception e) {
                                            showToast("Lỗi chụp ảnh: " + e.getMessage());
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession session) {
                                        showToast("Không thể cấu hình camera");
                                    }
                                }, null);
                    } catch (Exception e) {
                        showToast("Lỗi thiết lập camera: " + e.getMessage());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    showToast("Lỗi camera: " + error);
                }
            }, null);
        } catch (Exception e) {
            showToast("Không thể mở camera: " + e.getMessage());
        }
    }


    private void sendMMSWithImage(Uri imageUri) {
        ArrayList<String> phones = SosHelper.getTopEmergencyPhones(this, 3);
        if (phones.isEmpty()) {
            showToast("Vui lòng thêm số liên hệ khẩn cấp");
            return;
        }

        SosHelper.getCurrentLocation(this, location -> {
            String message = SosHelper.buildEmergencyMessage(this, location, "SOS - Kèm ảnh hiện trường");

            for (String phone : phones) {
                try {
                    Intent mmsIntent = new Intent(Intent.ACTION_SEND);
                    mmsIntent.putExtra("address", phone);
                    mmsIntent.putExtra("sms_body", message);
                    mmsIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    mmsIntent.setType("image/jpeg");
                    mmsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mmsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(mmsIntent);
                } catch (Exception e) {
                    // Fallback to SMS if MMS fails
                    SmsManager sms = SmsManager.getDefault();
                    ArrayList<String> parts = sms.divideMessage(message + "\n(Không thể gửi ảnh)");
                    sms.sendMultipartTextMessage(phone, null, parts, null, null);
                }
            }
            showToast("Đã gửi SOS kèm ảnh");
        });
    }


    private void triggerSilentSOS(String reason) {
        // Kiểm tra quyền SMS trước
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("Cần cấp quyền SMS để gửi tin nhắn khẩn cấp");
            return;
        }

        // Lấy danh sách số điện thoại khẩn cấp
        ArrayList<String> phones = SosHelper.getTopEmergencyPhones(this, 3);
        if (phones.isEmpty()) {
            showToast("Vui lòng thêm số liên hệ khẩn cấp");
            return;
        }

        showToast("Đang gửi tín hiệu SOS...");

        // Gửi SMS ngay lập tức
        SosHelper.getCurrentLocation(this, location -> {
            String message = SosHelper.buildEmergencyMessage(this, location, reason);
            sendSilentSMS(phones, message);

            // Sau khi gửi SMS, thực hiện chụp ảnh nếu có quyền
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                capturePhoto();
            }
        });

    }

    private void sendSilentSMS(ArrayList<String> phones, String message) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int successCount = 0;
        SmsManager sms = SmsManager.getDefault();

        for (String phone : phones) {
            try {
                ArrayList<String> parts = sms.divideMessage(message);
                sms.sendMultipartTextMessage(phone, null, parts, null, null);
                successCount++;
            } catch (Exception e) {
                // Không hiện chi tiết lỗi
            }
        }

        if (successCount > 0) {
            showToast("Đã gửi tín hiệu SOS");
        }
    }

    private void showToast(String message) {
        android.os.Handler handler = new android.os.Handler(getMainLooper());
        handler.post(() -> android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
    private void startRealtimeTrackingIfAllowed() {
        boolean auto = true;
        try { auto = SosPrefs.isAutoTrackingEnabled(this); } catch (Throwable ignore) {}
        if (!auto) return;

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                    getApplicationContext(),
                    new Intent(getApplicationContext(), com.example.womensafetyapp.services.LiveLocationService.class)
            );
        } catch (Exception e) {
            showToast("Không thể bật theo dõi thời gian thực: " + e.getMessage());
        }
    }

}
