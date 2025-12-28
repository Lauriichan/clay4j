package me.lauriichan.clay4j;

import java.util.function.Consumer;

public final class ElementContext {

    private final float layoutWidth, layoutHeight;
    private final Consumer<ElementRenderer.RenderCommand> pushCommand;

    int zIndex;
    boolean emitRectangle;
    boolean offscreen;
    BoundingBox boundingBox;

    public ElementContext(float layoutWidth, float layoutHeight, Consumer<ElementRenderer.RenderCommand> pushCommand) {
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

    public void push(ElementRenderer.RenderCommand command) {
        pushCommand.accept(command);
    }

}
