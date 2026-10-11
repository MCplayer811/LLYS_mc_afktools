package local.mcafk;

record MacroEvent(
        long timeNanos,
        Type type,
        int first,
        int second,
        int third,
        int fourth,
        double x,
        double y,
        double deltaX,
        double deltaY
) {
    enum Type {
        KEY,
        CHARACTER,
        MOUSE_BUTTON,
        MOUSE_MOVE,
        SCROLL
    }

    static MacroEvent key(long time, int action, int key, int keycode, int modifiers) {
        return new MacroEvent(time, Type.KEY, action, key, keycode, modifiers, 0, 0, 0, 0);
    }

    static MacroEvent character(long time, int codepoint) {
        return new MacroEvent(time, Type.CHARACTER, codepoint, 0, 0, 0, 0, 0, 0, 0);
    }

    static MacroEvent mouseButton(long time, int action, int button, int modifiers) {
        return new MacroEvent(time, Type.MOUSE_BUTTON, action, button, modifiers, 0, 0, 0, 0, 0);
    }

    static MacroEvent mouseMove(
            long time, double x, double y, double deltaX, double deltaY
    ) {
        return new MacroEvent(
                time, Type.MOUSE_MOVE, 0, 0, 0, 0, x, y, deltaX, deltaY
        );
    }

    static MacroEvent scroll(long time, double x, double y) {
        return new MacroEvent(time, Type.SCROLL, 0, 0, 0, 0, x, y, 0, 0);
    }
}
