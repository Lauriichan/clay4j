package me.lauriichan.clay4j;

import java.util.function.Consumer;

import me.lauriichan.clay4j.util.DebugPrinter;

public final class ElementContext {

    private final float layoutWidth, layoutHeight;
    private final Consumer<RenderCommand> pushCommand;

    int zIndex;
    boolean emitRectangle;
    boolean offscreen;
    BoundingBox boundingBox;

    public ElementContext(float layoutWidth, float layoutHeight, Consumer<RenderCommand> pushCommand) {
        this.layoutWidth = layoutWidth;
        this.layoutHeight = layoutHeight;
        this.pushCommand = pushCommand;
    }

    public boolean isOffscreen(BoundingBox boundingBox) {
        return isOffscreen(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
    }

    public boolean isOffscreen(float x, float y, float width, float height) {
        return x > layoutWidth || y > layoutHeight || x + width < 0 || y + height < 0;
    }

    public float layoutWidth() {
        return layoutWidth;
    }

    public float layoutHeight() {
        return layoutHeight;
    }

    public int zIndex() {
        return zIndex;
    }

    public boolean offscreen() {
        return offscreen;
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }
    
    public boolean emitRectangle() {
        return emitRectangle;
    }
    
    public void emitRectangle(boolean emitRectangle) {
        this.emitRectangle = emitRectangle;
    }

    public void push(RenderCommand command) {
        pushCommand.accept(command);
    }
    
    @Override
    public String toString() {
        DebugPrinter printer = new DebugPrinter();
        printer.append("layoutWidth", layoutWidth);
        printer.append("layoutHeight", layoutHeight);
        printer.append("zIndex", zIndex);
        printer.append("offscreen", offscreen);
        printer.append("emitRectangle", emitRectangle);
        printer.append("boundingBox", boundingBox);
        return printer.toString();
    }

}
