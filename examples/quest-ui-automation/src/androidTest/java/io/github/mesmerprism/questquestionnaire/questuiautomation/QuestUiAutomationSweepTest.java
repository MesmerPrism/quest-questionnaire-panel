package io.github.mesmerprism.questquestionnaire.questuiautomation;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@RunWith(AndroidJUnit4.class)
public final class QuestUiAutomationSweepTest {
    private static final String TAG = "QuestUiSweep";
    private static final String METACAM_SHARING_ACTIVITY =
            "com.oculus.metacam/com.oculus.panelapp.sharing.SharingPanelActivity";
    private static final String DEFAULT_SCROLL_TARGET_RES_REGEX = ".*settings_recycler_view.*";
    private static final Pattern DEFAULT_CANDIDATE_PATTERN = Pattern.compile(
            "record|video|capture|camera|share|start|stop|done|cancel|allow|microphone|mic|audio|" +
                    "quality|indicator|settings|scroll|frame|rate|bit|stabilization|eye|view|aspect",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEDIA_PROJECTION_PROMPT_PATTERN = Pattern.compile(
            "screen|record|capture|share|start now|start|allow|cancel",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    public void runSweep() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Bundle args = InstrumentationRegistry.getArguments();
        UiDevice device = UiDevice.getInstance(instrumentation);
        String scenario = args.getString("scenario", "metacamPanel");
        Report report = Report.create(instrumentation.getTargetContext(), scenario);

        try {
            report.event("start", new JSONObject()
                    .put("scenario", scenario)
                    .put("package", instrumentation.getTargetContext().getPackageName()));
            report.command("product", device.executeShellCommand("getprop ro.product.model").trim());
            report.command("sdk", device.executeShellCommand("getprop ro.build.version.sdk").trim());
            report.command("focus_before", focusSummary(device));

            if ("currentWindow".equals(scenario)) {
                dumpAndClassify(device, report, "current_window", DEFAULT_CANDIDATE_PATTERN);
                dumpAccessibilityState(instrumentation, report, "current_window");
                runOptionalSettingsScrolls(device, report, args);
            } else if ("surfaceMap".equals(scenario)) {
                runSurfaceMap(instrumentation, device, report, args);
            } else if ("systemSurfaceReachability".equals(scenario)) {
                runSystemSurfaceReachability(instrumentation, device, report, args);
            } else if ("scrollProbe".equals(scenario)) {
                runScrollProbe(instrumentation, device, report, args);
            } else if ("settingsNavProbe".equals(scenario)) {
                runSettingsNavProbe(instrumentation, device, report, args);
            } else if ("settingsSectionCrawler".equals(scenario)) {
                runSettingsSectionCrawler(instrumentation, device, report, args);
            } else if ("settingsChildPageProbe".equals(scenario)) {
                runSettingsChildPageProbe(instrumentation, device, report, args);
            } else if ("settingsRecoveryProbe".equals(scenario)) {
                runSettingsRecoveryProbe(instrumentation, device, report, args);
            } else if ("mediaProjectionPrompt".equals(scenario)) {
                runMediaProjectionPromptProbe(instrumentation, device, report, args);
            } else if ("metacamPanel".equals(scenario)) {
                runMetacamPanelSweep(device, report, args);
            } else if ("metacamRecordProbe".equals(scenario)) {
                runMetacamRecordProbe(device, report, args);
            } else {
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
            }

            report.command("focus_after", focusSummary(device));
            report.event("finish", new JSONObject().put("report", report.reportFile.getAbsolutePath()));
            assertTrue("Report file should exist", report.reportFile.isFile());
        } finally {
            report.close();
        }
    }

    private static void runSurfaceMap(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        String surface = args.getString("surface", "current");
        UiSnapshot snapshot = prepareSurface(device, report, surface);
        report.command("display_cmd", shell(device, "cmd display get-displays 2>/dev/null || true"));
        report.command("display_dumpsys", shell(
                device,
                "dumpsys display | sed -n '1,220p'"
        ));
        report.command("window_displays", shell(
                device,
                "dumpsys window displays | sed -n '1,220p'"
        ));
        String shellDumpPath = "/data/local/tmp/" + report.dir.getName() + "-surface-map-shell-compressed.xml";
        report.event("shell_uiautomator_dump_external", new JSONObject()
                .put("path", shellDumpPath)
                .put("command", "uiautomator dump --compressed " + shellQuote(shellDumpPath))
                .put("note", "Run this through host adb shell; nested shell uiautomator dump did not create a file reliably during Quest instrumentation."));
        report.event("surface_map_snapshot", new JSONObject()
                .put("surface", surface)
                .put("xml", snapshot.xmlFile.getAbsolutePath())
                .put("nodeCount", snapshot.nodes.size()));
        dumpAccessibilityState(instrumentation, report, "surface_map_" + surface);
    }

    private static void runSystemSurfaceReachability(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        List<String> surfaces = parseCsv(args.getString(
                "surfaces",
                "current,quickSettings,notifications,androidSettings,metacamPanel"
        ));
        int waitAfterSurfaceMs = parseInt(args.getString("waitAfterSurfaceMs", "1000"), 1000);

        report.event("system_surface_probe_start", new JSONObject()
                .put("surfaces", new JSONArray(surfaces))
                .put("waitAfterSurfaceMs", waitAfterSurfaceMs)
                .put("safety", "Passive reachability diagnostics: no row toggles, dropdown selections, recorder start/stop, force-stop, package kill, or settings mutation."));

        String previousHash = "";
        for (int index = 0; index < surfaces.size(); index += 1) {
            String surface = surfaces.get(index);
            try {
                UiSnapshot snapshot = prepareSurface(device, report, surface);
                device.waitForIdle(3000);
                Thread.sleep(Math.max(waitAfterSurfaceMs, 0));
                JSONObject attempt = systemSurfaceAttempt(surface, index, snapshot, previousHash);
                report.event("system_surface_attempt", attempt);
                dumpAccessibilityState(instrumentation, report, "system_surface_" + sanitizeName(surface));
                previousHash = attempt.optString("visibleTextHash", "");
            } catch (Exception exception) {
                report.event("system_surface_error", new JSONObject()
                        .put("surface", surface)
                        .put("index", index)
                        .put("errorClass", exception.getClass().getName())
                        .put("message", exception.getMessage() == null ? "" : exception.getMessage()));
            }
        }
    }

    private static void runSettingsRecoveryProbe(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        int retryCount = parseInt(args.getString("retryCount", "2"), 2);
        int retryWaitMs = parseInt(args.getString("retryWaitMs", "1500"), 1500);
        boolean dumpPassiveBaselines = parseBoolean(args.getString("dumpPassiveBaselines", "true"), true);

        report.event("settings_recovery_start", new JSONObject()
                .put("retryCount", retryCount)
                .put("retryWaitMs", retryWaitMs)
                .put("dumpPassiveBaselines", dumpPassiveBaselines)
                .put("safety", "Passive diagnostics only: no force-stop, package kill, setting toggle, or coordinate recovery path."));

        UiSnapshot initial = prepareSurface(device, report, "androidSettings");
        report.event("settings_recovery_attempt", settingsRecoveryAttempt("initial", 0, initial));
        dumpAccessibilityState(instrumentation, report, "settings_recovery_initial");

        UiSnapshot latest = initial;
        if (initial.nodes.isEmpty()) {
            report.event("settings_recovery_zero_node", emptySettingsSurfaceEvent("initial")
                    .put("phase", "initial")
                    .put("attempt", 0));
            if (dumpPassiveBaselines) {
                report.command("settings_recovery_focus_after_zero", focusSummary(device));
                report.command("settings_recovery_window_displays", shell(
                        device,
                        "dumpsys window displays | sed -n '1,220p'"
                ));
                UiSnapshot current = dumpAndClassify(
                        device,
                        report,
                        "settings_recovery_current_window",
                        DEFAULT_CANDIDATE_PATTERN
                );
                report.event("settings_recovery_passive_baseline", settingsRecoveryAttempt("currentWindow", 0, current));
                dumpAccessibilityState(instrumentation, report, "settings_recovery_current_window");
            }

            for (int attempt = 1; attempt <= retryCount; attempt += 1) {
                Thread.sleep(Math.max(retryWaitMs, 0));
                latest = prepareSurface(device, report, "androidSettings");
                report.event("settings_recovery_attempt", settingsRecoveryAttempt("retry", attempt, latest));
                dumpAccessibilityState(instrumentation, report, "settings_recovery_retry_" + attempt);
                if (!latest.nodes.isEmpty()) {
                    break;
                }
            }
        }

        report.event("settings_recovery_result", new JSONObject()
                .put("initialNodeCount", initial.nodes.size())
                .put("finalNodeCount", latest.nodes.size())
                .put("initialZeroNode", initial.nodes.isEmpty())
                .put("settingsVisible", !latest.nodes.isEmpty())
                .put("recovered", initial.nodes.isEmpty() && !latest.nodes.isEmpty())
                .put("retryCount", retryCount)
                .put("passiveOnly", true));
    }

    private static void runSettingsNavProbe(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        List<String> targets = parseCsv(args.getString(
                "navTargets",
                "general,notifications,display_brightness,audio,camera,accessibility,developer,help"
        ));
        int maxNavScrolls = parseInt(args.getString("maxNavScrolls", "8"), 8);
        boolean resetEachTarget = parseBoolean(args.getString("resetSettingsEachTarget", "true"), true);

        report.event("settings_nav_probe_start", new JSONObject()
                .put("targets", new JSONArray(targets))
                .put("maxNavScrolls", maxNavScrolls)
                .put("resetSettingsEachTarget", resetEachTarget));

        UiSnapshot initialSettings = prepareSurface(device, report, "androidSettings");
        if (initialSettings.nodes.isEmpty()) {
            report.event("settings_nav_probe_unavailable", emptySettingsSurfaceEvent("initial"));
            return;
        }
        dumpAccessibilityState(instrumentation, report, "settings_nav_initial");

        for (String target : targets) {
            if (resetEachTarget) {
                UiSnapshot preparedSettings = prepareSurface(device, report, "androidSettings");
                if (preparedSettings.nodes.isEmpty()) {
                    report.event("settings_nav_target", emptySettingsSurfaceEvent(target)
                            .put("target", target));
                    continue;
                }
            }
            UiSnapshot before = dumpAndClassify(
                    device,
                    report,
                    "settings_nav_before_" + sanitizeName(target),
                    DEFAULT_CANDIDATE_PATTERN
            );
            JSONObject outcome = clickSettingsNavTarget(device, target, maxNavScrolls);
            report.event("settings_nav_target", outcome);
            device.waitForIdle(3000);
            Thread.sleep(parseInt(args.getString("postNavWaitMs", "1200"), 1200));
            UiSnapshot after = dumpAndClassify(
                    device,
                    report,
                    "settings_nav_after_" + sanitizeName(target),
                    DEFAULT_CANDIDATE_PATTERN
            );
            report.event("settings_nav_delta", new JSONObject()
                    .put("target", target)
                    .put("beforeVisibleTextHash", visibleTextHash(before.nodes))
                    .put("afterVisibleTextHash", visibleTextHash(after.nodes))
                    .put("newVisibleTexts", newVisibleTexts(before.nodes, after.nodes)));
            dumpAccessibilityState(instrumentation, report, "settings_nav_after_" + target);
        }
    }

    private static void runSettingsChildPageProbe(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        List<String> rawTargets = parseCsv(args.getString(
                "childTargets",
                "camera:Bit rate,camera:Frame rate,camera:Image stabilization,camera:Eye perspective," +
                        "help:Help & Tips app,help:Support,privacy_safety:Device permissions,privacy_safety:App permissions"
        ));
        int maxNavScrolls = parseInt(args.getString("maxNavScrolls", "10"), 10);
        int maxContentScrolls = parseInt(args.getString("maxContentScrolls", "4"), 4);
        List<String> clickModes = parseCsv(args.getString("clickModes", args.getString("clickMode", "coordinate")));
        String childTargetRole = normalizeChildTargetRole(args.getString(
                "childTargetRole",
                args.getString("targetRole", "row")
        ));
        boolean dumpChildAccessibility = parseBoolean(args.getString("dumpChildAccessibility", "false"), false);
        String optionTarget = args.getString("optionTarget", "");
        Map<String, String> optionTargets = parseOptionTargets(args.getString("optionTargets", ""));
        boolean allowOptionSelect = parseBoolean(args.getString("allowOptionSelect", "false"), false);
        String optionClickMode = normalizeChildClickMode(args.getString("optionClickMode", "coordinate"));
        if (clickModes.isEmpty()) {
            clickModes.add("coordinate");
        }

        JSONArray parsedTargets = new JSONArray();
        List<ChildTargetSpec> targets = new ArrayList<>();
        for (String rawTarget : rawTargets) {
            ChildTargetSpec spec = ChildTargetSpec.parse(rawTarget);
            if (spec != null) {
                targets.add(spec);
                parsedTargets.put(spec.toJson());
            }
        }

        report.event("settings_child_probe_start", new JSONObject()
                .put("targets", parsedTargets)
                .put("maxNavScrolls", maxNavScrolls)
                .put("maxContentScrolls", maxContentScrolls)
                .put("clickModes", new JSONArray(clickModes))
                .put("childTargetRole", childTargetRole)
                .put("dumpChildAccessibility", dumpChildAccessibility)
                .put("optionTarget", optionTarget)
                .put("optionTargets", optionTargetsToJson(optionTargets))
                .put("allowOptionSelect", allowOptionSelect)
                .put("optionClickMode", optionClickMode)
                .put("optionSafety", "Dropdown option targeting is a dry run unless allowOptionSelect=true.")
                .put("safety", "Only matching non-checkable main-content rows are clicked; the probe dumps the child surface and presses Back."));

        for (ChildTargetSpec spec : targets) {
            for (String rawClickMode : clickModes) {
                String clickMode = normalizeChildClickMode(rawClickMode);
                UiSnapshot preparedSettings = prepareSurface(device, report, "androidSettings");
                if (preparedSettings.nodes.isEmpty()) {
                    report.event("settings_child_skip", new JSONObject()
                            .put("section", spec.section)
                            .put("label", spec.label)
                            .put("childTargetRole", childTargetRole)
                            .put("clickMode", clickMode)
                            .put("reason", "prepared settings surface had zero nodes"));
                    continue;
                }
                JSONObject navOutcome = clickSettingsNavTarget(device, spec.section, maxNavScrolls);
                report.event("settings_child_nav_target", navOutcome
                        .put("childLabel", spec.label)
                        .put("childTargetRole", childTargetRole)
                        .put("clickMode", clickMode));
                device.waitForIdle(3000);
                Thread.sleep(parseInt(args.getString("postNavWaitMs", "1200"), 1200));

                if (!navOutcome.optBoolean("clicked", false)) {
                    report.event("settings_child_skip", new JSONObject()
                            .put("section", spec.section)
                            .put("label", spec.label)
                            .put("childTargetRole", childTargetRole)
                            .put("clickMode", clickMode)
                            .put("reason", "navigation target not clicked"));
                    continue;
                }

                JSONObject clickOutcome = findAndClickSettingsChildTarget(
                        instrumentation,
                        device,
                        report,
                        spec,
                        maxContentScrolls,
                        clickMode,
                        childTargetRole
                );
                report.event("settings_child_click_target", clickOutcome);
                if (!clickOutcome.optBoolean("clicked", false)) {
                    continue;
                }

                device.waitForIdle(3000);
                Thread.sleep(parseInt(args.getString("postChildClickWaitMs", "1400"), 1400));
                String nameSuffix = sanitizeName(spec.section) + "_" + sanitizeName(spec.label) + "_" +
                        sanitizeName(childTargetRole) + "_" + sanitizeName(clickMode);
                UiSnapshot childSnapshot = dumpAndClassify(
                        device,
                        report,
                        "settings_child_after_" + nameSuffix,
                        DEFAULT_CANDIDATE_PATTERN
                );
                JSONObject childSummary = settingsContentSummary(childSnapshot);
                JSONObject clickedPageSummary = clickOutcome.optJSONObject("clickedPageSummary");
                String clickedPageHash = clickedPageSummary == null ? "" : clickedPageSummary.optString("visibleTextHash", "");
                report.event("settings_child_surface", new JSONObject()
                        .put("section", spec.section)
                        .put("label", spec.label)
                        .put("childTargetRole", childTargetRole)
                        .put("clickMode", clickMode)
                        .put("differsFromClickedPage", !clickedPageHash.equals(childSummary.optString("visibleTextHash", "")))
                        .put("clickedPageSummary", clickedPageSummary == null ? JSONObject.NULL : clickedPageSummary)
                        .put("summary", childSummary));
                if (dumpChildAccessibility) {
                    dumpAccessibilityState(instrumentation, report, "settings_child_after_" + nameSuffix);
                }

                String requestedOption = optionTargetForSpec(spec, optionTargets, optionTarget);
                if ("dropdown".equals(childTargetRole) && !requestedOption.trim().isEmpty()) {
                    JSONObject optionOutcome = handleSettingsDropdownOptionTarget(
                            instrumentation,
                            device,
                            childSnapshot,
                            requestedOption,
                            allowOptionSelect,
                            optionClickMode
                    );
                    report.event("settings_dropdown_option_target", new JSONObject()
                            .put("section", spec.section)
                            .put("label", spec.label)
                            .put("childTargetRole", childTargetRole)
                            .put("clickMode", clickMode)
                            .put("outcome", optionOutcome));
                    if (optionOutcome.optBoolean("clicked", false)) {
                        device.waitForIdle(3000);
                        Thread.sleep(parseInt(args.getString("postOptionClickWaitMs", "1000"), 1000));
                        UiSnapshot afterOption = dumpAndClassify(
                                device,
                                report,
                                "settings_child_after_option_" + nameSuffix,
                                DEFAULT_CANDIDATE_PATTERN
                        );
                        report.event("settings_dropdown_option_after_click", new JSONObject()
                                .put("section", spec.section)
                                .put("label", spec.label)
                                .put("optionTarget", requestedOption)
                                .put("summary", settingsContentSummary(afterOption)));
                    }
                }

                device.pressBack();
                device.waitForIdle(3000);
                Thread.sleep(800);
                UiSnapshot afterBack = dumpAndClassify(
                        device,
                        report,
                        "settings_child_after_back_" + nameSuffix,
                        DEFAULT_CANDIDATE_PATTERN
                );
                report.event("settings_child_after_back", new JSONObject()
                        .put("section", spec.section)
                        .put("label", spec.label)
                        .put("childTargetRole", childTargetRole)
                        .put("clickMode", clickMode)
                        .put("summary", settingsContentSummary(afterBack)));
            }
        }
    }

    private static void runSettingsSectionCrawler(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        List<String> targets = parseCsv(args.getString(
                "navTargets",
                "display_brightness,audio,camera,privacy_safety,developer,help"
        ));
        int maxNavScrolls = parseInt(args.getString("maxNavScrolls", "10"), 10);
        int maxSectionScrolls = parseInt(args.getString("maxSectionScrolls", "4"), 4);
        boolean coordinateFallback = parseBoolean(args.getString("mainCoordinateFallback", "false"), false);

        report.event("settings_section_crawler_start", new JSONObject()
                .put("targets", new JSONArray(targets))
                .put("maxNavScrolls", maxNavScrolls)
                .put("maxSectionScrolls", maxSectionScrolls)
                .put("mainCoordinateFallback", coordinateFallback));

        for (String target : targets) {
            UiSnapshot preparedSettings = prepareSurface(device, report, "androidSettings");
            if (preparedSettings.nodes.isEmpty()) {
                report.event("settings_section_skip", emptySettingsSurfaceEvent(target)
                        .put("target", target));
                continue;
            }
            JSONObject navOutcome = clickSettingsNavTarget(device, target, maxNavScrolls);
            report.event("settings_section_nav_target", navOutcome);
            device.waitForIdle(3000);
            Thread.sleep(parseInt(args.getString("postNavWaitMs", "1200"), 1200));

            if (!navOutcome.optBoolean("clicked", false)) {
                report.event("settings_section_skip", new JSONObject()
                        .put("target", target)
                        .put("reason", "navigation target not clicked"));
                continue;
            }

            dumpAccessibilityState(instrumentation, report, "settings_section_initial_" + target);
            crawlSettingsSection(device, report, target, maxSectionScrolls, coordinateFallback);
        }
    }

    private static void crawlSettingsSection(
            UiDevice device,
            Report report,
            String target,
            int maxSectionScrolls,
            boolean coordinateFallback
    ) throws Exception {
        String previousHash = "";
        for (int pageIndex = 0; pageIndex <= maxSectionScrolls; pageIndex += 1) {
            UiSnapshot snapshot = dumpAndClassify(
                    device,
                    report,
                    "settings_section_" + sanitizeName(target) + "_page_" + pageIndex,
                    DEFAULT_CANDIDATE_PATTERN
            );
            String hash = visibleTextHash(snapshot.nodes);
            report.event("settings_section_page", new JSONObject()
                    .put("target", target)
                    .put("pageIndex", pageIndex)
                    .put("nodeCount", snapshot.nodes.size())
                    .put("visibleTextHash", hash)
                    .put("sameAsPrevious", hash.equals(previousHash))
                    .put("visibleTexts", visibleTexts(snapshot.nodes, 80))
                    .put("checkedNodes", checkedNodeSummaries(snapshot.nodes, 40))
                    .put("clickableNodes", clickableNodeSummaries(snapshot.nodes, 80))
                    .put("settingsContentTexts", settingsContentTexts(snapshot.nodes, 80))
                    .put("settingsContentCheckedNodes", settingsContentCheckedNodeSummaries(snapshot.nodes, 40))
                    .put("settingsContentClickableNodes", settingsContentClickableNodeSummaries(snapshot.nodes, 80)));
            report.event("settings_section_route_inventory", settingsRouteInventory(snapshot.nodes, target, pageIndex, 120));
            previousHash = hash;

            if (pageIndex == maxSectionScrolls) {
                break;
            }

            JSONObject scrollOutcome = scrollMainSettingsContent(device, pageIndex + 1, coordinateFallback);
            report.event("settings_section_scroll", scrollOutcome.put("target", target));
            device.waitForIdle(3000);
            Thread.sleep(1000);
            if (!scrollOutcome.optBoolean("moved", false)) {
                break;
            }
        }
    }

    private static JSONObject scrollMainSettingsContent(
            UiDevice device,
            int index,
            boolean coordinateFallback
    ) throws JSONException {
        UiObject2 main = findObjectByResourceRegex(device, ".*settings_recycler_view.*");
        JSONObject outcome = new JSONObject()
                .put("index", index)
                .put("targetResRegex", ".*settings_recycler_view.*")
                .put("found", main != null)
                .put("coordinateFallbackEnabled", coordinateFallback);
        if (main == null) {
            return outcome.put("moved", false);
        }
        outcome.put("target", uiObjectSummary(main));
        boolean objectScrolled = false;
        try {
            main.setGestureMarginPercentage(0.12f);
            objectScrolled = main.scroll(Direction.DOWN, 0.75f, 1000);
        } catch (Exception exception) {
            outcome.put("objectScrollError", exception.toString());
        }
        boolean coordinateSwiped = false;
        if (!objectScrolled && coordinateFallback) {
            coordinateSwiped = device.swipe(900, 720, 900, 240, 50);
        }
        return outcome
                .put("objectScrolled", objectScrolled)
                .put("coordinateSwiped", coordinateSwiped)
                .put("moved", objectScrolled || coordinateSwiped);
    }

    private static JSONObject findAndClickSettingsChildTarget(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            ChildTargetSpec spec,
            int maxContentScrolls,
            String clickMode,
            String childTargetRole
    ) throws Exception {
        Pattern pattern = spec.pattern();
        JSONArray searchPages = new JSONArray();
        for (int pageIndex = 0; pageIndex <= maxContentScrolls; pageIndex += 1) {
            UiSnapshot snapshot = dumpAndClassify(
                    device,
                    report,
                    "settings_child_search_" + sanitizeName(spec.section) + "_" + sanitizeName(spec.label) + "_" +
                            sanitizeName(childTargetRole) + "_" + sanitizeName(clickMode) + "_page_" + pageIndex,
                    DEFAULT_CANDIDATE_PATTERN
            );
            UiNode targetNode = findSettingsContentClickNode(snapshot.nodes, pattern, childTargetRole);
            searchPages.put(new JSONObject()
                    .put("pageIndex", pageIndex)
                    .put("summary", settingsContentSummary(snapshot))
                    .put("found", targetNode != null)
                    .put("targetNode", targetNode == null ? JSONObject.NULL : targetNode.toJson()));
            if (targetNode != null && targetNode.bounds != null) {
                JSONObject clickedPageSummary = settingsContentSummary(snapshot);
                JSONObject clickAction = clickSettingsChildTarget(instrumentation, device, targetNode, clickMode);
                return new JSONObject()
                        .put("section", spec.section)
                        .put("label", spec.label)
                        .put("pattern", pattern.pattern())
                        .put("childTargetRole", childTargetRole)
                        .put("clickMode", clickMode)
                        .put("clicked", clickAction.optBoolean("clicked", false))
                        .put("pageIndex", pageIndex)
                        .put("targetNode", targetNode.toJson())
                        .put("clickAction", clickAction)
                        .put("clickedPageSummary", clickedPageSummary)
                        .put("searchPages", searchPages);
            }
            if (pageIndex == maxContentScrolls) {
                break;
            }
            JSONObject scrollOutcome = scrollMainSettingsContent(device, pageIndex + 1, false);
            report.event("settings_child_search_scroll", scrollOutcome
                    .put("section", spec.section)
                    .put("label", spec.label));
            device.waitForIdle(3000);
            Thread.sleep(1000);
            if (!scrollOutcome.optBoolean("moved", false)) {
                break;
            }
        }
        return new JSONObject()
                .put("section", spec.section)
                .put("label", spec.label)
                .put("pattern", pattern.pattern())
                .put("childTargetRole", childTargetRole)
                .put("clickMode", clickMode)
                .put("clicked", false)
                .put("searchPages", searchPages);
    }

    private static JSONObject clickSettingsChildTarget(
            Instrumentation instrumentation,
            UiDevice device,
            UiNode targetNode,
            String clickMode
    ) throws JSONException {
        String normalizedMode = normalizeChildClickMode(clickMode);
        JSONObject outcome = new JSONObject()
                .put("clickMode", normalizedMode)
                .put("targetNode", targetNode.toJson());
        if (targetNode.bounds == null) {
            return outcome.put("clicked", false).put("error", "target node has no bounds");
        }
        if ("coordinate".equals(normalizedMode)) {
            int x = targetNode.bounds.centerX();
            int y = targetNode.bounds.centerY();
            boolean clicked = device.click(x, y);
            return outcome
                    .put("clicked", clicked)
                    .put("x", x)
                    .put("y", y);
        }
        if ("uiObject2".equals(normalizedMode)) {
            UiObject2 object = findUiObjectByBounds(device, targetNode.bounds);
            if (object == null) {
                return outcome.put("clicked", false).put("error", "no safe matching UiObject2");
            }
            try {
                object.click();
                return outcome
                        .put("clicked", true)
                        .put("uiObject", uiObjectSummary(object));
            } catch (Exception exception) {
                return outcome
                        .put("clicked", false)
                        .put("uiObject", uiObjectSummary(object))
                        .put("error", exception.toString())
                        .put("errorClass", exception.getClass().getName());
            }
        }
        if ("accessibilityClick".equals(normalizedMode) || "accessibilityExpand".equals(normalizedMode)) {
            int actionId = "accessibilityExpand".equals(normalizedMode)
                    ? AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId()
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId();
            AccessibilityNodeInfo node = findAccessibilityActionTarget(
                    instrumentation.getUiAutomation(),
                    targetNode,
                    actionId
            );
            if (node == null) {
                return outcome
                        .put("clicked", false)
                        .put("action", actionName(actionId))
                        .put("error", "no safe matching accessibility node");
            }
            boolean result = false;
            try {
                result = node.performAction(actionId);
                return outcome
                        .put("clicked", result)
                        .put("action", actionName(actionId))
                        .put("accessibilityNode", accessibilityNodeSummary(node, "target", 0));
            } catch (Exception exception) {
                return outcome
                        .put("clicked", false)
                        .put("action", actionName(actionId))
                        .put("accessibilityNode", accessibilityNodeSummary(node, "target", 0))
                        .put("error", exception.toString())
                        .put("errorClass", exception.getClass().getName());
            }
        }
        return outcome.put("clicked", false).put("error", "unknown click mode");
    }

    private static UiNode findSettingsContentClickNode(
            List<UiNode> nodes,
            Pattern pattern,
            String childTargetRole
    ) {
        List<UiNode> labelMatches = new ArrayList<>();
        for (UiNode node : nodes) {
            if (!isSettingsContentNode(node) || node.bounds == null) {
                continue;
            }
            if (pattern.matcher(node.searchText()).find()) {
                labelMatches.add(node);
            }
        }
        for (UiNode labelNode : labelMatches) {
            UiNode rowTarget = findNearestSafeClickableOnSameRow(nodes, labelNode, childTargetRole);
            if (rowTarget != null) {
                return rowTarget;
            }
        }
        return null;
    }

    private static UiNode findNearestSafeClickableOnSameRow(
            List<UiNode> nodes,
            UiNode labelNode,
            String childTargetRole
    ) {
        UiNode best = null;
        int bestDistance = Integer.MAX_VALUE;
        int labelCenterY = labelNode.bounds.centerY();
        for (UiNode node : nodes) {
            if (!isSafeSettingsContentClickTarget(node) || node.bounds == null) {
                continue;
            }
            if ("dropdown".equals(childTargetRole) && !isDropdownSettingsTarget(node)) {
                continue;
            }
            boolean sameRow = node.bounds.top <= labelCenterY && node.bounds.bottom >= labelCenterY;
            int distance = Math.abs(node.bounds.centerY() - labelCenterY);
            if (!sameRow && distance > 96) {
                continue;
            }
            int score = distance + clickTargetPenalty(node, childTargetRole);
            if (score < bestDistance) {
                best = node;
                bestDistance = score;
            }
        }
        return best;
    }

    private static int clickTargetPenalty(UiNode node, String childTargetRole) {
        String resource = node.resourceId == null ? "" : node.resourceId;
        String className = node.className == null ? "" : node.className;
        if ("dropdown".equals(childTargetRole)) {
            if (resource.contains("dropdown_button") || className.contains("Spinner")) {
                return -260;
            }
            if (resource.contains("dropdown_container") || resource.contains("right_view_group")) {
                return -180;
            }
        }
        if (resource.contains("settings_list_item")) {
            return -200;
        }
        if (resource.contains("dropdown_container") || resource.contains("right_view_group")) {
            return -150;
        }
        if (resource.contains("subtitle") || resource.contains("title")) {
            return 120;
        }
        return 0;
    }

    private static boolean isDropdownSettingsTarget(UiNode node) {
        String resource = node.resourceId == null ? "" : node.resourceId;
        String className = node.className == null ? "" : node.className;
        return resource.contains("dropdown_button") ||
                resource.contains("dropdown_container") ||
                resource.contains("right_view_group") ||
                className.contains("Spinner");
    }

    private static boolean isSafeSettingsContentClickTarget(UiNode node) {
        if (!isSettingsContentNode(node) || node.bounds == null || !node.enabled || !node.clickable) {
            return false;
        }
        if (node.checkable || node.checked) {
            return false;
        }
        String search = node.searchText().toLowerCase(Locale.US);
        return !search.contains("toggle") && !search.contains("reset all to default");
    }

    private static String normalizeChildClickMode(String clickMode) {
        String normalized = clickMode == null ? "" : clickMode.trim().toLowerCase(Locale.US);
        if ("uiobject".equals(normalized) || "uiobject2".equals(normalized)) {
            return "uiObject2";
        }
        if ("accessibility".equals(normalized) || "accessibilityclick".equals(normalized) || "actionclick".equals(normalized)) {
            return "accessibilityClick";
        }
        if ("accessibilityexpand".equals(normalized) || "actionexpand".equals(normalized) || "expand".equals(normalized)) {
            return "accessibilityExpand";
        }
        return "coordinate";
    }

    private static String normalizeChildTargetRole(String childTargetRole) {
        String normalized = childTargetRole == null ? "" : childTargetRole.trim().toLowerCase(Locale.US);
        if ("dropdown".equals(normalized) || "spinner".equals(normalized) || "selector".equals(normalized)) {
            return "dropdown";
        }
        return "row";
    }

    private static UiObject2 findUiObjectByBounds(UiDevice device, Rect targetBounds) {
        List<UiObject2> objects = device.findObjects(By.pkg("com.oculus.panelapp.settings"));
        UiObject2 best = null;
        int bestScore = Integer.MAX_VALUE;
        for (UiObject2 object : objects) {
            if (!isSafeSettingsContentObject(object)) {
                continue;
            }
            Rect bounds = object.getVisibleBounds();
            int score = boundsMatchScore(bounds, targetBounds, object.getResourceName());
            if (score < bestScore) {
                best = object;
                bestScore = score;
            }
        }
        return bestScore == Integer.MAX_VALUE ? null : best;
    }

    private static boolean isSafeSettingsContentObject(UiObject2 object) {
        if (object == null || !object.isEnabled() || !object.isClickable() || object.isChecked()) {
            return false;
        }
        Rect bounds = object.getVisibleBounds();
        if (!isSettingsContentBounds(bounds)) {
            return false;
        }
        String search = uiObjectSearchText(object).toLowerCase(Locale.US);
        return !search.contains("toggle") && !search.contains("reset all to default");
    }

    private static AccessibilityNodeInfo findAccessibilityActionTarget(
            UiAutomation uiAutomation,
            UiNode targetNode,
            int actionId
    ) {
        AccessibilityNodeInfo best = null;
        int bestScore = Integer.MAX_VALUE;
        AccessibilityNodeInfo activeRoot = uiAutomation.getRootInActiveWindow();
        AccessibilityMatch activeMatch = findBestAccessibilityActionTargetInNode(activeRoot, targetNode, actionId);
        if (activeMatch != null) {
            best = activeMatch.node;
            bestScore = activeMatch.score;
        }
        try {
            for (AccessibilityWindowInfo window : uiAutomation.getWindows()) {
                AccessibilityMatch windowMatch = findBestAccessibilityActionTargetInNode(window.getRoot(), targetNode, actionId);
                if (windowMatch != null && windowMatch.score < bestScore) {
                    best = windowMatch.node;
                    bestScore = windowMatch.score;
                }
            }
        } catch (Exception ignored) {
            return best;
        }
        return best;
    }

    private static AccessibilityMatch findBestAccessibilityActionTargetInNode(
            AccessibilityNodeInfo node,
            UiNode targetNode,
            int actionId
    ) {
        if (node == null) {
            return null;
        }
        AccessibilityMatch best = null;
        if (isSafeSettingsContentAccessibilityActionTarget(node, actionId)) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int score = boundsMatchScore(bounds, targetNode.bounds, node.getViewIdResourceName());
            if (score < Integer.MAX_VALUE) {
                best = new AccessibilityMatch(node, score);
            }
        }
        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index += 1) {
            AccessibilityMatch childMatch = findBestAccessibilityActionTargetInNode(
                    node.getChild(index),
                    targetNode,
                    actionId
            );
            if (childMatch != null && (best == null || childMatch.score < best.score)) {
                best = childMatch;
            }
        }
        return best;
    }

    private static boolean isSafeSettingsContentAccessibilityActionTarget(
            AccessibilityNodeInfo node,
            int actionId
    ) {
        if (node == null || !node.isEnabled() || node.isCheckable() || node.isChecked() || !hasAction(node, actionId)) {
            return false;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!isSettingsContentBounds(bounds)) {
            return false;
        }
        String search = accessibilitySearchText(node).toLowerCase(Locale.US);
        return !search.contains("toggle") && !search.contains("reset all to default");
    }

    private static boolean isSettingsContentBounds(Rect bounds) {
        return bounds != null && (bounds.left >= 320 || bounds.right >= 360);
    }

    private static int boundsMatchScore(Rect candidate, Rect target, String resourceName) {
        if (candidate == null || target == null) {
            return Integer.MAX_VALUE;
        }
        int targetCenterX = target.centerX();
        int targetCenterY = target.centerY();
        boolean sameRow = candidate.top <= targetCenterY && candidate.bottom >= targetCenterY;
        int verticalDistance = Math.abs(candidate.centerY() - targetCenterY);
        if (!sameRow && verticalDistance > 96) {
            return Integer.MAX_VALUE;
        }
        int horizontalDistance = Math.abs(candidate.centerX() - targetCenterX);
        int score = (verticalDistance * 4) + horizontalDistance;
        if (candidate.contains(targetCenterX, targetCenterY)) {
            score -= 120;
        }
        String resource = resourceName == null ? "" : resourceName;
        if (resource.contains("settings_list_item")) {
            score -= 200;
        }
        if (resource.contains("dropdown_container") || resource.contains("right_view_group")) {
            score -= 150;
        }
        if (resource.contains("subtitle") || resource.contains("title")) {
            score += 120;
        }
        return score;
    }

    private static void runScrollProbe(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        String surface = args.getString("surface", "metacamDeepSettings");
        List<String> strategies = parseStrategies(args.getString("strategies", args.getString("strategy", "all")));
        boolean resetSurfaceEachStrategy = parseBoolean(args.getString("resetSurfaceEachStrategy", "true"), true);

        UiSnapshot prepared = prepareSurface(device, report, surface);
        dumpAccessibilityState(instrumentation, report, "scroll_probe_initial_" + surface);
        report.event("scroll_probe_start", new JSONObject()
                .put("surface", surface)
                .put("strategies", new JSONArray(strategies))
                .put("initialNodeCount", prepared.nodes.size()));

        for (int index = 0; index < strategies.size(); index += 1) {
            String strategy = strategies.get(index);
            if (index > 0 && resetSurfaceEachStrategy && !"current".equals(surface)) {
                prepareSurface(device, report, surface);
            }

            UiSnapshot before = dumpAndClassify(
                    device,
                    report,
                    "scroll_probe_before_" + sanitizeName(strategy),
                    DEFAULT_CANDIDATE_PATTERN
            );
            JSONObject outcome = runScrollStrategy(instrumentation, device, report, args, strategy, before);
            report.event("scroll_probe_strategy", outcome);
            device.waitForIdle(3000);
            Thread.sleep(parseInt(args.getString("postScrollWaitMs", "1000"), 1000));
            UiSnapshot after = dumpAndClassify(
                    device,
                    report,
                    "scroll_probe_after_" + sanitizeName(strategy),
                    DEFAULT_CANDIDATE_PATTERN
            );
            report.event("scroll_probe_delta", new JSONObject()
                    .put("strategy", strategy)
                    .put("beforeNodeCount", before.nodes.size())
                    .put("afterNodeCount", after.nodes.size())
                    .put("beforeVisibleTextHash", visibleTextHash(before.nodes))
                    .put("afterVisibleTextHash", visibleTextHash(after.nodes))
                    .put("newVisibleTexts", newVisibleTexts(before.nodes, after.nodes)));
            dumpAccessibilityState(instrumentation, report, "scroll_probe_after_" + strategy);
        }
    }

    private static JSONObject runScrollStrategy(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args,
            String strategy,
            UiSnapshot snapshot
    ) throws JSONException {
        JSONObject outcome = new JSONObject()
                .put("strategy", strategy)
                .put("started", System.currentTimeMillis());
        try {
            if ("uiScrollable".equals(strategy)) {
                return runUiScrollableStrategy(device, args, outcome);
            }
            if ("uiScrollableFling".equals(strategy)) {
                return runUiScrollableFlingStrategy(device, args, outcome);
            }
            if ("uiObject2".equals(strategy)) {
                return runUiObject2Strategy(device, args, outcome);
            }
            if ("uiObject2UntilEnd".equals(strategy)) {
                return runUiObject2UntilEndStrategy(device, args, outcome);
            }
            if ("accessibilityAction".equals(strategy)) {
                return runAccessibilityActionStrategy(instrumentation, args, outcome);
            }
            if ("deviceSwipe".equals(strategy)) {
                return runDeviceSwipeStrategy(device, args, outcome);
            }
            if ("deviceDrag".equals(strategy)) {
                return runDeviceDragStrategy(device, args, outcome);
            }
            if ("toolTypeSwipe".equals(strategy)) {
                return runToolTypeSwipeStrategy(device, args, outcome);
            }
            if ("shellSwipe".equals(strategy)) {
                return runShellSwipeStrategy(device, args, snapshot, outcome);
            }
            if ("shellScroll".equals(strategy)) {
                return runShellScrollStrategy(device, args, snapshot, outcome);
            }
            if ("keyScroll".equals(strategy)) {
                return runKeyScrollStrategy(device, args, outcome);
            }
            return outcome.put("error", "Unknown scroll strategy: " + strategy);
        } catch (Exception exception) {
            return outcome
                    .put("error", exception.toString())
                    .put("errorClass", exception.getClass().getName());
        }
    }

    private static JSONObject runUiScrollableStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws Exception {
        String resourceRegex = args.getString("targetResRegex", DEFAULT_SCROLL_TARGET_RES_REGEX);
        int count = parseInt(args.getString("count", "1"), 1);
        boolean forward = isForwardScrollDirection(args.getString("direction", "down"));
        UiScrollable scrollable = new UiScrollable(new UiSelector().resourceIdMatches(resourceRegex));
        scrollable.setAsVerticalList();
        scrollable.setSwipeDeadZonePercentage((double) parseFloat(args.getString("deadZone", "0.1"), 0.1f));
        JSONArray attempts = new JSONArray();
        for (int index = 1; index <= count; index += 1) {
            boolean scrolled = forward ? scrollable.scrollForward() : scrollable.scrollBackward();
            attempts.put(new JSONObject().put("index", index).put("scrolled", scrolled));
            device.waitForIdle(2000);
            if (!scrolled) {
                break;
            }
        }
        return outcome
                .put("targetResRegex", resourceRegex)
                .put("forward", forward)
                .put("attempts", attempts);
    }

    private static JSONObject runUiScrollableFlingStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws Exception {
        String resourceRegex = args.getString("targetResRegex", DEFAULT_SCROLL_TARGET_RES_REGEX);
        int count = parseInt(args.getString("count", "1"), 1);
        boolean forward = isForwardScrollDirection(args.getString("direction", "down"));
        UiScrollable scrollable = new UiScrollable(new UiSelector().resourceIdMatches(resourceRegex));
        scrollable.setAsVerticalList();
        JSONArray attempts = new JSONArray();
        for (int index = 1; index <= count; index += 1) {
            boolean scrolled = forward ? scrollable.flingForward() : scrollable.flingBackward();
            attempts.put(new JSONObject().put("index", index).put("flung", scrolled));
            device.waitForIdle(2000);
            if (!scrolled) {
                break;
            }
        }
        return outcome
                .put("targetResRegex", resourceRegex)
                .put("forward", forward)
                .put("attempts", attempts);
    }

    private static JSONObject runUiObject2Strategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws Exception {
        UiObject2 target = findScrollTarget(device, args);
        if (target == null) {
            return outcome.put("found", false);
        }
        Direction direction = toDirection(args.getString("direction", "down"));
        float percent = parseFloat(args.getString("percent", "0.75"), 0.75f);
        int speed = parseInt(args.getString("speed", "1000"), 1000);
        int count = parseInt(args.getString("count", "1"), 1);
        target.setGestureMarginPercentage(parseFloat(args.getString("gestureMargin", "0.12"), 0.12f));
        JSONArray attempts = new JSONArray();
        for (int index = 1; index <= count; index += 1) {
            boolean scrolled = target.scroll(direction, percent, speed);
            attempts.put(new JSONObject().put("index", index).put("scrolled", scrolled));
            device.waitForIdle(2000);
            if (!scrolled) {
                break;
            }
        }
        return outcome
                .put("found", true)
                .put("target", uiObjectSummary(target))
                .put("direction", direction.toString())
                .put("attempts", attempts);
    }

    private static JSONObject runUiObject2UntilEndStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws Exception {
        UiObject2 target = findScrollTarget(device, args);
        if (target == null) {
            return outcome.put("found", false);
        }
        Direction direction = toDirection(args.getString("direction", "down"));
        Boolean finished = target.scrollUntil(direction, Until.scrollFinished(direction));
        return outcome
                .put("found", true)
                .put("target", uiObjectSummary(target))
                .put("direction", direction.toString())
                .put("finished", finished);
    }

    private static JSONObject runAccessibilityActionStrategy(
            Instrumentation instrumentation,
            Bundle args,
            JSONObject outcome
    ) throws JSONException {
        Pattern targetPattern = optionalPattern(args.getString("targetRegex", ""));
        int[] actionIds = scrollActionIds(args.getString("direction", "down"));
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        AccessibilityNodeInfo target = findAccessibilityScrollTarget(uiAutomation, targetPattern, actionIds);
        if (target == null) {
            return outcome
                    .put("found", false)
                    .put("targetRegex", targetPattern == null ? "" : targetPattern.pattern());
        }
        JSONArray attempts = new JSONArray();
        boolean performed = false;
        for (int actionId : actionIds) {
            boolean hasAction = hasAction(target, actionId);
            boolean result = hasAction && target.performAction(actionId);
            attempts.put(new JSONObject()
                    .put("actionId", actionId)
                    .put("actionName", actionName(actionId))
                    .put("hasAction", hasAction)
                    .put("result", result));
            if (result) {
                performed = true;
                break;
            }
        }
        return outcome
                .put("found", true)
                .put("performed", performed)
                .put("target", accessibilityNodeSummary(target, "target", 0))
                .put("attempts", attempts);
    }

    private static JSONObject runDeviceSwipeStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws JSONException {
        SwipeSpec swipe = swipeSpec(args);
        JSONArray attempts = new JSONArray();
        int count = parseInt(args.getString("count", "1"), 1);
        for (int index = 1; index <= count; index += 1) {
            boolean result = device.swipe(swipe.startX, swipe.startY, swipe.endX, swipe.endY, swipe.steps);
            attempts.put(new JSONObject().put("index", index).put("result", result));
        }
        return outcome.put("swipe", swipe.toJson()).put("attempts", attempts);
    }

    private static JSONObject runDeviceDragStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws JSONException {
        SwipeSpec swipe = swipeSpec(args);
        JSONArray attempts = new JSONArray();
        int count = parseInt(args.getString("count", "1"), 1);
        for (int index = 1; index <= count; index += 1) {
            boolean result = device.drag(swipe.startX, swipe.startY, swipe.endX, swipe.endY, swipe.steps);
            attempts.put(new JSONObject().put("index", index).put("result", result));
        }
        return outcome.put("drag", swipe.toJson()).put("attempts", attempts);
    }

    private static JSONObject runToolTypeSwipeStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws JSONException {
        Configurator configurator = Configurator.getInstance();
        int previous = configurator.getToolType();
        int requested = toolType(args.getString("toolType", "mouse"));
        try {
            configurator.setToolType(requested);
            return runDeviceSwipeStrategy(device, args, outcome
                    .put("previousToolType", previous)
                    .put("requestedToolType", requested));
        } finally {
            configurator.setToolType(previous);
        }
    }

    private static JSONObject runShellSwipeStrategy(
            UiDevice device,
            Bundle args,
            UiSnapshot snapshot,
            JSONObject outcome
    ) throws IOException, JSONException {
        SwipeSpec swipe = swipeSpec(args);
        String source = args.getString("inputSource", "touchscreen");
        int durationMs = parseInt(args.getString("durationMs", "500"), 500);
        JSONArray attempts = new JSONArray();
        for (int displayId : resolveDisplayIds(args, snapshot)) {
            String command = inputPrefix(source, displayId) + " swipe "
                    + swipe.startX + " " + swipe.startY + " "
                    + swipe.endX + " " + swipe.endY + " " + durationMs;
            attempts.put(new JSONObject()
                    .put("displayId", displayId)
                    .put("command", command)
                    .put("output", shell(device, command)));
        }
        return outcome.put("swipe", swipe.toJson()).put("attempts", attempts);
    }

    private static JSONObject runShellScrollStrategy(
            UiDevice device,
            Bundle args,
            UiSnapshot snapshot,
            JSONObject outcome
    ) throws IOException, JSONException {
        String source = args.getString("inputSource", "rotaryencoder");
        String axis = args.getString("axis", "VSCROLL");
        String axisValues = args.getString("axisValues", args.getString("axisValue", "-3.0;3.0"));
        JSONArray attempts = new JSONArray();
        for (int displayId : resolveDisplayIds(args, snapshot)) {
            for (String rawValue : axisValues.split(";")) {
                String value = rawValue.trim();
                if (value.isEmpty()) {
                    continue;
                }
                String command = inputPrefix(source, displayId) + " scroll --axis " + axis + "," + value;
                attempts.put(new JSONObject()
                        .put("displayId", displayId)
                        .put("axis", axis)
                        .put("value", value)
                        .put("command", command)
                        .put("output", shell(device, command)));
            }
        }
        return outcome.put("source", source).put("attempts", attempts);
    }

    private static JSONObject runKeyScrollStrategy(
            UiDevice device,
            Bundle args,
            JSONObject outcome
    ) throws IOException, JSONException {
        String direction = args.getString("direction", "down");
        String key = args.getString("keyCode", isForwardScrollDirection(direction) ? "KEYCODE_DPAD_DOWN" : "KEYCODE_DPAD_UP");
        int count = parseInt(args.getString("count", "3"), 3);
        JSONArray attempts = new JSONArray();
        for (int index = 1; index <= count; index += 1) {
            String command = "input keyevent " + key;
            attempts.put(new JSONObject()
                    .put("index", index)
                    .put("command", command)
                    .put("output", shell(device, command)));
        }
        return outcome.put("key", key).put("attempts", attempts);
    }

    private static UiObject2 findScrollTarget(UiDevice device, Bundle args) {
        Pattern targetPattern = optionalPattern(args.getString("targetRegex", ""));
        String targetResRegex = args.getString("targetResRegex", "");
        int selectorDisplayId = parseInt(args.getString("selectorDisplayId", "-1"), -1);
        List<UiObject2> candidates;
        if (selectorDisplayId >= 0) {
            candidates = device.findObjects(By.scrollable(true).displayId(selectorDisplayId));
        } else {
            candidates = device.findObjects(By.scrollable(true));
        }
        for (UiObject2 candidate : candidates) {
            String search = uiObjectSearchText(candidate);
            if (!targetResRegex.isEmpty() && !Pattern.compile(targetResRegex).matcher(nullToEmpty(candidate.getResourceName())).find()) {
                continue;
            }
            if (targetPattern != null && !targetPattern.matcher(search).find()) {
                continue;
            }
            return candidate;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static JSONObject clickSettingsNavTarget(
            UiDevice device,
            String target,
            int maxNavScrolls
    ) throws JSONException {
        JSONObject outcome = new JSONObject()
                .put("target", target)
                .put("resourceRegex", settingsNavResourceRegex(target))
                .put("textRegex", settingsNavTextRegex(target));
        JSONArray scrollAttempts = new JSONArray();
        UiObject2 navTarget = findSettingsNavTarget(device, target);
        if (navTarget == null) {
            UiObject2 sideNav = findObjectByResourceRegex(device, ".*ocsidenav_recycler_view.*");
            if (sideNav == null) {
                return outcome
                        .put("found", false)
                        .put("clicked", false)
                        .put("error", "Side-nav recycler was not found");
            }
            sideNav.setGestureMarginPercentage(0.10f);
            for (int index = 1; index <= maxNavScrolls; index += 1) {
                JSONObject attempt = scrollSettingsSideNav(device, sideNav, Direction.UP, index);
                scrollAttempts.put(attempt);
                boolean moved = attempt.optBoolean("objectScrolled", false) || attempt.optBoolean("coordinateSwiped", false);
                device.waitForIdle(1000);
                navTarget = findSettingsNavTarget(device, target);
                if (navTarget != null || !moved) {
                    break;
                }
            }
        }
        if (navTarget == null) {
            UiObject2 sideNav = findObjectByResourceRegex(device, ".*ocsidenav_recycler_view.*");
            if (sideNav == null) {
                return outcome
                        .put("found", false)
                        .put("clicked", false)
                        .put("error", "Side-nav recycler was not found after upward search");
            }
            sideNav.setGestureMarginPercentage(0.10f);
            for (int index = 1; index <= maxNavScrolls; index += 1) {
                JSONObject attempt = scrollSettingsSideNav(device, sideNav, Direction.DOWN, index);
                scrollAttempts.put(attempt);
                boolean moved = attempt.optBoolean("objectScrolled", false) || attempt.optBoolean("coordinateSwiped", false);
                device.waitForIdle(1500);
                navTarget = findSettingsNavTarget(device, target);
                if (navTarget != null || !moved) {
                    break;
                }
            }
        }
        outcome.put("scrollAttempts", scrollAttempts);
        if (navTarget == null) {
            return outcome.put("found", false).put("clicked", false);
        }
        outcome.put("found", true).put("targetNode", uiObjectSummary(navTarget));
        try {
            navTarget.click();
            return outcome.put("clicked", true);
        } catch (Exception exception) {
            return outcome
                    .put("clicked", false)
                    .put("error", exception.toString())
                    .put("errorClass", exception.getClass().getName());
        }
    }

    private static JSONObject emptySettingsSurfaceEvent(String target) throws JSONException {
        return new JSONObject()
                .put("target", target)
                .put("found", false)
                .put("clicked", false)
                .put("error", "prepared settings surface had zero nodes")
                .put("diagnostic", "android.settings.SETTINGS returned but UIAutomator saw no accessibility nodes; run currentWindow/surfaceMap or restore a visible headset panel before retrying");
    }

    private static JSONObject settingsRecoveryAttempt(String phase, int attempt, UiSnapshot snapshot) throws JSONException {
        return new JSONObject()
                .put("phase", phase)
                .put("attempt", attempt)
                .put("nodeCount", snapshot.nodes.size())
                .put("settingsNodeCount", countPackageNodes(snapshot.nodes, "com.oculus.panelapp.settings"))
                .put("scrollableCount", countScrollableNodes(snapshot.nodes))
                .put("visibleTextHash", visibleTextHash(snapshot.nodes))
                .put("empty", snapshot.nodes.isEmpty());
    }

    private static JSONObject systemSurfaceAttempt(
            String surface,
            int index,
            UiSnapshot snapshot,
            String previousHash
    ) throws JSONException {
        String visibleTextHash = visibleTextHash(snapshot.nodes);
        return new JSONObject()
                .put("surface", surface)
                .put("index", index)
                .put("nodeCount", snapshot.nodes.size())
                .put("clickableCount", countClickableNodes(snapshot.nodes))
                .put("enabledCount", countEnabledNodes(snapshot.nodes))
                .put("scrollableCount", countScrollableNodes(snapshot.nodes))
                .put("checkedCount", countCheckedNodes(snapshot.nodes))
                .put("packageCount", countDistinctPackages(snapshot.nodes))
                .put("displayIds", displayIds(snapshot.nodes))
                .put("visibleTextHash", visibleTextHash)
                .put("changedFromPrevious", !previousHash.isEmpty() && !previousHash.equals(visibleTextHash))
                .put("empty", snapshot.nodes.isEmpty());
    }

    private static int countPackageNodes(List<UiNode> nodes, String packageName) {
        int count = 0;
        for (UiNode node : nodes) {
            if (packageName.equals(node.packageName)) {
                count += 1;
            }
        }
        return count;
    }

    private static int countClickableNodes(List<UiNode> nodes) {
        int count = 0;
        for (UiNode node : nodes) {
            if (node.clickable) {
                count += 1;
            }
        }
        return count;
    }

    private static int countEnabledNodes(List<UiNode> nodes) {
        int count = 0;
        for (UiNode node : nodes) {
            if (node.enabled) {
                count += 1;
            }
        }
        return count;
    }

    private static int countScrollableNodes(List<UiNode> nodes) {
        int count = 0;
        for (UiNode node : nodes) {
            if (node.scrollable) {
                count += 1;
            }
        }
        return count;
    }

    private static int countCheckedNodes(List<UiNode> nodes) {
        int count = 0;
        for (UiNode node : nodes) {
            if (node.checked) {
                count += 1;
            }
        }
        return count;
    }

    private static int countDistinctPackages(List<UiNode> nodes) {
        Set<String> packages = new LinkedHashSet<>();
        for (UiNode node : nodes) {
            if (!node.packageName.isEmpty()) {
                packages.add(node.packageName);
            }
        }
        return packages.size();
    }

    private static JSONArray displayIds(List<UiNode> nodes) {
        Set<String> displayIds = new LinkedHashSet<>();
        for (UiNode node : nodes) {
            if (!node.displayId.isEmpty()) {
                displayIds.add(node.displayId);
            }
        }
        return new JSONArray(displayIds);
    }

    private static JSONObject scrollSettingsSideNav(
            UiDevice device,
            UiObject2 sideNav,
            Direction direction,
            int index
    ) throws JSONException {
        boolean objectScrolled = false;
        try {
            objectScrolled = sideNav.scroll(direction, 0.75f, 1000);
        } catch (Exception ignored) {
            objectScrolled = false;
        }
        boolean coordinateSwiped = false;
        if (!objectScrolled) {
            if (direction == Direction.UP) {
                coordinateSwiped = device.swipe(170, 240, 170, 720, 50);
            } else {
                coordinateSwiped = device.swipe(170, 720, 170, 240, 50);
            }
        }
        return new JSONObject()
                        .put("index", index)
                        .put("direction", direction.toString())
                        .put("objectScrolled", objectScrolled)
                        .put("coordinateSwiped", coordinateSwiped);
    }

    private static UiObject2 findSettingsNavTarget(UiDevice device, String target) {
        UiObject2 byResource = findObjectByResourceRegex(device, settingsNavResourceRegex(target));
        if (byResource != null) {
            return byResource;
        }
        if (!isExplicitTextTarget(target)) {
            return null;
        }
        Pattern textPattern = Pattern.compile(settingsNavTextRegex(target), Pattern.CASE_INSENSITIVE);
        List<UiObject2> byText = device.findObjects(By.text(textPattern));
        if (!byText.isEmpty()) {
            return byText.get(0);
        }
        return null;
    }

    private static boolean isExplicitTextTarget(String target) {
        return target.indexOf(' ') >= 0 || target.indexOf('&') >= 0 || target.startsWith("text:");
    }

    private static UiObject2 findObjectByResourceRegex(UiDevice device, String resourceRegex) {
        List<UiObject2> objects = device.findObjects(By.res(Pattern.compile(resourceRegex, Pattern.CASE_INSENSITIVE)));
        return objects.isEmpty() ? null : objects.get(0);
    }

    private static String settingsNavResourceRegex(String target) {
        if (target.startsWith("com.") || target.contains(":id/")) {
            return ".*" + Pattern.quote(target) + ".*";
        }
        if (target.startsWith("settings_nav_")) {
            return ".*" + Pattern.quote(target) + ".*";
        }
        return ".*settings_nav_" + Pattern.quote(target) + ".*";
    }

    private static String settingsNavTextRegex(String target) {
        if (target.startsWith("text:")) {
            return ".*" + Pattern.quote(target.substring("text:".length())) + ".*";
        }
        if (target.indexOf(' ') >= 0 || target.indexOf('&') >= 0) {
            return ".*" + Pattern.quote(target) + ".*";
        }
        if ("quest_link".equals(target)) {
            return ".*\\bLink\\b.*";
        }
        if ("general".equals(target)) {
            return ".*\\bGeneral\\b.*";
        }
        if ("action_button".equals(target)) {
            return ".*Action button.*";
        }
        if ("notifications".equals(target)) {
            return ".*Notifications.*";
        }
        if ("space_setup".equals(target)) {
            return ".*Environment setup.*";
        }
        if ("world_movement".equals(target)) {
            return ".*\\bMovement\\b.*";
        }
        if ("movement_tracking".equals(target)) {
            return ".*\\bTracking\\b.*";
        }
        if ("accessibility".equals(target)) {
            return ".*Accessibility.*";
        }
        if ("display_brightness".equals(target)) {
            return ".*Display.*brightness.*";
        }
        if ("audio".equals(target)) {
            return ".*\\bAudio\\b.*";
        }
        if ("camera".equals(target)) {
            return ".*\\bCamera\\b.*";
        }
        if ("privacy_safety".equals(target)) {
            return ".*Privacy.*safety.*";
        }
        if ("passcode_security".equals(target)) {
            return ".*Passcode.*security.*";
        }
        if ("experimental".equals(target)) {
            return ".*Experimental.*";
        }
        if ("developer".equals(target)) {
            return ".*Developer.*";
        }
        if ("help".equals(target)) {
            return ".*\\bHelp\\b.*";
        }
        return ".*" + Pattern.quote(target.replace('_', ' ')) + ".*";
    }

    private static void runMediaProjectionPromptProbe(
            Instrumentation instrumentation,
            UiDevice device,
            Report report,
            Bundle args
    ) throws Exception {
        Context targetContext = instrumentation.getTargetContext();
        String packageName = targetContext.getPackageName();
        String temporaryAppOpMode = args.getString("temporaryAppOpMode", "");
        boolean restoreAppOp = parseBoolean(args.getString("restoreAppOp", "true"), true);
        String selectionChoice = args.getString("selectionChoice", "none");
        String tapChoice = args.getString("tapChoice", "cancel");
        int waitForPromptMs = parseInt(args.getString("waitForPromptMs", "6000"), 6000);
        int waitAfterTapMs = parseInt(args.getString("waitAfterTapMs", "2500"), 2500);
        File resultFile = projectionPromptResultFile(targetContext);
        if (resultFile.isFile() && !resultFile.delete()) {
            report.event("media_projection_prompt_result_delete", new JSONObject().put("deleted", false));
        }
        File traceFile = projectionPromptTraceFile(targetContext);
        if (traceFile.isFile() && !traceFile.delete()) {
            report.event("media_projection_prompt_trace_delete", new JSONObject().put("deleted", false));
        }

        report.command("media_projection_before", shell(device, "dumpsys media_projection | sed -n '1,120p'"));
        String appOpBefore = shell(device, "cmd appops get " + packageName + " PROJECT_MEDIA");
        report.command("project_media_appop_before", appOpBefore);
        String previousAppOpMode = appOpModeFromOutput(appOpBefore);
        if (!temporaryAppOpMode.trim().isEmpty()) {
            report.command(
                    "project_media_appop_set",
                    shell(device, "cmd appops set " + packageName + " PROJECT_MEDIA " + temporaryAppOpMode.trim())
            );
        }

        try {
            report.event("media_projection_prompt_start", new JSONObject()
                    .put("temporaryAppOpMode", temporaryAppOpMode.trim())
                    .put("previousAppOpMode", previousAppOpMode)
                    .put("selectionChoice", selectionChoice)
                    .put("tapChoice", tapChoice)
                    .put("restoreAppOp", restoreAppOp));
            report.command("media_projection_prompt_start_activity", shell(
                    device,
                    "am start -W -a " + ProjectionPromptActivity.ACTION_REQUEST_MEDIA_PROJECTION +
                            " -n " + packageName + "/.ProjectionPromptActivity" +
                            " --ez " + ProjectionPromptActivity.EXTRA_AUTO_REQUEST + " true" +
                            " --ez " + ProjectionPromptActivity.EXTRA_FINISH_AFTER_RESULT + " true"
            ));
            device.wait(Until.hasObject(By.text(MEDIA_PROJECTION_PROMPT_PATTERN)), waitForPromptMs);
            device.waitForIdle(3000);
            Thread.sleep(1000);

            UiSnapshot prompt = dumpAndClassify(
                    device,
                    report,
                    "media_projection_prompt",
                    MEDIA_PROJECTION_PROMPT_PATTERN
            );
            dumpAccessibilityState(instrumentation, report, "media_projection_prompt");
            JSONArray buttons = projectionPromptButtons(prompt.nodes);
            report.event("media_projection_prompt_buttons", new JSONObject()
                    .put("buttonCount", buttons.length())
                    .put("buttons", buttons));
            JSONArray selections = projectionPromptSelectionOptions(prompt.nodes);
            report.event("media_projection_prompt_selection_options", new JSONObject()
                    .put("optionCount", selections.length())
                    .put("options", selections));

            UiNode selectionTarget = projectionPromptSelectionTarget(prompt.nodes, selectionChoice);
            if (selectionTarget == null) {
                report.event("media_projection_prompt_selection_tap", new JSONObject()
                        .put("selectionChoice", selectionChoice)
                        .put("tapped", false)
                        .put("matchedRole", ""));
            } else {
                int x = selectionTarget.bounds.centerX();
                int y = selectionTarget.bounds.centerY();
                report.event("media_projection_prompt_selection_tap", new JSONObject()
                        .put("selectionChoice", selectionChoice)
                        .put("tapped", true)
                        .put("matchedRole", projectionPromptSelectionRole(selectionTarget))
                        .put("x", x)
                        .put("y", y));
                device.click(x, y);
                device.waitForIdle(3000);
                Thread.sleep(1000);
                prompt = dumpAndClassify(
                        device,
                        report,
                        "media_projection_prompt_after_selection",
                        MEDIA_PROJECTION_PROMPT_PATTERN
                );
                dumpAccessibilityState(instrumentation, report, "media_projection_prompt_after_selection");
                JSONArray afterSelectionButtons = projectionPromptButtons(prompt.nodes);
                report.event("media_projection_prompt_buttons", new JSONObject()
                        .put("buttonCount", afterSelectionButtons.length())
                        .put("buttons", afterSelectionButtons));
            }

            UiNode tapTarget = projectionPromptTapTarget(prompt.nodes, tapChoice);
            if (tapTarget == null || "none".equalsIgnoreCase(tapChoice.trim())) {
                report.event("media_projection_prompt_tap", new JSONObject()
                        .put("tapChoice", tapChoice)
                        .put("tapped", false)
                        .put("matchedRole", ""));
            } else {
                int x = tapTarget.bounds.centerX();
                int y = tapTarget.bounds.centerY();
                report.event("media_projection_prompt_tap", new JSONObject()
                        .put("tapChoice", tapChoice)
                        .put("tapped", true)
                        .put("matchedRole", projectionPromptRole(tapTarget))
                        .put("x", x)
                        .put("y", y));
                device.click(x, y);
                device.waitForIdle(3000);
                Thread.sleep(Math.max(waitAfterTapMs, 0));
                dumpAndClassify(device, report, "media_projection_after_prompt_tap", MEDIA_PROJECTION_PROMPT_PATTERN);
                dumpAccessibilityState(instrumentation, report, "media_projection_after_prompt_tap");
            }

            report.event("media_projection_prompt_result", readProjectionPromptResult(targetContext));
            report.event("media_projection_prompt_trace", readProjectionPromptTrace(targetContext));
            report.command("media_projection_after", shell(device, "dumpsys media_projection | sed -n '1,120p'"));
        } finally {
            if (!temporaryAppOpMode.trim().isEmpty() && restoreAppOp) {
                String restoreMode = previousAppOpMode.isEmpty() ? "default" : previousAppOpMode;
                report.command(
                        "project_media_appop_restore",
                        shell(device, "cmd appops set " + packageName + " PROJECT_MEDIA " + restoreMode)
                );
                report.event("media_projection_prompt_appop_restore", new JSONObject()
                        .put("restoredMode", restoreMode));
            }
        }
    }

    private static File projectionPromptResultFile(Context context) {
        return new File(context.getFilesDir(), ProjectionPromptActivity.RESULT_FILE_NAME);
    }

    private static File projectionPromptTraceFile(Context context) {
        return new File(context.getFilesDir(), ProjectionPromptActivity.TRACE_FILE_NAME);
    }

    private static JSONObject readProjectionPromptResult(Context context) throws JSONException, IOException {
        File resultFile = projectionPromptResultFile(context);
        JSONObject summary = new JSONObject().put("hasResultFile", resultFile.isFile());
        if (!resultFile.isFile()) {
            return summary;
        }
        JSONObject raw = new JSONObject(new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8));
        summary.put("resultCode", raw.optInt("resultCode", 0))
                .put("resultOk", raw.optBoolean("resultOk", false))
                .put("resultCanceled", raw.optBoolean("resultCanceled", false))
                .put("hasData", raw.optBoolean("hasData", false))
                .put("dataExtraCount", raw.optInt("dataExtraCount", 0))
                .put("hasError", raw.has("error"));
        return summary;
    }

    private static JSONObject readProjectionPromptTrace(Context context) throws JSONException, IOException {
        File traceFile = projectionPromptTraceFile(context);
        JSONObject summary = new JSONObject().put("hasTraceFile", traceFile.isFile());
        JSONArray events = new JSONArray();
        if (traceFile.isFile()) {
            List<String> lines = Files.readAllLines(traceFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                JSONObject row = new JSONObject(line);
                events.put(row.optString("event", ""));
            }
        }
        return summary.put("eventCount", events.length()).put("events", events);
    }

    private static JSONArray projectionPromptButtons(List<UiNode> nodes) throws JSONException {
        JSONArray buttons = new JSONArray();
        for (UiNode node : nodes) {
            String role = projectionPromptRole(node);
            if ("other".equals(role) || !node.clickable || node.bounds == null) {
                continue;
            }
            buttons.put(new JSONObject()
                    .put("role", role)
                    .put("enabled", node.enabled)
                    .put("clickable", node.clickable)
                    .put("className", node.className)
                    .put("bounds", rectToJson(node.bounds)));
        }
        return buttons;
    }

    private static JSONArray projectionPromptSelectionOptions(List<UiNode> nodes) throws JSONException {
        JSONArray options = new JSONArray();
        for (UiNode node : nodes) {
            String role = projectionPromptSelectionRole(node);
            if ("other".equals(role) || !node.enabled || !node.clickable || node.bounds == null) {
                continue;
            }
            options.put(new JSONObject()
                    .put("role", role)
                    .put("enabled", node.enabled)
                    .put("clickable", node.clickable)
                    .put("className", node.className)
                    .put("bounds", rectToJson(node.bounds)));
        }
        return options;
    }

    private static UiNode projectionPromptTapTarget(List<UiNode> nodes, String tapChoice) {
        String normalizedChoice = tapChoice == null ? "" : tapChoice.trim().toLowerCase(Locale.US);
        if (normalizedChoice.isEmpty() || "none".equals(normalizedChoice)) {
            return null;
        }
        for (UiNode node : nodes) {
            if (!node.enabled || !node.clickable || node.bounds == null) {
                continue;
            }
            String role = projectionPromptRole(node);
            if ("first".equals(normalizedChoice) && !"other".equals(role)) {
                return node;
            }
            if (("cancel".equals(normalizedChoice) || "deny".equals(normalizedChoice)) && "cancel".equals(role)) {
                return node;
            }
            if (("share".equals(normalizedChoice)
                    || "approve".equals(normalizedChoice)
                    || "allow".equals(normalizedChoice)
                    || "start".equals(normalizedChoice)) && "approve".equals(role)) {
                return node;
            }
        }
        return null;
    }

    private static UiNode projectionPromptSelectionTarget(List<UiNode> nodes, String selectionChoice) {
        String normalizedChoice = selectionChoice == null ? "" : selectionChoice.trim().toLowerCase(Locale.US);
        if (normalizedChoice.isEmpty() || "none".equals(normalizedChoice)) {
            return null;
        }
        for (UiNode node : nodes) {
            if (!node.enabled || !node.clickable || node.bounds == null) {
                continue;
            }
            String role = projectionPromptSelectionRole(node);
            if ("first".equals(normalizedChoice) && !"other".equals(role)) {
                return node;
            }
            if (("entire".equals(normalizedChoice) || "entire_view".equals(normalizedChoice))
                    && "entire_view".equals(role)) {
                return node;
            }
            if (("window".equals(normalizedChoice)
                    || "app".equals(normalizedChoice)
                    || "app_window".equals(normalizedChoice)) && "app_window".equals(role)) {
                return node;
            }
        }
        return null;
    }

    private static String projectionPromptRole(UiNode node) {
        String search = node.searchText().toLowerCase(Locale.US);
        if (search.contains("cancel")) {
            return "cancel";
        }
        if (search.contains("share")
                || search.contains("start now")
                || search.contains("start")
                || search.contains("allow")) {
            return "approve";
        }
        return "other";
    }

    private static String projectionPromptSelectionRole(UiNode node) {
        String search = node.searchText().toLowerCase(Locale.US);
        if (search.contains("entire view")) {
            return "entire_view";
        }
        if (node.clickable
                && node.className.toLowerCase(Locale.US).contains("linearlayout")
                && !node.contentDescription.isEmpty()) {
            return "app_window";
        }
        return "other";
    }

    private static String appOpModeFromOutput(String output) {
        if (output == null) {
            return "default";
        }
        String marker = "PROJECT_MEDIA:";
        int markerIndex = output.indexOf(marker);
        if (markerIndex < 0) {
            return "default";
        }
        String tail = output.substring(markerIndex + marker.length()).trim();
        if (tail.isEmpty()) {
            return "default";
        }
        int separator = tail.indexOf(';');
        String mode = separator >= 0 ? tail.substring(0, separator).trim() : tail.split("\\s+")[0].trim();
        return mode.isEmpty() ? "default" : mode;
    }

    private static void runMetacamRecordProbe(UiDevice device, Report report, Bundle args) throws Exception {
        int recordMs = parseInt(args.getString("recordMs", "6000"), 6000);
        report.command("videos_before", shell(
                device,
                "ls -1t /sdcard/Oculus/VideoShots 2>/dev/null | sed -n '1,10p'"
        ));
        report.command("media_projection_before", shell(device, "dumpsys media_projection | sed -n '1,120p'"));

        openMetacamPanel(device, report);
        UiSnapshot beforeStart = dumpAndClassify(device, report, "record_probe_before_start", DEFAULT_CANDIDATE_PATTERN);
        boolean startTapped = tapScreenRecordingButton(device, report, beforeStart, "record_start_tap");
        report.event("record_probe_start_result", new JSONObject().put("tapped", startTapped));

        Thread.sleep(Math.max(recordMs, 0));
        device.waitForIdle(3000);
        report.command("media_projection_during", shell(device, "dumpsys media_projection | sed -n '1,120p'"));
        report.command("videos_during", shell(
                device,
                "ls -1t /sdcard/Oculus/VideoShots 2>/dev/null | sed -n '1,10p'"
        ));

        openMetacamPanel(device, report);
        UiSnapshot beforeStop = dumpAndClassify(device, report, "record_probe_before_stop", DEFAULT_CANDIDATE_PATTERN);
        boolean stopTapped = tapScreenRecordingButton(device, report, beforeStop, "record_stop_tap");
        report.event("record_probe_stop_result", new JSONObject().put("tapped", stopTapped));

        Thread.sleep(3000);
        device.waitForIdle(3000);
        dumpAndClassify(device, report, "record_probe_after_stop", DEFAULT_CANDIDATE_PATTERN);
        report.command("media_projection_after", shell(device, "dumpsys media_projection | sed -n '1,120p'"));
        report.command("videos_after", shell(
                device,
                "ls -1t /sdcard/Oculus/VideoShots 2>/dev/null | sed -n '1,10p'"
        ));
    }

    private static void openMetacamPanel(UiDevice device, Report report) throws JSONException, IOException, InterruptedException {
        report.command("start_metacam_panel", shell(device, "am start -W -n " + METACAM_SHARING_ACTIVITY));
        device.waitForIdle(3000);
        Thread.sleep(1500);
    }

    private static UiSnapshot prepareSurface(UiDevice device, Report report, String surface) throws Exception {
        if ("current".equals(surface)) {
            return dumpAndClassify(device, report, "prepare_current", DEFAULT_CANDIDATE_PATTERN);
        }
        if ("quickSettings".equals(surface)) {
            device.openQuickSettings();
            device.waitForIdle(3000);
            Thread.sleep(1500);
            return dumpAndClassify(device, report, "prepare_quick_settings", DEFAULT_CANDIDATE_PATTERN);
        }
        if ("notifications".equals(surface)) {
            device.openNotification();
            device.waitForIdle(3000);
            Thread.sleep(1500);
            return dumpAndClassify(device, report, "prepare_notifications", DEFAULT_CANDIDATE_PATTERN);
        }
        if ("androidSettings".equals(surface)) {
            report.command("start_android_settings", shell(device, "am start -W -a android.settings.SETTINGS"));
            device.waitForIdle(3000);
            Thread.sleep(1500);
            return dumpAndClassify(device, report, "prepare_android_settings", DEFAULT_CANDIDATE_PATTERN);
        }
        if ("metacamPanel".equals(surface)
                || "metacamSettings".equals(surface)
                || "metacamDeepSettings".equals(surface)
                || "metacamAdvancedSettings".equals(surface)) {
            openMetacamPanel(device, report);
            UiSnapshot panel = dumpAndClassify(device, report, "prepare_metacam_panel", DEFAULT_CANDIDATE_PATTERN);
            if ("metacamPanel".equals(surface)) {
                return panel;
            }
            int firstTaps = tapMatchingNodes(
                    device,
                    report,
                    panel.nodes,
                    Pattern.compile("camera_settings_dropdown_button"),
                    1
            );
            report.event("prepare_metacam_settings_tap", new JSONObject().put("taps", firstTaps));
            device.waitForIdle(3000);
            Thread.sleep(1500);
            UiSnapshot settings = dumpAndClassify(device, report, "prepare_metacam_settings", DEFAULT_CANDIDATE_PATTERN);
            if ("metacamSettings".equals(surface)) {
                return settings;
            }
            int secondTaps = tapMatchingNodes(
                    device,
                    report,
                    settings.nodes,
                    Pattern.compile("camera_settings_menu_more_settings"),
                    1
            );
            report.event("prepare_metacam_deep_settings_tap", new JSONObject().put("taps", secondTaps));
            device.waitForIdle(3000);
            Thread.sleep(1500);
            UiSnapshot deepSettings = dumpAndClassify(device, report, "prepare_metacam_deep_settings", DEFAULT_CANDIDATE_PATTERN);
            if ("metacamAdvancedSettings".equals(surface)) {
                Bundle targetArgs = new Bundle();
                targetArgs.putString("targetResRegex", DEFAULT_SCROLL_TARGET_RES_REGEX);
                UiObject2 target = findScrollTarget(device, targetArgs);
                if (target != null) {
                    boolean scrolled = target.scroll(Direction.DOWN, 0.75f, 1000);
                    report.event("prepare_metacam_advanced_scroll", new JSONObject()
                            .put("scrolled", scrolled)
                            .put("target", uiObjectSummary(target)));
                    device.waitForIdle(3000);
                    Thread.sleep(1000);
                } else {
                    report.event("prepare_metacam_advanced_scroll", new JSONObject().put("scrolled", false));
                }
                return dumpAndClassify(device, report, "prepare_metacam_advanced_settings", DEFAULT_CANDIDATE_PATTERN);
            }
            return deepSettings;
        }
        throw new IllegalArgumentException("Unknown surface: " + surface);
    }

    private static boolean tapScreenRecordingButton(
            UiDevice device,
            Report report,
            UiSnapshot snapshot,
            String eventName
    ) throws JSONException, IOException {
        for (UiNode node : snapshot.nodes) {
            if (!node.enabled || !node.clickable || node.bounds == null) {
                continue;
            }
            if (!"com.oculus.metacam:id/screenrecording_button".equals(node.resourceId)) {
                continue;
            }
            int x = node.bounds.centerX();
            int y = node.bounds.centerY();
            report.event(eventName, node.toJson().put("x", x).put("y", y));
            device.click(x, y);
            return true;
        }
        return false;
    }

    private static void runMetacamPanelSweep(UiDevice device, Report report, Bundle args) throws Exception {
        report.command("metacam_resolve_activity", shell(
                device,
                "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.oculus.metacam"
        ));
        report.command("videos_before", shell(
                device,
                "ls -1t /sdcard/Oculus/VideoShots 2>/dev/null | sed -n '1,10p'"
        ));
        report.command("screenshots_before", shell(
                device,
                "ls -1t /sdcard/Oculus/Screenshots 2>/dev/null | sed -n '1,10p'"
        ));
        openMetacamPanel(device, report);
        UiSnapshot snapshot = dumpAndClassify(device, report, "metacam_panel", DEFAULT_CANDIDATE_PATTERN);

        Pattern tapPattern = optionalPattern(args.getString("tapRegex", ""));
        int tapLimit = parseInt(args.getString("tapLimit", "0"), 0);
        if (tapPattern != null && tapLimit > 0) {
            int taps = tapMatchingNodes(device, report, snapshot.nodes, tapPattern, tapLimit);
            report.event("active_tap_summary", new JSONObject()
                    .put("tapRegex", tapPattern.pattern())
                    .put("tapLimit", tapLimit)
                    .put("taps", taps));
            device.waitForIdle(3000);
            Thread.sleep(1500);
            UiSnapshot afterTap = dumpAndClassify(device, report, "after_active_tap", DEFAULT_CANDIDATE_PATTERN);
            Pattern secondTapPattern = optionalPattern(args.getString("tapRegex2", ""));
            int secondTapLimit = parseInt(args.getString("tapLimit2", "0"), 0);
            if (secondTapPattern != null && secondTapLimit > 0) {
                int secondTaps = tapMatchingNodes(device, report, afterTap.nodes, secondTapPattern, secondTapLimit);
                report.event("active_tap_2_summary", new JSONObject()
                        .put("tapRegex", secondTapPattern.pattern())
                        .put("tapLimit", secondTapLimit)
                        .put("taps", secondTaps));
                device.waitForIdle(3000);
                Thread.sleep(1500);
                dumpAndClassify(device, report, "after_active_tap_2", DEFAULT_CANDIDATE_PATTERN);
            }
            int swipeUpCount = parseInt(args.getString("swipeUpCount", "0"), 0);
            for (int index = 1; index <= swipeUpCount; index += 1) {
                boolean swiped = device.swipe(900, 720, 900, 240, 50);
                report.event("swipe_up", new JSONObject().put("index", index).put("swiped", swiped));
                device.waitForIdle(3000);
                Thread.sleep(1000);
                dumpAndClassify(device, report, "after_swipe_up_" + index, DEFAULT_CANDIDATE_PATTERN);
            }
            runOptionalSettingsScrolls(device, report, args);
            report.command("media_projection_after_tap", shell(device, "dumpsys media_projection | sed -n '1,120p'"));
            report.command("videos_after_tap", shell(
                    device,
                    "ls -1t /sdcard/Oculus/VideoShots 2>/dev/null | sed -n '1,10p'"
            ));
        }
    }

    private static int tapMatchingNodes(
            UiDevice device,
            Report report,
            List<UiNode> nodes,
            Pattern pattern,
            int tapLimit
    ) throws JSONException, IOException {
        int taps = 0;
        for (UiNode node : nodes) {
            if (taps >= tapLimit) {
                break;
            }
            if (!node.enabled || !node.clickable || node.bounds == null) {
                continue;
            }
            if (!pattern.matcher(node.searchText()).find()) {
                continue;
            }
            int x = node.bounds.centerX();
            int y = node.bounds.centerY();
            report.event("active_tap", node.toJson().put("x", x).put("y", y));
            device.click(x, y);
            taps += 1;
        }
        return taps;
    }

    private static void runOptionalSettingsScrolls(UiDevice device, Report report, Bundle args) throws Exception {
        int scrollCount = parseInt(args.getString("scrollSettingsForwardCount", "0"), 0);
        for (int index = 1; index <= scrollCount; index += 1) {
            try {
                UiScrollable settings = new UiScrollable(
                        new UiSelector().resourceId("com.oculus.panelapp.settings:id/settings_recycler_view")
                );
                settings.setAsVerticalList();
                boolean scrolled = settings.scrollForward();
                report.event("settings_scroll_forward", new JSONObject()
                        .put("index", index)
                        .put("scrolled", scrolled));
                device.waitForIdle(3000);
                Thread.sleep(1000);
                dumpAndClassify(device, report, "after_settings_scroll_forward_" + index, DEFAULT_CANDIDATE_PATTERN);
            } catch (Exception exception) {
                report.event("settings_scroll_forward", new JSONObject()
                        .put("index", index)
                        .put("scrolled", false)
                        .put("error", exception.toString()));
                break;
            }
        }
    }

    private static UiSnapshot dumpAndClassify(
            UiDevice device,
            Report report,
            String name,
            Pattern candidatePattern
    ) throws Exception {
        File xmlFile = new File(report.dir, name + ".xml");
        device.dumpWindowHierarchy(xmlFile);
        List<UiNode> nodes = parseNodes(xmlFile);
        JSONArray candidates = new JSONArray();
        JSONArray scrollables = new JSONArray();
        JSONArray checkedNodes = new JSONArray();
        Set<String> displayIds = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        int clickableCount = 0;
        int enabledCount = 0;
        for (UiNode node : nodes) {
            if (node.clickable) {
                clickableCount += 1;
            }
            if (node.enabled) {
                enabledCount += 1;
            }
            if (!node.displayId.isEmpty()) {
                displayIds.add(node.displayId);
            }
            if (!node.packageName.isEmpty()) {
                packages.add(node.packageName);
            }
            if (node.scrollable) {
                scrollables.put(node.toJson());
            }
            if (node.checked) {
                checkedNodes.put(node.toJson());
            }
            if (candidatePattern.matcher(node.searchText()).find()) {
                candidates.put(node.toJson());
            }
        }
        report.event("ui_dump", new JSONObject()
                .put("name", name)
                .put("xml", xmlFile.getAbsolutePath())
                .put("nodeCount", nodes.size())
                .put("clickableCount", clickableCount)
                .put("enabledCount", enabledCount)
                .put("scrollableCount", scrollables.length())
                .put("checkedCount", checkedNodes.length())
                .put("displayIds", new JSONArray(displayIds))
                .put("packages", new JSONArray(packages))
                .put("visibleTextHash", visibleTextHash(nodes))
                .put("candidateCount", candidates.length())
                .put("scrollables", scrollables)
                .put("checkedNodes", checkedNodes)
                .put("candidates", candidates));
        return new UiSnapshot(xmlFile, nodes);
    }

    private static void dumpAccessibilityState(
            Instrumentation instrumentation,
            Report report,
            String name
    ) throws JSONException, IOException {
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        JSONObject json = new JSONObject().put("name", name);
        try {
            JSONArray windowsJson = new JSONArray();
            List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
            int windowIndex = 0;
            for (AccessibilityWindowInfo window : windows) {
                Rect bounds = new Rect();
                window.getBoundsInScreen(bounds);
                AccessibilityNodeInfo root = window.getRoot();
                JSONArray nodes = new JSONArray();
                NodeStats stats = new NodeStats();
                collectAccessibilityNodes(root, "w" + windowIndex, 0, nodes, stats, 700);
                windowsJson.put(new JSONObject()
                        .put("index", windowIndex)
                        .put("id", window.getId())
                        .put("type", windowTypeName(window.getType()))
                        .put("layer", window.getLayer())
                        .put("active", window.isActive())
                        .put("focused", window.isFocused())
                        .put("accessibilityFocused", window.isAccessibilityFocused())
                        .put("displayId", reflectInt(window, "getDisplayId", -1))
                        .put("title", safeText(reflectObject(window, "getTitle")))
                        .put("bounds", rectToJson(bounds))
                        .put("nodeCount", stats.nodeCount)
                        .put("scrollableCount", stats.scrollableCount)
                        .put("actionNodeCount", stats.actionNodeCount)
                        .put("nodesCapped", stats.capped)
                        .put("nodes", nodes));
                windowIndex += 1;
            }
            json.put("windowCount", windows.size()).put("windows", windowsJson);

            AccessibilityNodeInfo activeRoot = uiAutomation.getRootInActiveWindow();
            JSONArray activeRootNodes = new JSONArray();
            NodeStats activeStats = new NodeStats();
            collectAccessibilityNodes(activeRoot, "active", 0, activeRootNodes, activeStats, 700);
            json.put("activeRoot", new JSONObject()
                    .put("nodeCount", activeStats.nodeCount)
                    .put("scrollableCount", activeStats.scrollableCount)
                    .put("actionNodeCount", activeStats.actionNodeCount)
                    .put("nodesCapped", activeStats.capped)
                    .put("nodes", activeRootNodes));
        } catch (Exception exception) {
            json.put("error", exception.toString())
                    .put("errorClass", exception.getClass().getName());
        }
        report.event("accessibility_state", json);
    }

    private static void collectAccessibilityNodes(
            AccessibilityNodeInfo node,
            String path,
            int depth,
            JSONArray nodes,
            NodeStats stats,
            int maxNodes
    ) throws JSONException {
        if (node == null) {
            return;
        }
        stats.nodeCount += 1;
        if (node.isScrollable()) {
            stats.scrollableCount += 1;
        }
        if (!node.getActionList().isEmpty()) {
            stats.actionNodeCount += 1;
        }
        if (nodes.length() < maxNodes) {
            nodes.put(accessibilityNodeSummary(node, path, depth));
        } else {
            stats.capped = true;
        }
        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index += 1) {
            collectAccessibilityNodes(node.getChild(index), path + "." + index, depth + 1, nodes, stats, maxNodes);
        }
    }

    private static AccessibilityNodeInfo findAccessibilityScrollTarget(
            UiAutomation uiAutomation,
            Pattern targetPattern,
            int[] actionIds
    ) {
        AccessibilityNodeInfo activeRoot = uiAutomation.getRootInActiveWindow();
        AccessibilityNodeInfo activeMatch = findAccessibilityScrollTargetInNode(activeRoot, targetPattern, actionIds);
        if (activeMatch != null) {
            return activeMatch;
        }
        try {
            for (AccessibilityWindowInfo window : uiAutomation.getWindows()) {
                AccessibilityNodeInfo match = findAccessibilityScrollTargetInNode(window.getRoot(), targetPattern, actionIds);
                if (match != null) {
                    return match;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static AccessibilityNodeInfo findAccessibilityScrollTargetInNode(
            AccessibilityNodeInfo node,
            Pattern targetPattern,
            int[] actionIds
    ) {
        if (node == null) {
            return null;
        }
        boolean matchesPattern = targetPattern == null || targetPattern.matcher(accessibilitySearchText(node)).find();
        if (matchesPattern && node.isScrollable() && hasAnyAction(node, actionIds)) {
            return node;
        }
        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index += 1) {
            AccessibilityNodeInfo match = findAccessibilityScrollTargetInNode(node.getChild(index), targetPattern, actionIds);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static JSONObject accessibilityNodeSummary(
            AccessibilityNodeInfo node,
            String path,
            int depth
    ) throws JSONException {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return new JSONObject()
                .put("path", path)
                .put("depth", depth)
                .put("windowId", node.getWindowId())
                .put("text", safeText(node.getText()))
                .put("contentDescription", safeText(node.getContentDescription()))
                .put("viewIdResourceName", nullToEmpty(node.getViewIdResourceName()))
                .put("className", safeText(node.getClassName()))
                .put("packageName", safeText(node.getPackageName()))
                .put("enabled", node.isEnabled())
                .put("clickable", node.isClickable())
                .put("longClickable", node.isLongClickable())
                .put("focusable", node.isFocusable())
                .put("focused", node.isFocused())
                .put("checkable", node.isCheckable())
                .put("checked", node.isChecked())
                .put("selected", node.isSelected())
                .put("scrollable", node.isScrollable())
                .put("childCount", node.getChildCount())
                .put("bounds", rectToJson(bounds))
                .put("actions", actionsToJson(node));
    }

    private static JSONArray actionsToJson(AccessibilityNodeInfo node) throws JSONException {
        JSONArray actions = new JSONArray();
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            actions.put(new JSONObject()
                    .put("id", action.getId())
                    .put("name", actionName(action.getId()))
                    .put("label", safeText(action.getLabel())));
        }
        return actions;
    }

    private static List<UiNode> parseNodes(File xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(xmlFile);
        NodeList xmlNodes = document.getElementsByTagName("node");
        List<UiNode> nodes = new ArrayList<>();
        for (int index = 0; index < xmlNodes.getLength(); index += 1) {
            Element element = (Element) xmlNodes.item(index);
            nodes.add(UiNode.from(element));
        }
        return nodes;
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            Log.w(TAG, "XML parser does not support feature: " + feature);
        }
    }

    private static String shell(UiDevice device, String command) throws IOException {
        return device.executeShellCommand(command);
    }

    private static String focusSummary(UiDevice device) throws IOException {
        return shell(
                device,
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -20; " +
                        "dumpsys window displays | grep -E 'mCurrentFocus|mFocusedApp' | head -20"
        );
    }

    private static Pattern optionalPattern(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Pattern.compile(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    private static List<String> parseStrategies(String value) {
        List<String> strategies = new ArrayList<>();
        String normalized = value == null || value.trim().isEmpty() ? "all" : value.trim();
        if ("all".equals(normalized)) {
            strategies.add("uiScrollable");
            strategies.add("uiScrollableFling");
            strategies.add("uiObject2");
            strategies.add("uiObject2UntilEnd");
            strategies.add("accessibilityAction");
            strategies.add("deviceSwipe");
            strategies.add("deviceDrag");
            strategies.add("toolTypeSwipe");
            strategies.add("shellSwipe");
            strategies.add("shellScroll");
            strategies.add("keyScroll");
            return strategies;
        }
        for (String raw : normalized.split(",")) {
            String strategy = raw.trim();
            if (!strategy.isEmpty()) {
                strategies.add(strategy);
            }
        }
        return strategies;
    }

    private static List<String> parseCsv(String value) {
        List<String> items = new ArrayList<>();
        if (value == null) {
            return items;
        }
        for (String raw : value.split(",")) {
            String item = raw.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private static Map<String, String> parseOptionTargets(String value) {
        Map<String, String> targets = new LinkedHashMap<>();
        if (value == null || value.trim().isEmpty()) {
            return targets;
        }
        for (String raw : value.split(";")) {
            String item = raw.trim();
            if (item.isEmpty()) {
                continue;
            }
            int separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                continue;
            }
            String key = item.substring(0, separator).trim();
            String option = item.substring(separator + 1).trim();
            if (!key.isEmpty() && !option.isEmpty()) {
                targets.put(key, option);
            }
        }
        return targets;
    }

    private static JSONArray optionTargetsToJson(Map<String, String> optionTargets) throws JSONException {
        JSONArray items = new JSONArray();
        for (Map.Entry<String, String> entry : optionTargets.entrySet()) {
            items.put(new JSONObject()
                    .put("target", entry.getKey())
                    .put("option", entry.getValue()));
        }
        return items;
    }

    private static String optionTargetForSpec(
            ChildTargetSpec spec,
            Map<String, String> optionTargets,
            String fallback
    ) {
        String fullKey = spec.section + ":" + spec.label;
        for (Map.Entry<String, String> entry : optionTargets.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase(fullKey) || key.equalsIgnoreCase(spec.label)) {
                return entry.getValue();
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static Pattern literalOrRegexPattern(String value) {
        String target = value == null ? "" : value.trim();
        if (target.startsWith("regex:")) {
            return Pattern.compile(target.substring("regex:".length()), Pattern.CASE_INSENSITIVE);
        }
        return Pattern.compile(".*" + Pattern.quote(target) + ".*", Pattern.CASE_INSENSITIVE);
    }

    private static Direction toDirection(String direction) {
        String normalized = direction == null ? "" : direction.toLowerCase(Locale.US);
        if ("up".equals(normalized) || "backward".equals(normalized)) {
            return Direction.UP;
        }
        if ("left".equals(normalized)) {
            return Direction.LEFT;
        }
        if ("right".equals(normalized)) {
            return Direction.RIGHT;
        }
        return Direction.DOWN;
    }

    private static boolean isForwardScrollDirection(String direction) {
        String normalized = direction == null ? "" : direction.toLowerCase(Locale.US);
        return !("up".equals(normalized) || "backward".equals(normalized) || "left".equals(normalized));
    }

    private static int[] scrollActionIds(String direction) {
        if (isForwardScrollDirection(direction)) {
            return new int[]{
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId(),
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId(),
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.getId()
            };
        }
        return new int[]{
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId(),
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId(),
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId()
        };
    }

    private static boolean hasAnyAction(AccessibilityNodeInfo node, int[] actionIds) {
        for (int actionId : actionIds) {
            if (hasAction(node, actionId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAction(AccessibilityNodeInfo node, int actionId) {
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action.getId() == actionId) {
                return true;
            }
        }
        return false;
    }

    private static String actionName(int actionId) {
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId()) {
            return "ACTION_CLICK";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.getId()) {
            return "ACTION_LONG_CLICK";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId()) {
            return "ACTION_SCROLL_FORWARD";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId()) {
            return "ACTION_SCROLL_BACKWARD";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()) {
            return "ACTION_SCROLL_UP";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) {
            return "ACTION_SCROLL_DOWN";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()) {
            return "ACTION_SCROLL_LEFT";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId()) {
            return "ACTION_SCROLL_RIGHT";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.getId()) {
            return "ACTION_PAGE_UP";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.getId()) {
            return "ACTION_PAGE_DOWN";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId()) {
            return "ACTION_EXPAND";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.getId()) {
            return "ACTION_COLLAPSE";
        }
        if (actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.getId()) {
            return "ACTION_SET_TEXT";
        }
        return "ACTION_" + actionId;
    }

    private static String windowTypeName(int type) {
        if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return "TYPE_APPLICATION";
        }
        if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return "TYPE_INPUT_METHOD";
        }
        if (type == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return "TYPE_SYSTEM";
        }
        if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return "TYPE_ACCESSIBILITY_OVERLAY";
        }
        if (type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return "TYPE_SPLIT_SCREEN_DIVIDER";
        }
        return "TYPE_" + type;
    }

    private static String uiObjectSearchText(UiObject2 object) {
        return nullToEmpty(object.getText()) + " " +
                nullToEmpty(object.getResourceName()) + " " +
                nullToEmpty(object.getClassName()) + " " +
                nullToEmpty(object.getApplicationPackage()) + " " +
                nullToEmpty(object.getContentDescription());
    }

    private static JSONObject uiObjectSummary(UiObject2 object) throws JSONException {
        return new JSONObject()
                .put("text", nullToEmpty(object.getText()))
                .put("resourceName", nullToEmpty(object.getResourceName()))
                .put("className", nullToEmpty(object.getClassName()))
                .put("packageName", nullToEmpty(object.getApplicationPackage()))
                .put("contentDescription", nullToEmpty(object.getContentDescription()))
                .put("displayId", object.getDisplayId())
                .put("scrollable", object.isScrollable())
                .put("clickable", object.isClickable())
                .put("enabled", object.isEnabled())
                .put("checked", object.isChecked())
                .put("bounds", rectToJson(object.getVisibleBounds()));
    }

    private static String accessibilitySearchText(AccessibilityNodeInfo node) {
        return safeText(node.getText()) + " " +
                nullToEmpty(node.getViewIdResourceName()) + " " +
                safeText(node.getClassName()) + " " +
                safeText(node.getPackageName()) + " " +
                safeText(node.getContentDescription());
    }

    private static SwipeSpec swipeSpec(Bundle args) {
        int startX = parseInt(args.getString("startX", "900"), 900);
        int startY = parseInt(args.getString("startY", "720"), 720);
        int endX = parseInt(args.getString("endX", "900"), 900);
        int endY = parseInt(args.getString("endY", "240"), 240);
        int steps = parseInt(args.getString("steps", "50"), 50);
        if (!isForwardScrollDirection(args.getString("direction", "down"))) {
            return new SwipeSpec(endX, endY, startX, startY, steps);
        }
        return new SwipeSpec(startX, startY, endX, endY, steps);
    }

    private static int toolType(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.US);
        if ("finger".equals(normalized)) {
            return MotionEvent.TOOL_TYPE_FINGER;
        }
        if ("stylus".equals(normalized)) {
            return MotionEvent.TOOL_TYPE_STYLUS;
        }
        if ("eraser".equals(normalized)) {
            return MotionEvent.TOOL_TYPE_ERASER;
        }
        if ("unknown".equals(normalized)) {
            return MotionEvent.TOOL_TYPE_UNKNOWN;
        }
        return MotionEvent.TOOL_TYPE_MOUSE;
    }

    private static List<Integer> resolveDisplayIds(Bundle args, UiSnapshot snapshot) {
        String requested = args.getString("displayIds", "");
        List<Integer> displayIds = new ArrayList<>();
        if (requested != null && !requested.trim().isEmpty()) {
            for (String raw : requested.split(",")) {
                String value = raw.trim();
                if (!value.isEmpty()) {
                    displayIds.add(parseInt(value, -1));
                }
            }
        }
        if (!displayIds.isEmpty()) {
            return displayIds;
        }
        Set<String> xmlDisplayIds = new LinkedHashSet<>();
        for (UiNode node : snapshot.nodes) {
            if (!node.displayId.isEmpty()) {
                xmlDisplayIds.add(node.displayId);
            }
        }
        for (String displayId : xmlDisplayIds) {
            displayIds.add(parseInt(displayId, -1));
        }
        if (displayIds.isEmpty()) {
            displayIds.add(-1);
        }
        return displayIds;
    }

    private static String inputPrefix(String source, int displayId) {
        String prefix = "input";
        if (source != null && !source.trim().isEmpty()) {
            prefix += " " + source.trim();
        }
        if (displayId >= 0) {
            prefix += " -d " + displayId;
        }
        return prefix;
    }

    private static String visibleTextHash(List<UiNode> nodes) {
        return Integer.toHexString(visibleTextJoined(nodes).hashCode());
    }

    private static JSONArray newVisibleTexts(List<UiNode> before, List<UiNode> after) {
        Set<String> beforeTexts = visibleTextSet(before);
        JSONArray added = new JSONArray();
        for (String text : visibleTextSet(after)) {
            if (!beforeTexts.contains(text)) {
                added.put(text);
            }
        }
        return added;
    }

    private static JSONArray visibleTexts(List<UiNode> nodes, int limit) {
        JSONArray texts = new JSONArray();
        for (String text : visibleTextSet(nodes)) {
            if (texts.length() >= limit) {
                break;
            }
            texts.put(text);
        }
        return texts;
    }

    private static JSONArray checkedNodeSummaries(List<UiNode> nodes, int limit) throws JSONException {
        JSONArray summaries = new JSONArray();
        for (UiNode node : nodes) {
            if (summaries.length() >= limit) {
                break;
            }
            if (!node.checkable && !node.checked) {
                continue;
            }
            summaries.put(node.toJson());
        }
        return summaries;
    }

    private static JSONArray clickableNodeSummaries(List<UiNode> nodes, int limit) throws JSONException {
        JSONArray summaries = new JSONArray();
        for (UiNode node : nodes) {
            if (summaries.length() >= limit) {
                break;
            }
            if (!node.clickable || !node.enabled) {
                continue;
            }
            summaries.put(new JSONObject()
                    .put("text", node.text)
                    .put("resourceId", node.resourceId)
                    .put("className", node.className)
                    .put("packageName", node.packageName)
                    .put("contentDescription", node.contentDescription)
                    .put("checkable", node.checkable)
                    .put("checked", node.checked)
                    .put("bounds", node.bounds == null ? JSONObject.NULL : rectToJson(node.bounds)));
        }
        return summaries;
    }

    private static JSONArray settingsContentTexts(List<UiNode> nodes, int limit) {
        JSONArray texts = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        for (UiNode node : nodes) {
            if (texts.length() >= limit) {
                break;
            }
            if (!isSettingsContentNode(node)) {
                continue;
            }
            String text = firstNonEmpty(node.text, node.contentDescription, node.resourceId);
            if (!text.isEmpty() && seen.add(text)) {
                texts.put(text);
            }
        }
        return texts;
    }

    private static JSONArray settingsContentCheckedNodeSummaries(List<UiNode> nodes, int limit) throws JSONException {
        JSONArray summaries = new JSONArray();
        for (UiNode node : nodes) {
            if (summaries.length() >= limit) {
                break;
            }
            if (!isSettingsContentNode(node) || (!node.checkable && !node.checked)) {
                continue;
            }
            summaries.put(node.toJson());
        }
        return summaries;
    }

    private static JSONArray settingsContentClickableNodeSummaries(List<UiNode> nodes, int limit) throws JSONException {
        JSONArray summaries = new JSONArray();
        for (UiNode node : nodes) {
            if (summaries.length() >= limit) {
                break;
            }
            if (!isSettingsContentNode(node) || !node.clickable || !node.enabled) {
                continue;
            }
            summaries.put(new JSONObject()
                    .put("text", node.text)
                    .put("resourceId", node.resourceId)
                    .put("className", node.className)
                    .put("contentDescription", node.contentDescription)
                    .put("checkable", node.checkable)
                    .put("checked", node.checked)
                    .put("bounds", node.bounds == null ? JSONObject.NULL : rectToJson(node.bounds)));
        }
        return summaries;
    }

    private static JSONObject settingsRouteInventory(
            List<UiNode> nodes,
            String target,
            int pageIndex,
            int limit
    ) throws JSONException {
        JSONArray candidates = new JSONArray();
        JSONObject countsByType = new JSONObject();
        Set<String> seen = new LinkedHashSet<>();
        for (UiNode node : nodes) {
            if (candidates.length() >= limit) {
                break;
            }
            String routeType = settingsRouteType(nodes, node);
            if (routeType.isEmpty()) {
                continue;
            }
            JSONArray rowTexts = settingsRouteRowTexts(nodes, node, 8);
            String label = firstRouteLabel(rowTexts, node);
            String value = settingsRouteValue(node, label);
            String key = routeType + "|" + label + "|" + value + "|" + node.resourceId + "|" +
                    (node.bounds == null ? "" : node.bounds.flattenToString());
            if (!seen.add(key)) {
                continue;
            }
            countsByType.put(routeType, countsByType.optInt(routeType, 0) + 1);
            candidates.put(new JSONObject()
                    .put("target", target)
                    .put("pageIndex", pageIndex)
                    .put("routeType", routeType)
                    .put("label", label)
                    .put("value", value)
                    .put("risk", settingsRouteRisk(target, label, routeType))
                    .put("recommendedProbe", settingsRouteRecommendedProbe(routeType))
                    .put("rowTexts", rowTexts)
                    .put("node", compactRouteNode(node)));
        }
        return new JSONObject()
                .put("target", target)
                .put("pageIndex", pageIndex)
                .put("candidateCount", candidates.length())
                .put("countsByType", countsByType)
                .put("candidates", candidates);
    }

    private static String settingsRouteType(List<UiNode> nodes, UiNode node) {
        if (!isSettingsContentNode(node) || node.bounds == null || !node.enabled || !node.clickable) {
            return "";
        }
        if (node.checkable || node.checked) {
            return "";
        }
        String resource = nullToEmpty(node.resourceId);
        String className = nullToEmpty(node.className);
        String search = node.searchText().toLowerCase(Locale.US);
        if (search.contains("toggle")) {
            return "";
        }
        if (resource.contains("context_menu_item")) {
            return "dropdown_option";
        }
        if (resource.contains("navigation_container")
                || node.contentDescription.toLowerCase(Locale.US).contains("navigate to next screen")) {
            return "child_page";
        }
        if (resource.contains("dropdown_button") || className.contains("Spinner")) {
            return "dropdown";
        }
        if (resource.contains("button_container") || className.contains("Button")) {
            return "button";
        }
        if (resource.contains("settings_list_item") && sameRowHasRouteControl(nodes, node.bounds)) {
            return "";
        }
        return "";
    }

    private static boolean sameRowHasRouteControl(List<UiNode> nodes, Rect rowBounds) {
        for (UiNode node : nodes) {
            if (!isSettingsContentNode(node) || node.bounds == null || node.bounds == rowBounds) {
                continue;
            }
            if (!sameRow(rowBounds, node.bounds)) {
                continue;
            }
            String resource = nullToEmpty(node.resourceId);
            String className = nullToEmpty(node.className);
            if (resource.contains("navigation_container")
                    || resource.contains("dropdown_button")
                    || resource.contains("button_container")
                    || className.contains("Spinner")
                    || className.contains("Button")) {
                return true;
            }
        }
        return false;
    }

    private static JSONArray settingsRouteRowTexts(List<UiNode> nodes, UiNode routeNode, int limit) {
        JSONArray texts = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        if (routeNode.bounds == null) {
            return texts;
        }
        for (UiNode node : nodes) {
            if (texts.length() >= limit) {
                break;
            }
            if (!isSettingsContentNode(node) || node.bounds == null || !sameRow(routeNode.bounds, node.bounds)) {
                continue;
            }
            String text = firstNonEmpty(node.text, node.contentDescription, "");
            if (!isRouteInventoryText(text) || !seen.add(text)) {
                continue;
            }
            texts.put(text);
        }
        return texts;
    }

    private static boolean sameRow(Rect first, Rect second) {
        int firstCenterY = first.centerY();
        int secondCenterY = second.centerY();
        if (secondCenterY >= first.top && secondCenterY <= first.bottom) {
            return true;
        }
        if (firstCenterY >= second.top && firstCenterY <= second.bottom) {
            return true;
        }
        return Math.abs(firstCenterY - secondCenterY) <= 48;
    }

    private static boolean isRouteInventoryText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (text.startsWith("com.") || text.startsWith("android:") || text.contains(":id/")) {
            return false;
        }
        return !text.matches("^[0-9\\s%:._/-]+$");
    }

    private static String firstRouteLabel(JSONArray rowTexts, UiNode node) {
        for (int index = 0; index < rowTexts.length(); index += 1) {
            String text = rowTexts.optString(index, "");
            if (isRouteInventoryText(text) && !text.equals(node.text) && !text.equals(node.contentDescription)) {
                return text;
            }
        }
        for (int index = 0; index < rowTexts.length(); index += 1) {
            String text = rowTexts.optString(index, "");
            if (isRouteInventoryText(text)) {
                return text;
            }
        }
        return firstNonEmpty(node.text, node.contentDescription, node.resourceId);
    }

    private static String settingsRouteValue(UiNode node, String label) {
        String text = firstNonEmpty(node.text, node.contentDescription, "");
        if (text.equals(label)) {
            return "";
        }
        return text;
    }

    private static String settingsRouteRisk(String target, String label, String routeType) {
        String normalizedTarget = target == null ? "" : target.toLowerCase(Locale.US);
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.US);
        if (normalizedLabel.contains("reset")
                || normalizedLabel.contains("delete")
                || normalizedLabel.contains("create")
                || normalizedLabel.contains("passcode")) {
            return "mutation_or_security_sensitive";
        }
        if ("button".equals(routeType)) {
            return "possible_mutation";
        }
        if ("dropdown".equals(routeType) || "dropdown_option".equals(routeType)) {
            return "open_selector_only";
        }
        if ("help".equals(normalizedTarget)) {
            return "external_surface";
        }
        if ("privacy_safety".equals(normalizedTarget)
                || "passcode_security".equals(normalizedTarget)
                || "developer".equals(normalizedTarget)) {
            return "sensitive_open_dump_only";
        }
        return "open_dump_only";
    }

    private static String settingsRouteRecommendedProbe(String routeType) {
        if ("dropdown".equals(routeType) || "dropdown_option".equals(routeType)) {
            return "settingsChildPageProbe childTargetRole=dropdown";
        }
        if ("child_page".equals(routeType)) {
            return "settingsChildPageProbe childTargetRole=row";
        }
        return "manual allowlist required";
    }

    private static JSONObject compactRouteNode(UiNode node) throws JSONException {
        return new JSONObject()
                .put("text", node.text)
                .put("resourceId", node.resourceId)
                .put("className", node.className)
                .put("contentDescription", node.contentDescription)
                .put("clickable", node.clickable)
                .put("checkable", node.checkable)
                .put("checked", node.checked)
                .put("bounds", node.bounds == null ? JSONObject.NULL : rectToJson(node.bounds));
    }

    private static JSONObject handleSettingsDropdownOptionTarget(
            Instrumentation instrumentation,
            UiDevice device,
            UiSnapshot snapshot,
            String optionTarget,
            boolean allowOptionSelect,
            String optionClickMode
    ) throws JSONException {
        Pattern pattern = literalOrRegexPattern(optionTarget);
        UiNode optionNode = findSettingsDropdownOptionNode(snapshot.nodes, pattern);
        JSONObject outcome = new JSONObject()
                .put("optionTarget", optionTarget)
                .put("pattern", pattern.pattern())
                .put("allowOptionSelect", allowOptionSelect)
                .put("optionClickMode", optionClickMode)
                .put("found", optionNode != null)
                .put("clicked", false);
        if (optionNode == null) {
            return outcome.put("reason", "no matching context_menu_item option");
        }
        outcome.put("option", settingsDropdownOptionSummary(snapshot.nodes, optionNode));
        if (!allowOptionSelect) {
            return outcome.put("reason", "allowOptionSelect=false dry run");
        }
        JSONObject clickAction = clickSettingsChildTarget(instrumentation, device, optionNode, optionClickMode);
        return outcome
                .put("clicked", clickAction.optBoolean("clicked", false))
                .put("clickAction", clickAction);
    }

    private static UiNode findSettingsDropdownOptionNode(List<UiNode> nodes, Pattern pattern) {
        for (UiNode optionNode : nodes) {
            if (!isSettingsDropdownOptionNode(optionNode)) {
                continue;
            }
            if (pattern.matcher(settingsDropdownOptionSearchText(nodes, optionNode)).find()) {
                return optionNode;
            }
        }
        return null;
    }

    private static boolean isSettingsDropdownOptionNode(UiNode node) {
        return isSettingsContentNode(node)
                && node.bounds != null
                && node.resourceId.contains("context_menu_item");
    }

    private static String settingsDropdownOptionSearchText(List<UiNode> nodes, UiNode optionNode) {
        StringBuilder builder = new StringBuilder(optionNode.searchText());
        if (optionNode.bounds == null) {
            return builder.toString();
        }
        for (UiNode rowNode : nodes) {
            if (!isSettingsContentNode(rowNode) || rowNode.bounds == null) {
                continue;
            }
            if (!boundsCenterInside(rowNode.bounds, optionNode.bounds)) {
                continue;
            }
            builder.append(' ').append(rowNode.searchText());
        }
        return builder.toString();
    }

    private static JSONObject settingsDropdownOptionSummary(List<UiNode> nodes, UiNode optionNode) throws JSONException {
        JSONArray texts = new JSONArray();
        boolean checked = optionNode.checked;
        boolean selected = optionNode.selected;
        boolean hasDefaultMarker = false;
        Set<String> seenTexts = new LinkedHashSet<>();
        if (optionNode.bounds != null) {
            for (UiNode rowNode : nodes) {
                if (!isSettingsContentNode(rowNode) || rowNode.bounds == null) {
                    continue;
                }
                if (!boundsCenterInside(rowNode.bounds, optionNode.bounds)) {
                    continue;
                }
                checked = checked || rowNode.checked;
                selected = selected || rowNode.selected;
                if (!rowNode.resourceId.contains("item_title")) {
                    continue;
                }
                String text = firstNonEmpty(rowNode.text, rowNode.contentDescription, "");
                if (!text.isEmpty() && seenTexts.add(text)) {
                    texts.put(text);
                    hasDefaultMarker = hasDefaultMarker || text.toLowerCase(Locale.US).contains("(default)");
                }
            }
        }
        return new JSONObject()
                .put("resourceId", optionNode.resourceId)
                .put("className", optionNode.className)
                .put("clickable", optionNode.clickable)
                .put("enabled", optionNode.enabled)
                .put("checked", checked)
                .put("selected", selected)
                .put("hasDefaultMarker", hasDefaultMarker)
                .put("texts", texts)
                .put("bounds", optionNode.bounds == null ? JSONObject.NULL : rectToJson(optionNode.bounds));
    }

    private static boolean boundsCenterInside(Rect inner, Rect outer) {
        int centerY = inner.centerY();
        if (centerY < outer.top || centerY > outer.bottom) {
            return false;
        }
        int centerX = inner.centerX();
        return centerX >= outer.left && centerX <= outer.right;
    }

    private static JSONArray settingsDropdownOptions(List<UiNode> nodes, int limit) throws JSONException {
        JSONArray options = new JSONArray();
        for (UiNode optionNode : nodes) {
            if (options.length() >= limit) {
                break;
            }
            if (!isSettingsDropdownOptionNode(optionNode)) {
                continue;
            }
            options.put(settingsDropdownOptionSummary(nodes, optionNode));
        }
        return options;
    }

    private static JSONObject settingsContentSummary(UiSnapshot snapshot) throws JSONException {
        return new JSONObject()
                .put("xml", snapshot.xmlFile.getAbsolutePath())
                .put("nodeCount", snapshot.nodes.size())
                .put("visibleTextHash", visibleTextHash(snapshot.nodes))
                .put("settingsContentTexts", settingsContentTexts(snapshot.nodes, 80))
                .put("settingsContentCheckedNodes", settingsContentCheckedNodeSummaries(snapshot.nodes, 40))
                .put("settingsContentClickableNodes", settingsContentClickableNodeSummaries(snapshot.nodes, 80))
                .put("settingsDropdownOptions", settingsDropdownOptions(snapshot.nodes, 40));
    }

    private static boolean isSettingsContentNode(UiNode node) {
        if (!"com.oculus.panelapp.settings".equals(node.packageName) || node.bounds == null) {
            return false;
        }
        return node.bounds.left >= 320 || node.bounds.right >= 360;
    }

    private static Set<String> visibleTextSet(List<UiNode> nodes) {
        Set<String> texts = new LinkedHashSet<>();
        for (UiNode node : nodes) {
            String text = firstNonEmpty(node.text, node.contentDescription, node.resourceId);
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static String visibleTextJoined(List<UiNode> nodes) {
        StringBuilder builder = new StringBuilder();
        for (String text : visibleTextSet(nodes)) {
            builder.append(text).append('|');
        }
        return builder.toString();
    }

    private static Object rectToJson(Rect bounds) throws JSONException {
        if (bounds == null) {
            return JSONObject.NULL;
        }
        return new JSONObject()
                .put("left", bounds.left)
                .put("top", bounds.top)
                .put("right", bounds.right)
                .put("bottom", bounds.bottom)
                .put("centerX", bounds.centerX())
                .put("centerY", bounds.centerY());
    }

    private static String sanitizeName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return third == null ? "" : third;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeText(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Object reflectObject(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static int reflectInt(Object target, String methodName, int fallback) {
        Object value = reflectObject(target, methodName);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return fallback;
    }

    private static final class NodeStats {
        int nodeCount;
        int scrollableCount;
        int actionNodeCount;
        boolean capped;
    }

    private static final class AccessibilityMatch {
        final AccessibilityNodeInfo node;
        final int score;

        AccessibilityMatch(AccessibilityNodeInfo node, int score) {
            this.node = node;
            this.score = score;
        }
    }

    private static final class ChildTargetSpec {
        final String section;
        final String label;

        ChildTargetSpec(String section, String label) {
            this.section = section;
            this.label = label;
        }

        static ChildTargetSpec parse(String raw) {
            if (raw == null) {
                return null;
            }
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                return null;
            }
            String section = raw.substring(0, separator).trim();
            String label = raw.substring(separator + 1).trim();
            if (section.isEmpty() || label.isEmpty()) {
                return null;
            }
            return new ChildTargetSpec(section, label);
        }

        Pattern pattern() {
            if (label.startsWith("regex:")) {
                return Pattern.compile(label.substring("regex:".length()), Pattern.CASE_INSENSITIVE);
            }
            return Pattern.compile(".*" + Pattern.quote(label) + ".*", Pattern.CASE_INSENSITIVE);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("section", section)
                    .put("label", label)
                    .put("pattern", pattern().pattern());
        }
    }

    private static final class SwipeSpec {
        final int startX;
        final int startY;
        final int endX;
        final int endY;
        final int steps;

        SwipeSpec(int startX, int startY, int endX, int endY, int steps) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.steps = steps;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("startX", startX)
                    .put("startY", startY)
                    .put("endX", endX)
                    .put("endY", endY)
                    .put("steps", steps);
        }
    }

    private static final class UiSnapshot {
        final File xmlFile;
        final List<UiNode> nodes;

        UiSnapshot(File xmlFile, List<UiNode> nodes) {
            this.xmlFile = xmlFile;
            this.nodes = nodes;
        }
    }

    private static final class UiNode {
        final String text;
        final String resourceId;
        final String className;
        final String packageName;
        final String contentDescription;
        final String displayId;
        final boolean clickable;
        final boolean enabled;
        final boolean scrollable;
        final boolean checkable;
        final boolean checked;
        final boolean selected;
        final boolean focusable;
        final boolean focused;
        final boolean longClickable;
        final Rect bounds;

        UiNode(
                String text,
                String resourceId,
                String className,
                String packageName,
                String contentDescription,
                String displayId,
                boolean clickable,
                boolean enabled,
                boolean scrollable,
                boolean checkable,
                boolean checked,
                boolean selected,
                boolean focusable,
                boolean focused,
                boolean longClickable,
                Rect bounds
        ) {
            this.text = text;
            this.resourceId = resourceId;
            this.className = className;
            this.packageName = packageName;
            this.contentDescription = contentDescription;
            this.displayId = displayId;
            this.clickable = clickable;
            this.enabled = enabled;
            this.scrollable = scrollable;
            this.checkable = checkable;
            this.checked = checked;
            this.selected = selected;
            this.focusable = focusable;
            this.focused = focused;
            this.longClickable = longClickable;
            this.bounds = bounds;
        }

        static UiNode from(Element element) {
            return new UiNode(
                    element.getAttribute("text"),
                    element.getAttribute("resource-id"),
                    element.getAttribute("class"),
                    element.getAttribute("package"),
                    element.getAttribute("content-desc"),
                    firstNonEmpty(element.getAttribute("display-id"), element.getAttribute("displayId"), ""),
                    Boolean.parseBoolean(element.getAttribute("clickable")),
                    Boolean.parseBoolean(element.getAttribute("enabled")),
                    Boolean.parseBoolean(element.getAttribute("scrollable")),
                    Boolean.parseBoolean(element.getAttribute("checkable")),
                    Boolean.parseBoolean(element.getAttribute("checked")),
                    Boolean.parseBoolean(element.getAttribute("selected")),
                    Boolean.parseBoolean(element.getAttribute("focusable")),
                    Boolean.parseBoolean(element.getAttribute("focused")),
                    Boolean.parseBoolean(element.getAttribute("long-clickable")),
                    parseBounds(element.getAttribute("bounds"))
            );
        }

        String searchText() {
            return text + " " + resourceId + " " + className + " " + packageName + " " + contentDescription;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject()
                    .put("text", text)
                    .put("resourceId", resourceId)
                    .put("className", className)
                    .put("packageName", packageName)
                    .put("contentDescription", contentDescription)
                    .put("displayId", displayId)
                    .put("clickable", clickable)
                    .put("enabled", enabled)
                    .put("scrollable", scrollable)
                    .put("checkable", checkable)
                    .put("checked", checked)
                    .put("selected", selected)
                    .put("focusable", focusable)
                    .put("focused", focused)
                    .put("longClickable", longClickable);
            if (bounds != null) {
                json.put("bounds", rectToJson(bounds));
            }
            return json;
        }

        private static Rect parseBounds(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            String[] parts = value.replace("[", "")
                    .replace("]", ",")
                    .split(",");
            if (parts.length < 4) {
                return null;
            }
            try {
                return new Rect(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static final class Report implements AutoCloseable {
        final File dir;
        final File reportFile;
        private final BufferedWriter writer;

        static Report create(Context context, String scenario) throws IOException {
            File root = context.getExternalFilesDir("sweeps");
            if (root == null) {
                root = context.getFilesDir();
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File dir = new File(root, stamp + "-" + scenario);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Could not create report directory: " + dir);
            }
            return new Report(dir, new File(dir, "report.jsonl"));
        }

        private Report(File dir, File reportFile) throws IOException {
            this.dir = dir;
            this.reportFile = reportFile;
            this.writer = new BufferedWriter(new FileWriter(reportFile, StandardCharsets.UTF_8, false));
        }

        void command(String name, String output) throws JSONException, IOException {
            event("command", new JSONObject()
                    .put("name", name)
                    .put("output", output));
        }

        void event(String type, JSONObject data) throws JSONException, IOException {
            JSONObject row = new JSONObject()
                    .put("type", type)
                    .put("timestampMs", System.currentTimeMillis())
                    .put("data", data);
            String line = row.toString();
            writer.write(line);
            writer.newLine();
            writer.flush();
            Log.i(TAG, line);
        }

        @Override
        public void close() throws IOException {
            writer.close();
            Files.write(new File(dir, "REPORT_PATH.txt").toPath(), reportFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        }
    }
}
