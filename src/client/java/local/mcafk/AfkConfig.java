package local.mcafk;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

final class AfkConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MC AFK Helper");
    private static final List<String> DEFAULT_CHAT_MESSAGES = List.of(
            "The sunset looks beautiful from here.",
            "I wonder what we should build next.",
            "This is a nice place to explore.",
            "I found some interesting materials today.",
            "The world feels peaceful at this time.",
            "A small farm would be useful nearby.",
            "There are many places left to discover.",
            "I enjoy building things with friends.",
            "The view from this hill is amazing.",
            "Maybe we can explore the caves later."
    );

    int chatMinSeconds = 60;
    int chatMaxSeconds = 180;
    List<String> chatMessages = DEFAULT_CHAT_MESSAGES;

    void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            chatMinSeconds = read(properties, "chat.min", chatMinSeconds);
            chatMaxSeconds = read(properties, "chat.max", chatMaxSeconds);
            chatMessages = readMessages(properties.getProperty(
                    "chat.messages", String.join("\n", DEFAULT_CHAT_MESSAGES)
            ));
            validate();
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load MC AFK Helper settings from {}", file, exception);
            resetDefaults();
        }
    }

    void save() throws IOException {
        validate();
        Path file = file();
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        properties.setProperty("chat.min", Integer.toString(chatMinSeconds));
        properties.setProperty("chat.max", Integer.toString(chatMaxSeconds));
        properties.setProperty("chat.messages", String.join("\n", chatMessages));
        try (Writer writer = Files.newBufferedWriter(file)) {
            properties.store(writer, "MC AFK Helper chat interval (seconds)");
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("mcafk.properties");
    }

    private static int read(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer setting " + key, exception);
        }
    }

    private static List<String> readMessages(String value) {
        return Arrays.stream(value.split("\\R", -1))
                .map(String::trim)
                .filter(message -> !message.isEmpty())
                .toList();
    }

    private void validate() {
        if (chatMinSeconds < 1 || chatMaxSeconds < chatMinSeconds || chatMaxSeconds > 86_400) {
            throw new IllegalArgumentException("聊天间隔必须满足 1 <= 最短 <= 最长 <= 86400 秒");
        }
        if (chatMessages.size() > 100 || chatMessages.stream()
                .anyMatch(message -> message.length() > 256 || message.indexOf('\n') >= 0
                        || message.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("聊天句子最多 100 行，每行不能超过 256 个字符");
        }
    }

    private void resetDefaults() {
        chatMinSeconds = 60;
        chatMaxSeconds = 180;
        chatMessages = DEFAULT_CHAT_MESSAGES;
    }
}
