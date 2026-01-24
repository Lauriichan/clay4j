package me.lauriichan.clay4j;

public enum PointerState {

    PRESSED_THIS_FRAME(true, true),
    PRESSED(true, false),
    RELEASED_THIS_FRAME(false, true),
    RELEASED(false, false);

    private final boolean pressed, thisFrame;

    private PointerState(boolean pressed, boolean thisFrame) {
        this.pressed = pressed;
        this.thisFrame = thisFrame;
    }
    
    public boolean hasJustHappened() {
        return thisFrame;
    }

    public boolean hasPressed() {
        return pressed;
    }

    public boolean hasJustPressed() {
        return pressed && thisFrame;
    }

    public boolean hasReleased() {
        return !pressed;
    }

    public boolean hasJustReleased() {
        return !pressed && thisFrame;
    }

}
