package io.github.mesmerprism.questquestionnaire.questuiautomation;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProjectionPromptActivity extends Activity {
    public static final String ACTION_REQUEST_MEDIA_PROJECTION =
            "io.github.mesmerprism.questquestionnaire.questuiautomation.REQUEST_MEDIA_PROJECTION";
    public static final String EXTRA_AUTO_REQUEST = "autoRequest";
    public static final String EXTRA_FINISH_AFTER_RESULT = "finishAfterResult";
    public static final String RESULT_FILE_NAME = "projection-prompt-result.json";
    public static final String TRACE_FILE_NAME = "projection-prompt-trace.jsonl";

    private static final int REQUEST_MEDIA_PROJECTION = 12040;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appendTrace("onCreate");
        TextView textView = new TextView(this);
        textView.setText("MediaProjection prompt probe");
        setContentView(textView);
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_AUTO_REQUEST, true)) {
            requestMediaProjection();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        appendTrace("onNewIntent");
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_AUTO_REQUEST, true)) {
            requestMediaProjection();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        appendTrace("onActivityResult");
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return;
        }
        writeResult(resultCode, data);
        if (getIntent().getBooleanExtra(EXTRA_FINISH_AFTER_RESULT, true)) {
            finish();
        }
    }

    private void requestMediaProjection() {
        appendTrace("requestMediaProjection");
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        if (manager == null) {
            writeError("MediaProjectionManager unavailable");
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private void writeResult(int resultCode, Intent data) {
        try {
            appendTrace("writeResult");
            JSONObject json = new JSONObject()
                    .put("timestampMs", System.currentTimeMillis())
                    .put("resultCode", resultCode)
                    .put("resultOk", resultCode == Activity.RESULT_OK)
                    .put("resultCanceled", resultCode == Activity.RESULT_CANCELED)
                    .put("hasData", data != null)
                    .put("dataAction", data == null ? "" : safe(data.getAction()))
                    .put("dataType", data == null ? "" : safe(data.getType()))
                    .put("dataExtraCount", data == null || data.getExtras() == null ? 0 : data.getExtras().size());
            writeJson(json);
        } catch (JSONException | IOException exception) {
            throw new IllegalStateException("Could not write MediaProjection prompt result", exception);
        }
    }

    private void writeError(String message) {
        try {
            appendTrace("writeError");
            writeJson(new JSONObject()
                    .put("timestampMs", System.currentTimeMillis())
                    .put("error", message));
        } catch (JSONException | IOException exception) {
            throw new IllegalStateException("Could not write MediaProjection prompt error", exception);
        }
    }

    private void writeJson(JSONObject json) throws IOException {
        File file = new File(getFilesDir(), RESULT_FILE_NAME);
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, false)) {
            writer.write(json.toString());
            writer.write('\n');
        }
    }

    private void appendTrace(String event) {
        try {
            JSONObject json = new JSONObject()
                    .put("timestampMs", System.currentTimeMillis())
                    .put("event", event);
            File file = new File(getFilesDir(), TRACE_FILE_NAME);
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, true)) {
                writer.write(json.toString());
                writer.write('\n');
            }
        } catch (JSONException | IOException ignored) {
            // The instrumentation report is still useful if trace persistence fails.
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
