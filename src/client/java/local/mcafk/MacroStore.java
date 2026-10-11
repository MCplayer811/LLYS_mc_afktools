package local.mcafk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MacroStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("MC AFK Helper");
    private static final int MAGIC = 0x4D434146;
    private static final int VERSION = 2;
    private static final int MAX_MACROS = 256;
    private static final int MAX_TOTAL_EVENTS = 1_000_000;

    private final List<RecordedMacro> macros = new ArrayList<>();

    List<RecordedMacro> all() {
        return List.copyOf(macros);
    }

    void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file))
        )) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Macro file has an unsupported format.");
            }
            int version = input.readInt();
            if (version < 1 || version > VERSION) {
                throw new IOException("Macro file has an unsupported format.");
            }

            int macroCount = input.readInt();
            if (macroCount < 0 || macroCount > MAX_MACROS) {
                throw new IOException("Invalid macro count: " + macroCount);
            }

            List<RecordedMacro> loaded = new ArrayList<>(macroCount);
            Set<Integer> ids = new HashSet<>();
            long totalEvents = 0;
            for (int i = 0; i < macroCount; i++) {
                int id = input.readInt();
                int min = input.readInt();
                int max = input.readInt();
                boolean returnView = version >= 2 && input.readBoolean();
                boolean queued = version < 2 || input.readBoolean();
                String comment = version >= 2 ? input.readUTF() : "";
                int eventCount = input.readInt();
                validateInterval(min, max);
                validateComment(comment);
                totalEvents += eventCount;
                if (id < 1 || !ids.add(id) || eventCount < 0
                        || eventCount > MacroCapture.MAX_EVENTS
                        || totalEvents > MAX_TOTAL_EVENTS) {
                    throw new IOException("Invalid macro metadata for macro " + id);
                }

                List<MacroEvent> events = new ArrayList<>(eventCount);
                long previousTime = 0;
                for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
                    MacroEvent event = readEvent(input);
                    if (event.timeNanos() < previousTime) {
                        throw new IOException("Unordered events in macro " + id);
                    }
                    previousTime = event.timeNanos();
                    events.add(event);
                }
                loaded.add(new RecordedMacro(
                        id, min, max, events, returnView, queued, comment
                ));
            }

            if (input.read() != -1) {
                throw new IOException("Unexpected data after the last macro.");
            }
            macros.clear();
            macros.addAll(loaded);
            macros.sort(Comparator.comparingInt(RecordedMacro::id));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load macros from {}", file, exception);
            throw new IllegalStateException("无法读取宏文件：" + file, exception);
        }
    }

    RecordedMacro add(List<MacroEvent> events) throws IOException {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("录制内容为空");
        }
        if (macros.size() >= MAX_MACROS) {
            throw new IllegalStateException("最多只能保存 " + MAX_MACROS + " 个宏");
        }
        int totalEvents = macros.stream().mapToInt(macro -> macro.events().size()).sum();
        if ((long) totalEvents + events.size() > MAX_TOTAL_EVENTS) {
            throw new IllegalStateException("所有宏合计最多只能保存 " + MAX_TOTAL_EVENTS + " 个操作事件");
        }
        int id = macros.stream().mapToInt(RecordedMacro::id).max().orElse(0) + 1;
        RecordedMacro macro = new RecordedMacro(id, 60, 180, events);
        macros.add(macro);
        try {
            save();
        } catch (IOException | RuntimeException exception) {
            macros.remove(macro);
            throw exception;
        }
        return macro;
    }

    void replaceAll(List<RecordedMacro> replacement) throws IOException {
        if (replacement.size() > MAX_MACROS) {
            throw new IllegalArgumentException("宏数量超过上限");
        }
        long totalEvents = replacement.stream()
                .mapToLong(macro -> macro.events().size())
                .sum();
        if (totalEvents > MAX_TOTAL_EVENTS) {
            throw new IllegalArgumentException("所有宏的操作事件总数超过上限");
        }
        List<RecordedMacro> previous = new ArrayList<>(macros);
        macros.clear();
        macros.addAll(replacement);
        macros.sort(Comparator.comparingInt(RecordedMacro::id));
        try {
            save();
        } catch (IOException | RuntimeException exception) {
            macros.clear();
            macros.addAll(previous);
            throw exception;
        }
    }

    void save() throws IOException {
        Path file = file();
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary))
        )) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(macros.size());
            for (RecordedMacro macro : macros) {
                validateInterval(macro.minSeconds(), macro.maxSeconds());
                output.writeInt(macro.id());
                output.writeInt(macro.minSeconds());
                output.writeInt(macro.maxSeconds());
                output.writeBoolean(macro.returnView());
                output.writeBoolean(macro.queued());
                validateComment(macro.comment());
                output.writeUTF(macro.comment());
                output.writeInt(macro.events().size());
                for (MacroEvent event : macro.events()) {
                    writeEvent(output, event);
                }
            }
        }

        try {
            Files.move(
                    temporary, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path file() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("mcafk-macros.dat");
    }

    private static MacroEvent readEvent(DataInputStream input) throws IOException {
        long time = input.readLong();
        int typeId = input.readUnsignedByte();
        if (time < 0 || typeId >= MacroEvent.Type.values().length) {
            throw new IOException("Invalid macro event.");
        }
        MacroEvent.Type type = MacroEvent.Type.values()[typeId];
        return switch (type) {
            case KEY -> MacroEvent.key(
                    time, input.readInt(), input.readInt(), input.readInt(), input.readInt()
            );
            case CHARACTER -> MacroEvent.character(time, input.readInt());
            case MOUSE_BUTTON -> MacroEvent.mouseButton(
                    time, input.readInt(), input.readInt(), input.readInt()
            );
            case MOUSE_MOVE -> MacroEvent.mouseMove(
                    time, input.readDouble(), input.readDouble(),
                    input.readDouble(), input.readDouble()
            );
            case SCROLL -> MacroEvent.scroll(time, input.readDouble(), input.readDouble());
        };
    }

    private static void writeEvent(DataOutputStream output, MacroEvent event)
            throws IOException {
        output.writeLong(event.timeNanos());
        output.writeByte(event.type().ordinal());
        switch (event.type()) {
            case KEY -> {
                output.writeInt(event.first());
                output.writeInt(event.second());
                output.writeInt(event.third());
                output.writeInt(event.fourth());
            }
            case CHARACTER -> output.writeInt(event.first());
            case MOUSE_BUTTON -> {
                output.writeInt(event.first());
                output.writeInt(event.second());
                output.writeInt(event.third());
            }
            case MOUSE_MOVE -> {
                output.writeDouble(event.x());
                output.writeDouble(event.y());
                output.writeDouble(event.deltaX());
                output.writeDouble(event.deltaY());
            }
            case SCROLL -> {
                output.writeDouble(event.x());
                output.writeDouble(event.y());
            }
        }
    }

    private static void validateInterval(int min, int max) {
        if (min < 1 || max < min || max > 86_400) {
            throw new IllegalArgumentException("宏间隔必须满足 1 <= 最短 <= 最长 <= 86400 秒");
        }
    }

    private static void validateComment(String comment) {
        if (comment == null || comment.length() > 512) {
            throw new IllegalArgumentException("宏注释最多 512 个字符");
        }
    }
}
