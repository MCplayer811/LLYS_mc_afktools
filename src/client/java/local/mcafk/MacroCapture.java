package local.mcafk;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.List;

public final class MacroCapture {
    static final int MAX_EVENTS = 1_000_000;
    private static final int[] CONTROL_KEYS = {
            InputConstants.KEY_F6,
            InputConstants.KEY_F7,
            InputConstants.KEY_F8,
            InputConstants.KEY_F10
    };

    private static List<MacroEvent> events;
    private static long startedAt;
    private static boolean dispatchingPlayback;
    private static boolean dispatchingMousePlayback;
    private static boolean overflowed;

    private MacroCapture() {
    }

    public static boolean start() {
        if (events != null || dispatchingPlayback) {
            return false;
        }
        events = new ArrayList<>();
        startedAt = System.nanoTime();
        overflowed = false;
        return true;
    }

    public static List<MacroEvent> finish() {
        if (events == null) {
            return List.of();
        }
        List<MacroEvent> result = List.copyOf(events);
        events = null;
        return result;
    }

    public static boolean isRecording() {
        return events != null;
    }

    public static boolean overflowed() {
        return overflowed;
    }

    public static void setDispatchingPlayback(boolean dispatching) {
        dispatchingPlayback = dispatching;
    }

    public static boolean isDispatchingMousePlayback() {
        return dispatchingMousePlayback;
    }

    public static void setDispatchingMousePlayback(boolean dispatching) {
        dispatchingMousePlayback = dispatching;
    }

    public static void key(int action, KeyEvent event) {
        if (!canCapture() || isControlKey(event)) {
            return;
        }
        add(MacroEvent.key(
                elapsed(), action, event.key(), event.keycode(), event.modifiers()
        ));
    }

    public static void character(CharacterEvent event) {
        if (canCapture()) {
            add(MacroEvent.character(elapsed(), event.codepoint()));
        }
    }

    public static void mouseButton(int action, MouseButtonInfo info) {
        if (canCapture()) {
            add(MacroEvent.mouseButton(
                    elapsed(), action, info.button(), info.modifiers()
            ));
        }
    }

    public static void mouseMove(double x, double y, double deltaX, double deltaY) {
        if (canCapture()) {
            add(MacroEvent.mouseMove(elapsed(), x, y, deltaX, deltaY));
        }
    }

    public static void scroll(double x, double y) {
        if (canCapture()) {
            add(MacroEvent.scroll(elapsed(), x, y));
        }
    }

    private static boolean canCapture() {
        Minecraft client = Minecraft.getInstance();
        return events != null && !dispatchingPlayback && client.player != null
                && !(client.gui.screen() instanceof AfkSettingsScreen);
    }

    private static boolean isControlKey(KeyEvent event) {
        for (int key : CONTROL_KEYS) {
            if (event.key() == key || event.keycode() == key) {
                return true;
            }
        }
        return false;
    }

    private static long elapsed() {
        return Math.max(0, System.nanoTime() - startedAt);
    }

    private static void add(MacroEvent event) {
        if (events.size() >= MAX_EVENTS) {
            overflowed = true;
            return;
        }
        events.add(event);
    }
}
