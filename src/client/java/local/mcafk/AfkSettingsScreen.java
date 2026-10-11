package local.mcafk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AfkSettingsScreen extends Screen {
    private final AfkConfig config;
    private final MacroStore store;
    private final Runnable onSave;
    private final Map<Integer, int[]> intervalDrafts = new HashMap<>();
    private final Map<Integer, Boolean> returnViewDrafts = new HashMap<>();
    private final Map<Integer, Boolean> queuedDrafts = new HashMap<>();
    private final Map<Integer, String> commentDrafts = new HashMap<>();
    private final Set<Integer> deletedIds = new HashSet<>();
    private String chatMinDraft;
    private String chatMaxDraft;
    private String chatMessagesDraft;
    private EditBox chatMin;
    private EditBox chatMax;
    private MultiLineEditBox chatMessages;
    private StringWidget status;
    private MacroList macroList;
    private SettingsHeaderEntry settingsHeader;

    AfkSettingsScreen(AfkConfig config, MacroStore store, Runnable onSave) {
        super(Component.literal("挂机助手设置"));
        this.config = config;
        this.store = store;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        captureVisibleDrafts();
        double scrollAmount = macroList == null ? 0 : macroList.scrollAmount();
        int contentWidth = Math.max(1, Math.min(560, width - 24));
        int left = (width - contentWidth) / 2;
        int top = Math.max(10, Math.min(18, height / 12));
        int buttonsY = Math.max(0, height - 27);
        int statusY = buttonsY - 22;
        int listBottom = Math.max(top + 120, statusY - 6);
        int listHeight = Math.max(1, listBottom - top);
        List<RecordedMacro> macros = visibleMacros();
        macroList = new MacroList(contentWidth, listHeight, top, listBottom, left);
        settingsHeader = new SettingsHeaderEntry(left, contentWidth, macros.isEmpty());
        chatMessages = settingsHeader.chatMessages;
        macroList.addHeader(settingsHeader);
        for (RecordedMacro macro : macros) {
            intervalDrafts.putIfAbsent(
                    macro.id(), new int[]{macro.minSeconds(), macro.maxSeconds()}
            );
            returnViewDrafts.putIfAbsent(macro.id(), macro.returnView());
            queuedDrafts.putIfAbsent(macro.id(), macro.queued());
            commentDrafts.putIfAbsent(macro.id(), macro.comment());
            macroList.addMacro(macro);
        }
        macroList.setScrollAmount(scrollAmount);
        addRenderableWidget(macroList);

        status = new StringWidget(left, statusY, contentWidth, 16, Component.empty(), font);
        status.setMaxWidth(contentWidth);
        addRenderableOnly(status);
        addRenderableWidget(Button.builder(
                Component.literal("关闭（自动保存）"),
                button -> onClose()
        ).bounds(width / 2 - 75, buttonsY, 150, 20).build());
    }

    private List<RecordedMacro> visibleMacros() {
        return store.all().stream()
                .filter(macro -> !deletedIds.contains(macro.id()))
                .toList();
    }

    private static int macroFieldWidth(int rowWidth) {
        return Math.max(32, Math.min(52, (rowWidth - 257) / 2));
    }

    private static int macroColumnsWidth(int fieldWidth) {
        return 55 + fieldWidth * 2 + 64 * 2 + 44 + 6 * 5;
    }

    private void captureVisibleDrafts() {
        if (macroList == null) {
            return;
        }
        for (MacroListEntry entry : macroList.children()) {
            if (!(entry instanceof MacroEntry row)) {
                continue;
            }
            int[] previous = intervalDrafts.get(row.macro.id());
            int minFallback = previous == null ? row.macro.minSeconds() : previous[0];
            int maxFallback = previous == null ? row.macro.maxSeconds() : previous[1];
            intervalDrafts.put(row.macro.id(), new int[]{
                    parseDraft(row.min, minFallback),
                    parseDraft(row.max, maxFallback)
            });
            returnViewDrafts.put(row.macro.id(), row.returnView.value());
            queuedDrafts.put(row.macro.id(), row.queued.value());
            commentDrafts.put(row.macro.id(), row.comment.getValue());
        }
        if (settingsHeader != null) {
            chatMessages = settingsHeader.chatMessages;
            chatMinDraft = chatMin.getValue();
            chatMaxDraft = chatMax.getValue();
            chatMessagesDraft = chatMessages.getValue();
        }
    }

    private static int parseDraft(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static StringWidget label(int x, int y, String text) {
        Component message = Component.literal(text);
        return new StringWidget(
                x, y, Minecraft.getInstance().font.width(message), 9,
                message, Minecraft.getInstance().font
        );
    }

    private EditBox secondsField(int x, int y, int value, int fieldWidth) {
        EditBox box = new EditBox(font, x, y, fieldWidth, 20, Component.literal("秒"));
        box.setHint(Component.literal("秒"));
        box.setMaxLength(6);
        box.setValue(Integer.toString(value));
        return box;
    }

    private boolean saveSettings() {
        captureVisibleDrafts();
        try {
            int chatMinValue = parseRequired(chatMin);
            int chatMaxValue = parseRequired(chatMax);
            List<String> chatMessageValues = chatMessages.getValue().lines()
                    .map(String::trim)
                    .filter(message -> !message.isEmpty())
                    .toList();
            validateInterval(chatMinValue, chatMaxValue);
            validateChatMessages(chatMessageValues);

            List<RecordedMacro> replacement = new ArrayList<>();
            for (RecordedMacro macro : store.all()) {
                if (deletedIds.contains(macro.id())) {
                    continue;
                }
                int[] interval = intervalDrafts.getOrDefault(
                        macro.id(), new int[]{macro.minSeconds(), macro.maxSeconds()}
                );
                validateInterval(interval[0], interval[1]);
                String comment = commentDrafts.getOrDefault(macro.id(), macro.comment());
                validateComment(comment);
                replacement.add(new RecordedMacro(
                        macro.id(), interval[0], interval[1], macro.events(),
                        returnViewDrafts.getOrDefault(macro.id(), macro.returnView()),
                        queuedDrafts.getOrDefault(macro.id(), macro.queued()),
                        comment
                ));
            }

            int oldChatMin = config.chatMinSeconds;
            int oldChatMax = config.chatMaxSeconds;
            List<String> oldChatMessages = config.chatMessages;
            config.chatMinSeconds = chatMinValue;
            config.chatMaxSeconds = chatMaxValue;
            config.chatMessages = chatMessageValues;
            try {
                config.save();
                store.replaceAll(replacement);
            } catch (IOException | RuntimeException exception) {
                config.chatMinSeconds = oldChatMin;
                config.chatMaxSeconds = oldChatMax;
                config.chatMessages = oldChatMessages;
                try {
                    config.save();
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
            onSave.run();
            return true;
        } catch (NumberFormatException exception) {
            status.setMessage(Component.literal("请输入有效的整数秒数"));
        } catch (IOException | RuntimeException exception) {
            status.setMessage(Component.literal("保存失败：" + exception.getMessage()));
        }
        return false;
    }

    private static int parseRequired(EditBox box) {
        return Integer.parseInt(box.getValue());
    }

    private static void validateInterval(int min, int max) {
        if (min < 1 || max < min || max > 86_400) {
            throw new IllegalArgumentException("间隔必须满足 1 <= 最短 <= 最长 <= 86400 秒");
        }
    }

    private static void validateChatMessages(List<String> messages) {
        if (messages.size() > 100 || messages.stream().anyMatch(message -> message.length() > 256)) {
            throw new IllegalArgumentException("聊天内容最多 100 行，每行不能超过 256 个字符");
        }
    }

    private static void validateComment(String comment) {
        if (comment.length() > 512) {
            throw new IllegalArgumentException("宏注释最多 512 个字符");
        }
    }

    @Override
    public void onClose() {
        if (!saveSettings()) {
            return;
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void deleteMacro(int id) {
        captureVisibleDrafts();
        deletedIds.add(id);
        init(width, height);
    }

    private abstract class MacroListEntry
            extends ContainerObjectSelectionList.Entry<MacroListEntry> {
    }

    private final class MacroList extends ContainerObjectSelectionList<MacroListEntry> {
        private final int left;
        private final int rowWidth;

        private MacroList(int width, int height, int top, int bottom, int left) {
            super(Minecraft.getInstance(), width, height, top, bottom);
            this.left = left;
            this.rowWidth = Math.max(1, width - 12);
            setX(left);
        }

        private void addMacro(RecordedMacro macro) {
            addEntry(new MacroEntry(macro));
        }

        private void addHeader(SettingsHeaderEntry header) {
            addEntry(header);
        }

        @Override
        public int getRowLeft() {
            return left + 4;
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }
    }

    private final class SettingsHeaderEntry extends MacroListEntry {
        private final boolean noMacros;
        private final StringWidget intervalTitle;
        private final StringWidget minTitle;
        private final StringWidget maxTitle;
        private final StringWidget messagesTitle;
        private final StringWidget messagesHint;
        private final StringWidget macroTitle;
        private final StringWidget emptyMacros;
        private final List<StringWidget> columnHeaders = new ArrayList<>();
        private final MultiLineEditBox chatMessages;

        private SettingsHeaderEntry(int left, int contentWidth, boolean noMacros) {
            this.noMacros = noMacros;
            intervalTitle = label(0, 0, "聊天间隔（秒）");
            minTitle = label(0, 0, "最短");
            maxTitle = label(0, 0, "最长");
            messagesTitle = label(0, 0, "聊天内容");
            messagesHint = label(0, 0, "每行一句，随机发送；留空则停用");
            macroTitle = label(0, 0, "宏管理 · F6 开始录制 · F7 停止并保存");
            emptyMacros = label(0, 0, "还没有宏。进入游戏后按 F6 录制，按 F7 保存。");

            int chatFieldsLeft = left + Math.min(150, contentWidth / 3);
            chatMin = secondsField(chatFieldsLeft + 34, 0, config.chatMinSeconds, 64);
            chatMax = secondsField(chatFieldsLeft + 144, 0, config.chatMaxSeconds, 64);
            if (chatMinDraft != null) {
                chatMin.setValue(chatMinDraft);
            }
            if (chatMaxDraft != null) {
                chatMax.setValue(chatMaxDraft);
            }
            chatMessages = MultiLineEditBox.builder()
                    .setX(left)
                    .setY(0)
                    .setPlaceholder(Component.literal("每行输入一句聊天内容"))
                    .build(font, contentWidth - 8, 56, Component.literal("聊天内容"));
            chatMessages.setLineLimit(100);
            chatMessages.setCharacterLimit(25_600);
            chatMessages.setValue(chatMessagesDraft == null
                    ? String.join("\n", config.chatMessages) : chatMessagesDraft);

            addColumnHeader("编号", 55);
            int fieldWidth = macroFieldWidth(Math.max(1, contentWidth - 12));
            addColumnHeader("最短(秒)", fieldWidth);
            addColumnHeader("最长(秒)", fieldWidth);
            addColumnHeader("视角归位", 64);
            addColumnHeader("挂机队列", 64);
            addColumnHeader("操作", 44);
            setHeight(150);
            layoutWidgets();
        }

        private void addColumnHeader(String text, int columnWidth) {
            StringWidget header = label(0, 0, text);
            header.setMaxWidth(columnWidth);
            columnHeaders.add(header);
        }

        private void layoutWidgets() {
            int x = getX();
            int y = getY();
            int rowWidth = Math.max(1, getWidth());
            int chatFieldsLeft = x + Math.min(150, rowWidth / 3);
            intervalTitle.setPosition(x, y + 6);
            minTitle.setPosition(chatFieldsLeft, y + 6);
            chatMin.setPosition(chatFieldsLeft + 34, y + 3);
            maxTitle.setPosition(chatFieldsLeft + 110, y + 6);
            chatMax.setPosition(chatFieldsLeft + 144, y + 3);
            messagesTitle.setPosition(x, y + 30);
            messagesHint.setPosition(x + 70, y + 30);
            chatMessages.setPosition(x, y + 42);
            chatMessages.setWidth(Math.max(1, rowWidth - 8));
            macroTitle.setPosition(x, y + 103);

            int fieldWidth = macroFieldWidth(Math.max(1, rowWidth));
            int columnsWidth = macroColumnsWidth(fieldWidth);
            int columnX = x + (rowWidth - columnsWidth) / 2;
            int headerY = y + 122;
            int[] widths = {55, fieldWidth, fieldWidth, 64, 64, 44};
            for (int i = 0; i < columnHeaders.size(); i++) {
                int width = widths[i];
                Component text = columnHeaders.get(i).getMessage();
                int labelWidth = font.width(text);
                columnHeaders.get(i).setPosition(
                        columnX + (width - labelWidth) / 2, headerY
                );
                columnX += width + 6;
            }
            if (noMacros) {
                emptyMacros.setPosition(x, y + 138);
            }
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            layoutWidgets();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            layoutWidgets();
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            layoutWidgets();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(chatMin, chatMax, chatMessages);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(chatMin, chatMax, chatMessages);
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                boolean hovered, float partialTick
        ) {
            layoutWidgets();
            intervalTitle.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            minTitle.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            maxTitle.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            chatMin.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            chatMax.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            messagesTitle.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            messagesHint.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            chatMessages.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            macroTitle.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            for (StringWidget header : columnHeaders) {
                header.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            }
            if (noMacros) {
                emptyMacros.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public int getHeight() {
            return 150;
        }
    }

    private final class MacroEntry extends MacroListEntry {
        private final RecordedMacro macro;
        private final int[] interval;
        private final int fieldWidth;
        private final int deleteWidth = 44;
        private final StringWidget macroLabel;
        private final EditBox min;
        private final EditBox max;
        private final EditBox comment;
        private final RowToggleButton returnView;
        private final RowToggleButton queued;
        private final RowDeleteButton deleteButton;

        private MacroEntry(RecordedMacro macro) {
            this.macro = macro;
            interval = intervalDrafts.get(macro.id());
            int contentWidth = macroList.getRowWidth();
            fieldWidth = macroFieldWidth(contentWidth);
            macroLabel = label(0, 0, String.format(
                    "宏 %04d", macro.id()
            ));
            min = secondsField(0, 0, interval[0], fieldWidth);
            max = secondsField(0, 0, interval[1], fieldWidth);
            comment = new EditBox(font, 0, 0, Math.max(1, contentWidth - 8), 18,
                    Component.literal("宏注释"));
            comment.setHint(Component.literal("注释（可选）"));
            comment.setMaxLength(512);
            comment.setValue(commentDrafts.getOrDefault(macro.id(), macro.comment()));
            returnView = new RowToggleButton("归位", returnViewDrafts.getOrDefault(
                    macro.id(), macro.returnView()
            ), value -> returnViewDrafts.put(macro.id(), value));
            queued = new RowToggleButton("队列", queuedDrafts.getOrDefault(
                    macro.id(), macro.queued()
            ), value -> queuedDrafts.put(macro.id(), value));
            deleteButton = new RowDeleteButton(macro.id());
            macroLabel.setMaxWidth(65);
            comment.setHeight(18);
            setHeight(48);
            layoutWidgets();
        }

        private void layoutWidgets() {
            int x = getX();
            int y = getY();
            int columnsWidth = macroColumnsWidth(fieldWidth);
            int columnX = x + (getWidth() - columnsWidth) / 2;
            macroLabel.setPosition(columnX, y + 6);
            columnX += 55 + 6;
            min.setPosition(columnX + (fieldWidth - min.getWidth()) / 2, y + 3);
            columnX += fieldWidth + 6;
            max.setPosition(columnX + (fieldWidth - max.getWidth()) / 2, y + 3);
            columnX += fieldWidth + 6;
            returnView.setPosition(columnX + (64 - returnView.getWidth()) / 2, y + 3);
            columnX += 64 + 6;
            queued.setPosition(columnX + (64 - queued.getWidth()) / 2, y + 3);
            columnX += 64 + 6;
            deleteButton.setPosition(columnX + (44 - deleteWidth) / 2, y + 3);
            comment.setPosition(x, y + 26);
            comment.setWidth(Math.max(1, getWidth() - 8));
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            layoutWidgets();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            layoutWidgets();
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            layoutWidgets();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(min, max, returnView, queued, deleteButton, comment);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(min, max, returnView, queued, deleteButton, comment);
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                boolean hovered, float partialTick
        ) {
            layoutWidgets();
            macroLabel.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            min.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            max.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            returnView.renderState(graphics, mouseX, mouseY, partialTick);
            queued.renderState(graphics, mouseX, mouseY, partialTick);
            deleteButton.renderState(graphics, mouseX, mouseY, partialTick);
            comment.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public int getHeight() {
            return 48;
        }
    }

    private final class RowDeleteButton extends Button {
        private RowDeleteButton(int macroId) {
            super(0, 0, 48, 20, Component.literal("删除"),
                    button -> deleteMacro(macroId), DEFAULT_NARRATION);
        }

        private void renderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
        ) {
            extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
        ) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRenderer());
        }
    }

    private final class RowToggleButton extends Button {
        private final String title;
        private final java.util.function.Consumer<Boolean> onChange;
        private boolean value;

        private RowToggleButton(
                String title, boolean initialValue, java.util.function.Consumer<Boolean> onChange
        ) {
            super(0, 0, 60, 20, toggleMessage(title, initialValue),
                    button -> ((RowToggleButton) button).toggle(), DEFAULT_NARRATION);
            this.title = title;
            this.value = initialValue;
            this.onChange = onChange;
        }

        private static Component toggleMessage(String title, boolean value) {
            return Component.literal(title + ":" + (value ? "是" : "否"));
        }

        private void toggle() {
            value = !value;
            setMessage(toggleMessage(title, value));
            onChange.accept(value);
        }

        private boolean value() {
            return value;
        }

        private void renderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
        ) {
            extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
        ) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRenderer());
        }
    }

}
