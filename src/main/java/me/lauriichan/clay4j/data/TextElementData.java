package me.lauriichan.clay4j.data;

import me.lauriichan.clay4j.IElementData;

public final class TextElementData implements IElementData {
    
    private final float preferredWidth, preferredHeight;
    
    public TextElementData(float preferredWidth, float preferredHeight) {
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }
    
    public float preferredWidth() {
        return preferredWidth;
    }
    
    public float preferredHeight() {
        return preferredHeight;
    }

}
