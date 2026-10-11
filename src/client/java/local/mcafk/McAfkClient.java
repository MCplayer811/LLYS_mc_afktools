package local.mcafk;

import com.mojang.blaze3d.platform.InputConstants;
import local.mcafk.mixin.MouseHandlerAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class McAfkClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MC AFK Helper");
    private static final long ACTION_GAP_NANOS = 1_000_000_000L;
    private static final int MAX_EVENTS_PER_TICK = 10_000;

    private final AfkConfig config = new AfkConfig();
    private final MacroStore macroStore = new MacroStore();
    private final Set<InputConstants.Key> playbackHeldInputs = new HashSet<>();
    private KeyMapping toggleKey;
    private KeyMapping settingsKey;
    private KeyMapping startRecordingKey;
    private KeyMapping stopRecordingKey;
    private boolean enabled;
    private boolean pauseOnLostFocusBeforeAfk;
    private long nextChatAt;
    private long nextMacroAt = Long.MAX_VALUE;
    private long nextActionAllowedAt;
    private long playbackStartedAt;
    private float playbackStartYaw;
    private float playbackStartPitch;
    private int nextMacroIndex;
    private RecordedMacro playingMacro;
    private int playbackEventIndex;

    @Override
    public void onInitializeClient() {
        toggleKey = registerKey("key.mcafk.toggle", InputConstants.KEY_F8);
        settingsKey = registerKey("key.mcafk.settings", InputConstants.KEY_F10);
        startRecordingKey = registerKey("key.mcafk.start_recording", InputConstants.KEY_F6);
        stopRecordingKey = registerKey("key.mcafk.stop_recording", InputConstants.KEY_F7);
        config.load();
        macroStore.load();
        resetTimers();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private static KeyMapping registerKey(String translationKey, int keyCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                keyCode,
                KeyMapping.Category.MISC
        ));
    }

    private void onClientTick(Minecraft client) {
        while (settingsKey.consumeClick()) {
            openSettings(client);
        }
        while (startRecordingKey.consumeClick()) {
            startRecording(client);
        }
        while (stopRecordingKey.consumeClick()) {
            stopRecording(client, false);
        }
        while (toggleKey.consumeClick()) {
            toggleAfk(client);
        }

        if (MacroCapture.overflowed()) {
            stopRecording(client, true);
        } else if (MacroCapture.isRecording()
                && (client.player == null || client.level == null)) {
            stopRecording(client, true);
        }

        if (MacroCapture.isRecording()) {
            return;
        }

        long now = System.nanoTime();
        if (playingMacro != null) {
            playbackTick(client, now);
            return;
        }

        if (!enabled || client.player == null || client.level == null
                || client.getConnection() == null || client.gui.screen() != null
                || now < nextActionAllowedAt) {
            return;
        }

        boolean chatDue = !config.chatMessages.isEmpty() && now >= nextChatAt;
        boolean macroDue = now >= nextMacroAt;
        if (!chatDue && !macroDue) {
            return;
        }

        if (chatDue && (!macroDue || nextChatAt <= nextMacroAt)) {
            sendRandomChat(client);
            nextChatAt = now + randomInterval(config.chatMinSeconds, config.chatMaxSeconds);
            nextActionAllowedAt = now + ACTION_GAP_NANOS;
        } else {
            startNextMacro(now);
            playbackTick(client, now);
        }
    }

    private void openSettings(Minecraft client) {
        Screen currentScreen = client.gui.screen();
        if (MacroCapture.isRecording() || playingMacro != null) {
            notify(client, "录制或播放宏期间不能打开设置");
        } else if (currentScreen instanceof AfkSettingsScreen) {
            currentScreen.onClose();
        } else if (currentScreen == null) {
            client.gui.setScreen(new AfkSettingsScreen(
                    config, macroStore, this::resetTimers
            ));
        }
    }

    private void startRecording(Minecraft client) {
        if (enabled || playingMacro != null || MacroCapture.isRecording()) {
            notify(client, "请先关闭挂机助手，再开始录制宏");
            return;
        }
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            notify(client, "请进入游戏世界后，在没有打开界面时按 F6");
            return;
        }
        if (MacroCapture.start()) {
            notify(client, "开始录制宏；按 F7 停止并保存");
        }
    }

    private void stopRecording(Minecraft client, boolean automatic) {
        if (!MacroCapture.isRecording()) {
            if (!automatic) {
                notify(client, "当前没有正在录制的宏");
            }
            return;
        }

        List<MacroEvent> events = MacroCapture.finish();
        if (events.isEmpty()) {
            notify(client, "宏没有记录到操作，未保存");
            return;
        }
        try {
            RecordedMacro macro = macroStore.add(events);
            if (automatic) {
                notify(client, "宏已自动停止并保存为 #" + macro.id()
                        + "（达到录制上限或离开世界）");
            } else {
                notify(client, "宏已保存为 #" + macro.id()
                        + "，共 " + events.size() + " 个输入事件");
            }
            if (enabled && playingMacro == null) {
                resetTimers();
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not save recorded macro", exception);
            notify(client, "宏保存失败：" + exception.getMessage());
        }
    }

    private void toggleAfk(Minecraft client) {
        if (MacroCapture.isRecording()) {
            notify(client, "录制期间不能开启挂机助手");
            return;
        }
        enabled = !enabled;
        if (!enabled) {
            stopPlayback();
            client.options.pauseOnLostFocus = pauseOnLostFocusBeforeAfk;
        } else {
            pauseOnLostFocusBeforeAfk = client.options.pauseOnLostFocus;
            client.options.pauseOnLostFocus = false;
            nextMacroIndex = 0;
        }
        resetTimers();
        notify(client, enabled ? "挂机助手已开启" : "挂机助手已关闭");
    }

    private void resetTimers() {
        long now = System.nanoTime();
        nextChatAt = config.chatMessages.isEmpty() ? Long.MAX_VALUE
                : now + randomInterval(config.chatMinSeconds, config.chatMaxSeconds);
        List<RecordedMacro> macros = macroStore.all();
        int nextQueued = findQueuedMacroIndex(macros, Math.max(0, nextMacroIndex), true);
        if (nextQueued < 0) {
            nextMacroAt = Long.MAX_VALUE;
            nextMacroIndex = -1;
        } else {
            nextMacroIndex = nextQueued;
            nextMacroAt = now + randomInterval(
                    macros.get(nextMacroIndex).minSeconds(),
                    macros.get(nextMacroIndex).maxSeconds()
            );
        }
        nextActionAllowedAt = now;
    }

    private static long randomInterval(int minSeconds, int maxSeconds) {
        return ThreadLocalRandom.current().nextLong(minSeconds, (long) maxSeconds + 1)
                * 1_000_000_000L;
    }

    private void sendRandomChat(Minecraft client) {
        String message = config.chatMessages.get(
                ThreadLocalRandom.current().nextInt(config.chatMessages.size())
        );
        client.getConnection().sendChat(message);
    }

    private void startNextMacro(long now) {
        List<RecordedMacro> macros = macroStore.all();
        if (macros.isEmpty() || nextMacroIndex < 0 || nextMacroIndex >= macros.size()
                || !macros.get(nextMacroIndex).queued()) {
            nextMacroAt = Long.MAX_VALUE;
            return;
        }
        playingMacro = macros.get(nextMacroIndex);
        playbackEventIndex = 0;
        playbackStartedAt = now;
        playbackStartYaw = Minecraft.getInstance().player.getYRot();
        playbackStartPitch = Minecraft.getInstance().player.getXRot();
        playbackHeldInputs.clear();
        MacroCapture.setDispatchingPlayback(true);
    }

    private void playbackTick(Minecraft client, long now) {
        if (client.player == null || client.level == null || client.getConnection() == null) {
            stopPlayback();
            return;
        }

        List<MacroEvent> events = playingMacro.events();
        long elapsed = now - playbackStartedAt;
        int dispatched = 0;
        while (playbackEventIndex < events.size()
                && events.get(playbackEventIndex).timeNanos() <= elapsed
                && dispatched < MAX_EVENTS_PER_TICK) {
            dispatch(client, events.get(playbackEventIndex++));
            dispatched++;
        }

        if (playbackEventIndex >= events.size()) {
            RecordedMacro completed = playingMacro;
            releaseHeldInputs();
            if (completed.returnView()) {
                client.player.setYRot(playbackStartYaw);
                client.player.setXRot(playbackStartPitch);
                client.player.yRotO = playbackStartYaw;
                client.player.xRotO = playbackStartPitch;
            }
            playingMacro = null;
            List<RecordedMacro> macros = macroStore.all();
            nextMacroIndex = findQueuedMacroIndex(macros, nextMacroIndex + 1, true);
            if (nextMacroIndex < 0) {
                nextMacroAt = Long.MAX_VALUE;
            } else {
                nextMacroAt = now + randomInterval(
                        completed.minSeconds(), completed.maxSeconds()
                );
            }
            nextActionAllowedAt = now + ACTION_GAP_NANOS;
        }
    }

    private static int findQueuedMacroIndex(
            List<RecordedMacro> macros, int startIndex, boolean includeStart
    ) {
        if (macros.isEmpty()) {
            return -1;
        }
        int first = Math.floorMod(startIndex + (includeStart ? 0 : 1), macros.size());
        for (int offset = 0; offset < macros.size(); offset++) {
            int index = (first + offset) % macros.size();
            if (macros.get(index).queued()) {
                return index;
            }
        }
        return -1;
    }

    private void dispatch(Minecraft client, MacroEvent event) {
        long window = client.getWindow().handle();
        MacroCapture.setDispatchingPlayback(true);
        try {
            switch (event.type()) {
                case KEY -> {
                    KeyEvent keyEvent = new KeyEvent(
                            event.second(), event.third(), event.fourth()
                    );
                    client.keyboardHandler.keyPress(window, event.first(), keyEvent);
                    trackHeldInput(
                            InputConstants.Type.KEYBOARD.getOrCreate(event.second()),
                            event.first()
                    );
                }
                case CHARACTER -> client.keyboardHandler.charTyped(
                        window, new CharacterEvent(event.first())
                );
                case MOUSE_BUTTON -> {
                    dispatchMouseButton(client, window, event);
                    trackHeldInput(
                            InputConstants.Type.MOUSE.getOrCreate(event.second()),
                            event.first()
                    );
                }
                case MOUSE_MOVE -> dispatchMouseMove(client, window, event);
                case SCROLL -> client.mouseHandler.onScroll(window, event.x(), event.y());
            }
        } finally {
            MacroCapture.setDispatchingMousePlayback(false);
            MacroCapture.setDispatchingPlayback(false);
        }
    }

    private static void dispatchMouseButton(Minecraft client, long window, MacroEvent event) {
        boolean windowWasActive = client.isWindowActive();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
        boolean wasMouseGrabbed = client.mouseHandler.isMouseGrabbed();
        MacroCapture.setDispatchingMousePlayback(true);
        if (!windowWasActive) {
            mouse.mcafk$setMouseGrabbed(true);
        }
        try {
            client.mouseHandler.onButton(
                    window,
                    new MouseButtonInfo(event.second(), event.third()),
                    event.first()
            );
        } finally {
            if (!windowWasActive) {
                mouse.mcafk$setMouseGrabbed(wasMouseGrabbed);
            }
            MacroCapture.setDispatchingMousePlayback(false);
        }
    }

    private static void dispatchMouseMove(Minecraft client, long window, MacroEvent event) {
        boolean windowWasActive = client.isWindowActive();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
        boolean wasMouseGrabbed = client.mouseHandler.isMouseGrabbed();
        MacroCapture.setDispatchingMousePlayback(true);
        if (!windowWasActive) {
            mouse.mcafk$setMouseGrabbed(true);
        }
        try {
            client.mouseHandler.onMove(
                    window, event.x(), event.y(), event.deltaX(), event.deltaY()
            );
            client.mouseHandler.handleAccumulatedMovement();
        } finally {
            if (!windowWasActive) {
                mouse.mcafk$setMouseGrabbed(wasMouseGrabbed);
            }
            MacroCapture.setDispatchingMousePlayback(false);
        }
    }

    private void trackHeldInput(InputConstants.Key key, int action) {
        if (action == 0) {
            playbackHeldInputs.remove(key);
        } else if (action == 1) {
            playbackHeldInputs.add(key);
        }
    }

    private void releaseHeldInputs() {
        for (InputConstants.Key key : playbackHeldInputs) {
            KeyMapping.set(key, false);
        }
        playbackHeldInputs.clear();
        MacroCapture.setDispatchingPlayback(false);
    }

    private void stopPlayback() {
        if (playingMacro != null) {
            releaseHeldInputs();
        }
        playingMacro = null;
        playbackEventIndex = 0;
    }

    private static void notify(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}
