package io.github.mesmerprism.questquestionnaire.questuiautomation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ProjectionRecordingService extends Service {
    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL_ID = "quest-recording-probe";
    private static final int NOTIFICATION_ID = 12042;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            handler.post(() -> {
                if (!stopping) {
                    stopRecording("projection_callback_stop");
                }
            });
        }
    };

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean stopping;
    private JSONObject pendingError;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startProjectionForeground();
        if (intent == null) {
            pendingError = errorDetails("java.lang.IllegalArgumentException", "Missing service intent.");
            stopRecording("missing_intent");
            return START_NOT_STICKY;
        }
        startRecording(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (!stopping) {
            stopRecording("service_destroyed");
        }
        super.onDestroy();
    }

    private void startRecording(Intent intent) {
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            if (manager == null) {
                throw new IllegalStateException("MediaProjectionManager unavailable.");
            }
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = getResultData(intent);
            if (resultCode == 0 || resultData == null) {
                throw new IllegalArgumentException("Missing MediaProjection result data.");
            }

            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                throw new IllegalStateException("MediaProjection unavailable.");
            }
            mediaProjection.registerCallback(projectionCallback, handler);

            CaptureConfig config = CaptureConfig.from(this, intent);
            outputFile = nextOutputFile();
            mediaRecorder = new MediaRecorder(this);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(config.bitRate);
            mediaRecorder.setVideoFrameRate(config.frameRate);
            mediaRecorder.setVideoSize(config.width, config.height);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            mediaRecorder.prepare();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "quest-questionnaire-panel-comparison",
                    config.width,
                    config.height,
                    config.dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(),
                    null,
                    handler
            );
            mediaRecorder.start();
            ProjectionRecordingActivity.writeStatus(this, "recording_started", config.toJson()
                    .put("outputFile", outputFile.getAbsolutePath()));
            handler.postDelayed(
                    () -> stopRecording("duration_elapsed"),
                    Math.max(config.durationMs, 1000)
            );
        } catch (Exception exception) {
            pendingError = ProjectionRecordingActivity.errorJson(exception);
            ProjectionRecordingActivity.writeStatus(this, "recording_error", pendingError);
            stopRecording("error_cleanup");
        }
    }

    private Intent getResultData(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void stopRecording(String reason) {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        Exception stopError = null;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception exception) {
                stopError = exception;
            }
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(projectionCallback);
            } catch (Exception ignored) {
                // Callback cleanup is best-effort.
            }
            mediaProjection.stop();
            mediaProjection = null;
        }
        JSONObject details = new JSONObject();
        try {
            details.put("reason", reason);
            if (outputFile != null) {
                details.put("outputFile", outputFile.getAbsolutePath());
                details.put("bytes", outputFile.isFile() ? outputFile.length() : 0);
            }
            if (pendingError != null) {
                details.put("recordingError", pendingError);
            }
            if (stopError != null) {
                details.put("stopError", ProjectionRecordingActivity.errorJson(stopError));
            }
        } catch (JSONException ignored) {
            // Status details are best-effort lab evidence only.
        }
        ProjectionRecordingActivity.writeStatus(
                this,
                stopError == null ? "recording_stopped" : "recording_stop_error",
                details
        );
        stopForeground(true);
        stopSelf();
    }

    private void startProjectionForeground() {
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Quest recording probe",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent launchIntent = new Intent(this, ProjectionRecordingActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("Quest recording probe")
                .setContentText("Recording comparison capture is active")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private File nextOutputFile() {
        File dir = getExternalFilesDir("recordings");
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Could not create recording directory: " + dir);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return new File(dir, "media-projection-" + stamp + ".mp4");
    }

    private static JSONObject errorDetails(String className, String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("class", className);
            json.put("message", message);
        } catch (JSONException ignored) {
            // Best effort.
        }
        return json;
    }

    private static final class CaptureConfig {
        final int durationMs;
        final int width;
        final int height;
        final int dpi;
        final int frameRate;
        final int bitRate;

        private CaptureConfig(int durationMs, int width, int height, int dpi, int frameRate, int bitRate) {
            this.durationMs = durationMs;
            this.width = width;
            this.height = height;
            this.dpi = dpi;
            this.frameRate = frameRate;
            this.bitRate = bitRate;
        }

        static CaptureConfig from(Service service, Intent intent) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = service.getSystemService(WindowManager.class);
            if (windowManager != null) {
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
            }
            int defaultWidth = metrics.widthPixels > 0 ? metrics.widthPixels : 1920;
            int defaultHeight = metrics.heightPixels > 0 ? metrics.heightPixels : 1080;
            int defaultDpi = metrics.densityDpi > 0 ? metrics.densityDpi : 320;
            return new CaptureConfig(
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_DURATION_MS, 15000),
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_WIDTH, defaultWidth),
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_HEIGHT, defaultHeight),
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_DPI, defaultDpi),
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_FRAME_RATE, 60),
                    intent.getIntExtra(ProjectionRecordingActivity.EXTRA_BIT_RATE, 40000000)
            );
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("durationMs", durationMs)
                    .put("width", width)
                    .put("height", height)
                    .put("dpi", dpi)
                    .put("frameRate", frameRate)
                    .put("bitRate", bitRate);
        }
    }
}
