package me.lauriichan.clay4j.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.clay4j.IElementData;

public final class TextElementData implements IElementData {

    public static record Line(float width, float height, String text) {}
    
    private final ObjectArrayList<Line> iLines = new ObjectArrayList<>();

    public final float preferredWidth, preferredHeight;
    public final ObjectList<Line> lines = ObjectLists.unmodifiable(iLines);

    public TextElementData(float preferredWidth, float preferredHeight) {
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }

    public void add(Line line) {
        iLines.add(line);
    }

    public void reset() {
        iLines.clear();
    }

}
