package me.lauriichan.clay4j;

import me.lauriichan.clay4j.Layout.HAlignment;
import me.lauriichan.clay4j.buildergen.GenerateBuilder;
import me.lauriichan.clay4j.data.TextElementData;

public interface IElementConfig_ {

    default IElementData buildData(Element element) {
        return null;
    }
    
    default void buildCommands(ElementContext context, Element element, IElementConfig_ elementConfig) {}

    default int priority() {
        return 0;
    }
    
    public static record AspectRatio(float aspectRatio) implements IElementConfig_ {}

    @GenerateBuilder
    public static record Text(String text, IFont font, int fontSize, int letterSpacing, int lineHeight, WrapMode wrapMode,
        HAlignment alignment) implements IElementConfig_ {

        public static enum WrapMode {

            WRAP_WORDS,
            WRAP_NEWLINES,
            WRAP_NONE;

        }

        @Override
        public IElementData buildData(Element element) {
            LayoutContext.MeasuredText measured = element.context.measuredText(element.rootTime, (IElementConfig.Text) (Object) this);
            element.width = measured.width();
            element.height = lineHeight > 0 ? lineHeight : measured.height();
            element.minWidth = measured.minWidth();
            element.minHeight = element.height;
            return new TextElementData(measured.width(), measured.height());
        }
        
        @Override
        public void buildCommands(ElementContext context, Element element, IElementConfig_ elementConfig) {
            if (context.offscreen()) {
                return;
            }
            Text config = (Text) elementConfig;
            TextElementData data = element.data(TextElementData.class).get();
            float naturalLineHeight = data.preferredHeight;
            float finalLineHeight = config.lineHeight > 0 ? config.lineHeight : naturalLineHeight;
            float lineHeightOffset = (finalLineHeight - naturalLineHeight) / 2;
            float yPosition = lineHeightOffset;
            BoundingBox box = context.boundingBox;
            for (int index = 0; index < data.lines.size(); index++) {
                TextElementData.Line line = data.lines.get(index);
                if (line.text().isEmpty()) {
                    yPosition += finalLineHeight;
                    continue;
                }
                float offset = (box.width - line.width());
                if (config.alignment() == HAlignment.LEFT) {
                    offset = 0f;
                } else if (config.alignment() == HAlignment.CENTER) {
                    offset /= 2f;
                }
                context.push(new RenderCommand(RenderCommand.TEXT_RENDERER_ID, context.zIndex, element, new BoundingBox(box.x + offset, box.y + yPosition, line.width(), line.height()), line.text()));
                yPosition += finalLineHeight;
                if (box.y + yPosition > context.layoutHeight()) {
                    break;
                }
            }
        }

    }

    @GenerateBuilder
    public static record Floating(float xOffset, float yOffset, float expandWidth, float expandHeight, int zIndex, AttachPointType element,
        AttachPointType parent, PointerCaptureMode captureMode, AttachToElement attachTo, ClipToElement clipTo, String elementId) implements IElementConfig_ {

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
        
        public static enum PointerCaptureMode {

            CAPTURE,
            PASSTHROUGH;

        }
        
        @Override
        public IElementData buildData(Element element) {
            element.zIndex = zIndex;
            return null;
        }

    }

    @GenerateBuilder
    public static record Clip(boolean horizontal, boolean vertical, float xChildOffset, float yChildOffset)
        implements IElementConfig_ {
        
        @Override
        public int priority() {
            return -100;
        }
        
        @Override
        public void buildCommands(ElementContext context, Element element, IElementConfig_ elementConfig) {
            context.push(new RenderCommand(RenderCommand.CLIPPING_START_ID, element, context.boundingBox()));
        }
        
    }
    
    @GenerateBuilder
    public static record Border(BorderWidth width) implements IElementConfig_ {
        
        @Override
        public int priority() {
            return 100;
        }
        
        @GenerateBuilder
        public static record BorderWidth(int left, int right, int top, int bottom, boolean betweenChildren) {}
        
    }

}
