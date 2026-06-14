package io.github.mesmerprism.questquestionnaire.questuiautomation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProjectionRecordingActivity extends Activity {
    public static final String ACTION_RECORD_MEDIA_PROJECTION =
            "io.github.mesmerprism.questquestionnaire.questuiautomation.RECORD_MEDIA_PROJECTION";
    public static final String EXTRA_DURATION_MS = "durationMs";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DPI = "dpi";
    public static final String EXTRA_FRAME_RATE = "frameRate";
    public static final String EXTRA_BIT_RATE = "bitRate";
    public static final String STATUS_FILE_NAME = "media-projection-record-status.json";

    private static final int REQUEST_MEDIA_PROJECTION = 12041;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setText("MediaProjection recording probe");
        setContentView(textView);
        if (savedInstanceState == null) {
            requestMediaProjection();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            writeStatus(this, "projection_denied", null);
            finish();
            return;
        }

        Intent serviceIntent = new Intent(this, ProjectionRecordingService.class);
        serviceIntent.putExtras(getIntent());
        serviceIntent.putExtra(ProjectionRecordingService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(ProjectionRecordingService.EXTRA_RESULT_DATA, data);
        writeStatus(this, "service_start_requested", null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        finish();
    }

    private void requestMediaProjection() {
        writeStatus(this, "projection_request", null);
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        if (manager == null) {
            writeStatus(this, "projection_manager_unavailable", null);
            finish();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    static void writeStatus(Context context, String state, JSONObject details) {
        try {
            JSONObject status = new JSONObject()
                    .put("timestampMs", System.currentTimeMillis())
                    .put("state", state);
            if (details != null) {
                status.put("details", details);
            }
            File root = context.getExternalFilesDir(null);
            if (root == null) {
                root = context.getFilesDir();
            }
            File file = new File(root, STATUS_FILE_NAME);
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, false)) {
                writer.write(status.toString());
                writer.write('\n');
            }
        } catch (IOException | JSONException ignored) {
            // The recording output remains the primary artifact.
        }
    }

    static JSONObject errorJson(Exception exception) {
        JSONObject json = new JSONObject();
        try {
            json.put("class", exception.getClass().getName());
            json.put("message", exception.getMessage() == null ? "" : exception.getMessage());
        } catch (JSONException ignored) {
            // Best effort.
        }
        return json;
    }
}
