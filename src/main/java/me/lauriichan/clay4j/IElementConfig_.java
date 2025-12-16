package me.lauriichan.clay4j;

import me.lauriichan.clay4j.Layout.HAlignment;
import me.lauriichan.clay4j.buildergen.GenerateBuilder;

public interface IElementConfig_ {

    public static record AspectRatio(float aspectRatio) implements IElementConfig_ {}

    @GenerateBuilder
    public static record Text(String text, IFont font, int fontSize, int letterSpacing,
        int lineHeight, WrapMode wrapMode, HAlignment alignment) implements IElementConfig_ {

        public static enum WrapMode {

            WRAP_WORDS,
            WRAP_NEWLINES,
            WRAP_NONE;

        }

    }

    @GenerateBuilder
    public static record Floating(float xOffset, float yOffset, float expandWidth, float expandHeight, int zIndex, AttachPointType element,
        AttachPointType parent, AttachToElement attachTo, ClipToElement clipTo, String elementId) implements IElementConfig_ {

        public static enum AttachPointType {

            TOP_LEFT,
            TOP_CENTER,
            TOP_RIGHT,

            CENTER_LEFT,
            CENTER_CENTER,
            CENTER_RIGHT,

            BOTTOM_LEFT,
            BOTTOM_CENTER,
            BOTTOM_RIGHT;

        }

        public static enum ClipToElement {

            NONE,
            ATTACHED_PARENT;

        }

        public static enum AttachToElement {

            ATTACH_TO_NONE,
            ATTACH_TO_PARENT,
            ATTACH_TO_ELEMENT_WITH_ID,
            ATTACH_TO_ROOT;

        }

    }

    @GenerateBuilder
    public static record Clip(boolean horizontal, boolean vertical, float xChildOffset, float yChildOffset) implements IElementConfig_ {}

}
