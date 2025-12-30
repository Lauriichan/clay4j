package me.lauriichan.clay4j;

import java.util.concurrent.atomic.AtomicReference;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.clay4j.IElementConfig.Floating.AttachToElement;
import me.lauriichan.clay4j.IElementConfig.Floating.PointerCaptureMode;
import me.lauriichan.clay4j.IElementConfig.Text.WrapMode;
import me.lauriichan.clay4j.Layout.LayoutDirection;
import me.lauriichan.clay4j.data.TextElementData;

public final class LayoutContext {

    private static class TreeNode {

        private Element element;
        private float x, y;
        private float offsetX, offsetY;

        private boolean visited = false;

        public TreeNode(Element element) {
            this.element = element;
            this.offsetX = element.layout.padding().left();
            this.offsetY = element.layout.padding().top();
            this.x = element.x;
            this.y = element.y;
        }

    }

    private final class ScrollDataInternal {

        Element element;
        String elementId;

        BoundingBox boundingBox;

        float contentWidth, contentHeight;
        float scrollVelocityX, scrollVelocityY;
        float originX, originY;
        float pointerX, pointerY;
        float scrollX, scrollY;
        float time;

        boolean openThisFrame, pointerActive;

    }

    public static record MeasuredWord(int start, int length, float width) {}

    public static class MeasuredText {

        private final ObjectArrayList<MeasuredWord> iWords = new ObjectArrayList<>();

        public final int textHash, fontId, fontSize;
        public final ObjectList<MeasuredWord> words = ObjectLists.unmodifiable(iWords);

        private volatile long lastAccess;

        private float minWidth;
        private float width, height;
        private boolean containsNewLines = false;

        public MeasuredText(long lastAccess, int textHash, int fontId, int fontSize) {
            this.lastAccess = lastAccess;
            this.textHash = textHash;
            this.fontId = fontId;
            this.fontSize = fontSize;
        }

        public float minWidth() {
            return minWidth;
        }

        public float width() {
            return width;
        }

        public float height() {
            return height;
        }

        public boolean containsNewLines() {
            return containsNewLines;
        }

        @Override
        public final int hashCode() {
            return hash(textHash, fontId, fontSize);
        }

        public static int hash(int textHash, int fontId, int fontSize) {
            return textHash + (fontId << 8) + (fontSize << 16);
        }

    }

    private static final int TEXT_CACHE_MAX_SIZE = 32;
    private static final int TEXT_CACHE_MAX_WORD_COUNT = 32;

    private static final float TOLERANCE = 0.01f;

    private final ObjectArrayList<Element> roots = new ObjectArrayList<>();
    private final ObjectArrayList<Element> textElements = new ObjectArrayList<>();
    private final ObjectArrayList<Element> aspectRatioElements = new ObjectArrayList<>();

    private final Object2ObjectOpenHashMap<String, Element> id2elementMap = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<ScrollDataInternal> scrollDataList = new ObjectArrayList<>();

    private final Int2ObjectMap<MeasuredText> textCache = new Int2ObjectArrayMap<>(TEXT_CACHE_MAX_SIZE);

    private final AtomicReference<ObjectList<RenderCommand>> renderCommands = new AtomicReference<>(ObjectList.of());

    private final ObjectArrayList<Element> hovered = new ObjectArrayList<>();
    private final ObjectList<Element> immutableHovered = ObjectLists.unmodifiable(hovered);

    private volatile float pointerX = 0f, pointerY = 0f;
    private volatile PointerState pointerState = PointerState.RELEASED;

    private volatile float layoutWidth, layoutHeight;
    private volatile boolean changed;

    public LayoutContext() {
        this(0f, 0f);
    }

    public LayoutContext(float layoutWidth, float layoutHeight) {
        this.layoutWidth = layoutWidth;
        this.layoutHeight = layoutHeight;
    }
    
    public int rootAmount() {
        return roots.size();
    }

    public float width() {
        return layoutWidth;
    }

    public float height() {
        return layoutHeight;
    }

    public void setDimensions(float width, float height) {
        if (this.layoutWidth == width && this.layoutHeight == height) {
            return;
        }
        this.layoutWidth = width;
        this.layoutHeight = height;
        changed = true;
    }
    
    public Element elementById(String id) {
        return id2elementMap.get(id);
    }

    public float pointerX() {
        return pointerX;
    }

    public float pointerY() {
        return pointerY;
    }

    public PointerState pointerState() {
        return pointerState;
    }

    public ObjectList<Element> hoveredElements() {
        return immutableHovered;
    }

    public void setPointer(float x, float y, boolean pressed) {
        pointerX = x;
        pointerY = y;
        hovered.forEach(element -> element.hovered = false);
        hovered.clear();

        ObjectArrayList<Element> stack = new ObjectArrayList<>();
        for (int i = roots.size() - 1; i >= 0; i--) {
            Element root = roots.get(i);
            stack.push(root);
            boolean found = false;
            while (!stack.isEmpty()) {
                Element current = stack.removeLast();
                ScrollDataInternal scrollData = scrollDataById(current.clipElementId);
                if (current.boundingBox.isInside(x, y) && (scrollData == null || scrollData.boundingBox.isInside(x, y))) {
                    if (!hovered.contains(current)) {
                        hovered.add(current);
                        current.hovered = true;
                    }
                    found = true;
                }
                if (current.isText) {
                    continue;
                }
                for (int c = current.children.size() - 1; c >= 0; c--) {
                    stack.push(current.children.get(c));
                }
            }

            if (found && root.isFloating
                && root.layout.config(IElementConfig.Floating.class).get().captureMode() == PointerCaptureMode.CAPTURE) {
                break;
            }
        }

        if (pressed) {
            if (pointerState == PointerState.PRESSED_THIS_FRAME) {
                pointerState = PointerState.PRESSED;
            } else if (pointerState != PointerState.PRESSED) {
                pointerState = PointerState.PRESSED_THIS_FRAME;
            }
        } else {
            if (pointerState == PointerState.RELEASED_THIS_FRAME) {
                pointerState = PointerState.RELEASED;
            } else if (pointerState != PointerState.RELEASED) {
                pointerState = PointerState.RELEASED_THIS_FRAME;
            }
        }
    }

    public void updateScrollContainers(boolean enableDragScrolling, float scrollDeltaX, float scrollDeltaY, float deltaTime) {
        boolean isPointerActive = enableDragScrolling
            && (pointerState == PointerState.PRESSED || pointerState == PointerState.PRESSED_THIS_FRAME);
        // Don't apply scroll events to ancestors of the inner element
        int highestPriority = -1;
        ScrollDataInternal highestPriorityData = null;
        boolean scrollOccurred = scrollDeltaX != 0f || scrollDeltaY != 0f;
        for (int i = 0; i < scrollDataList.size(); i++) {
            ScrollDataInternal scrollData = scrollDataList.get(i);
            if (!scrollData.openThisFrame) {
                scrollDataList.remove(i--);
                continue;
            }
            scrollData.openThisFrame = false;
            if (!id2elementMap.containsKey(scrollData.elementId)) {
                scrollDataList.remove(i--);
                continue;
            }

            if (!isPointerActive && scrollData.pointerActive) {
                float xDiff = scrollData.scrollX - scrollData.originX;
                if (xDiff < -10 || xDiff > 10) {
                    scrollData.scrollVelocityX = (scrollData.scrollX - scrollData.originX) / (scrollData.time * 25);
                }
                float yDiff = scrollData.scrollY - scrollData.originY;
                if (yDiff < -10 || yDiff > 10) {
                    scrollData.scrollVelocityY = (scrollData.scrollY - scrollData.originY) / (scrollData.time * 25);
                }
                scrollData.pointerActive = false;

                scrollData.pointerX = scrollData.pointerY = 0f;
                scrollData.originX = scrollData.originY = 0f;
                scrollData.time = 0;
            }

            scrollData.scrollX += scrollData.scrollVelocityX;
            scrollData.scrollVelocityX *= 0.95f;
            if ((scrollData.scrollVelocityX > -0.1f && scrollData.scrollVelocityX < 0.1f) || scrollOccurred) {
                scrollData.scrollVelocityX = 0;
            }
            scrollData.scrollX = Math.min(Math.max(scrollData.scrollX, -Math.max(scrollData.contentWidth - scrollData.element.width, 0)),
                0);

            scrollData.scrollY += scrollData.scrollVelocityY;
            scrollData.scrollVelocityY *= 0.95f;
            if ((scrollData.scrollVelocityY > -0.1f && scrollData.scrollVelocityY < 0.1f) || scrollOccurred) {
                scrollData.scrollVelocityY = 0;
            }
            scrollData.scrollY = Math.min(Math.max(scrollData.scrollY, -Math.max(scrollData.contentHeight - scrollData.element.height, 0)),
                0);

            for (int j = 0; j < hovered.size(); j++) {
                if (scrollData.element != hovered.get(i)) {
                    continue;
                }
                highestPriority = j;
                highestPriorityData = scrollData;
            }
        }

        if (highestPriority > -1 && highestPriorityData != null) {
            Element scrollElement = highestPriorityData.element;
            boolean canScrollVertically = scrollElement.clipsVertical && highestPriorityData.contentHeight > scrollElement.height;
            boolean canScrollHorizontal = scrollElement.clipsHorizontal && highestPriorityData.contentWidth > scrollElement.width;

            if (canScrollVertically) {
                highestPriorityData.scrollY = highestPriorityData.scrollY + scrollDeltaY * 10;
            }
            if (canScrollHorizontal) {
                highestPriorityData.scrollX = highestPriorityData.scrollX + scrollDeltaX * 10;
            }

            if (isPointerActive) {
                highestPriorityData.scrollVelocityX = 0f;
                highestPriorityData.scrollVelocityY = 0f;
                if (!highestPriorityData.pointerActive) {
                    highestPriorityData.pointerX = pointerX;
                    highestPriorityData.pointerY = pointerY;
                    highestPriorityData.originX = highestPriorityData.scrollX;
                    highestPriorityData.originY = highestPriorityData.scrollY;
                    highestPriorityData.pointerActive = true;
                } else {
                    float deltaX = 0f, deltaY = 0f;
                    if (canScrollHorizontal) {
                        float oldScrollPos = highestPriorityData.scrollX;
                        highestPriorityData.scrollX = highestPriorityData.originX + (pointerX - highestPriorityData.pointerX);
                        highestPriorityData.scrollX = Math.max(Math.min(highestPriorityData.scrollX, 0),
                            -(highestPriorityData.contentWidth - highestPriorityData.boundingBox.width));
                        deltaX = highestPriorityData.scrollX - oldScrollPos;
                    }
                    if (canScrollVertically) {
                        float oldScrollPos = highestPriorityData.scrollY;
                        highestPriorityData.scrollY = highestPriorityData.originY + (pointerY - highestPriorityData.pointerY);
                        highestPriorityData.scrollY = Math.max(Math.min(highestPriorityData.scrollY, 0),
                            -(highestPriorityData.contentHeight - highestPriorityData.boundingBox.height));
                        deltaY = highestPriorityData.scrollY - oldScrollPos;
                    }
                    if (deltaX > -0.1f && deltaX < 0.1f && deltaY > -0.1f && deltaY < 0.1f && highestPriorityData.time > 0.15f) {
                        highestPriorityData.time = 0f;
                        highestPriorityData.pointerX = pointerX;
                        highestPriorityData.pointerY = pointerY;
                        highestPriorityData.originX = highestPriorityData.scrollX;
                        highestPriorityData.originY = highestPriorityData.scrollY;
                    } else {
                        highestPriorityData.time += deltaTime;
                    }
                }
            }

            if (canScrollVertically) {
                highestPriorityData.scrollY = Math.max(Math.min(highestPriorityData.scrollY, 0),
                    -(highestPriorityData.contentHeight - scrollElement.height));
            }
            if (canScrollHorizontal) {
                highestPriorityData.scrollX = Math.max(Math.min(highestPriorityData.scrollX, 0),
                    -(highestPriorityData.contentWidth - scrollElement.width));
            }
        }
    }

    public boolean hasChanged() {
        return changed;
    }

    public ObjectList<RenderCommand> renderCommands() {
        return renderCommands.get();
    }

    public void reset() {
        roots.clear();
        textElements.clear();
        aspectRatioElements.clear();
        id2elementMap.clear();
        scrollDataList.forEach(data -> data.element = null);
        changed = true;
    }

    public Element.Builder newRoot() {
        return Element.builder(this);
    }

    public boolean isOffscreen(BoundingBox boundingBox) {
        return isOffscreen(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
    }

    public boolean isOffscreen(float x, float y, float width, float height) {
        return x > layoutWidth || y > layoutHeight || x + width < 0 || y + height < 0;
    }

    public void calculateLayout() {
        changed = false;

        // Size along x-axis
        sizeContainersAlongAxis(true);
        // Wrap text
        wrapText();
        // Scale vertical heights according to aspect ratio
        scaleAspectVertical();
        // Propergate effects of text wrapping, aspect scaling etc. on height of parents
        propergateVerticalEffects();
        // Size along y-axis
        sizeContainersAlongAxis(false);
        // Scale horizontal widths according to aspect ratio
        scaleAspectHorizontal();

        // Sort roots by z-index
        ObjectArrayList<Element> sortedRoots = new ObjectArrayList<>();
        sortedRoots.addAll(roots);
        sortedRoots.sort((e1, e2) -> Integer.max(e1.zIndex, e2.zIndex));

        // Calculate final positions
        ObjectArrayList<RenderCommand> renderCommands = new ObjectArrayList<>();
        ElementContext context = new ElementContext(layoutWidth, layoutHeight, renderCommands::push);
        for (Element root : sortedRoots) {
            root.x = root.y = 0f;
            if (root.isFloating) {
                IElementConfig.Floating config = root.layout.config(IElementConfig.Floating.class).get();
                if (config.attachTo() == AttachToElement.ATTACH_TO_PARENT && root.parent != null) {
                    Element attachElement = targetOfFloating(root, config);
                    if (attachElement != null && attachElement.boundingBox != null) {
                        BoundingBox attachBox = attachElement.boundingBox;
                        switch (config.parent()) {
                        case TOP_LEFT:
                        case CENTER_LEFT:
                        case BOTTOM_LEFT:
                            root.x = attachBox.x();
                            break;
                        case TOP_CENTER:
                        case CENTER_CENTER:
                        case BOTTOM_CENTER:
                            root.x = attachBox.x() + (attachBox.width() / 2);
                            break;
                        case TOP_RIGHT:
                        case CENTER_RIGHT:
                        case BOTTOM_RIGHT:
                            root.x = attachBox.x() + attachBox.width();
                            break;
                        }
                        switch (config.element()) {
                        case TOP_LEFT:
                        case CENTER_LEFT:
                        case BOTTOM_LEFT:
                            break;
                        case TOP_CENTER:
                        case CENTER_CENTER:
                        case BOTTOM_CENTER:
                            root.x -= root.width / 2;
                            break;
                        case TOP_RIGHT:
                        case CENTER_RIGHT:
                        case BOTTOM_RIGHT:
                            root.x -= root.width;
                            break;
                        }
                        switch (config.parent()) {
                        case TOP_LEFT:
                        case TOP_CENTER:
                        case TOP_RIGHT:
                            root.y = attachBox.y();
                            break;
                        case CENTER_LEFT:
                        case CENTER_CENTER:
                        case CENTER_RIGHT:
                            root.y = attachBox.y() + (attachBox.height() / 2);
                            break;
                        case BOTTOM_LEFT:
                        case BOTTOM_CENTER:
                        case BOTTOM_RIGHT:
                            root.y = attachBox.y() + attachBox.height();
                            break;
                        }
                        switch (config.element()) {
                        case TOP_LEFT:
                        case TOP_CENTER:
                        case TOP_RIGHT:
                            break;
                        case CENTER_LEFT:
                        case CENTER_CENTER:
                        case CENTER_RIGHT:
                            root.y -= root.height / 2;
                            break;
                        case BOTTOM_LEFT:
                        case BOTTOM_CENTER:
                        case BOTTOM_RIGHT:
                            root.y -= root.height;
                            break;
                        }
                    }
                }
            }
            boolean rootHasToBeClosed = false;
            if (root.clipElementId != null && (root.clipsHorizontal || root.clipsVertical)) {
                Element element = id2elementMap.get(root.clipElementId);
                if (element != null && element.boundingBox != null) {
                    rootHasToBeClosed = true;
                    renderCommands.add(new RenderCommand(RenderCommand.CLIPPING_START_ID, root, element.boundingBox));
                }
            }
            context.zIndex = root.zIndex;
            ObjectArrayList<TreeNode> nodes = new ObjectArrayList<>();
            nodes.push(new TreeNode(root));
            while (!nodes.isEmpty()) {
                TreeNode node = nodes.getLast();
                Element element = node.element;
                float scrollOffsetX = 0f, scrollOffsetY = 0f;

                if (!node.visited) {
                    node.visited = true;

                    BoundingBox elementBox = new BoundingBox(node.x, node.y, element.width, element.height);
                    if (element.isFloating) {
                        IElementConfig.Floating floating = element.layout.config(IElementConfig.Floating.class).orElse(null);
                        elementBox.x -= floating.expandWidth();
                        elementBox.width += floating.expandWidth() * 2;
                        elementBox.y -= floating.expandHeight();
                        elementBox.height += floating.expandHeight() * 2;
                    }
                    element.boundingBox = elementBox;

                    ScrollDataInternal scrollData = null;
                    if (element.elementId != null && (element.clipsHorizontal || element.clipsVertical)) {
                        IElementConfig.Clip clip = element.layout.config(IElementConfig.Clip.class).orElse(null);
                        scrollData = scrollDataById(element.elementId);
                        scrollData.boundingBox = elementBox;
                        scrollOffsetX = clip.xChildOffset();
                        scrollOffsetY = clip.yChildOffset();
                    }

                    context.emitRectangle = element.layout.renderBackground();
                    context.offscreen = context.isOffscreen(elementBox);
                    context.boundingBox = elementBox;
                    int currentIndex = renderCommands.size();
                    for (IElementConfig config : element.layout.configs()) {
                        config.buildCommands(context, element, config);
                    }

                    if (context.emitRectangle) {
                        renderCommands.add(currentIndex, new RenderCommand(RenderCommand.RECTANGLE_RENDERER_ID, context.zIndex, element, elementBox));
                    }

                    if (!element.isText) {
                        float contentWidth = 0, contentHeight = 0;
                        if (element.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT) {
                            for (int i = 0; i < element.children.size(); i++) {
                                Element child = element.children.get(i);
                                contentWidth += child.width;
                                contentHeight = Math.max(contentHeight, child.height);
                            }
                            contentWidth += Math.max(0, element.children.size()) * element.layout.childGap();
                            float extraSpace = element.width - (element.layout.padding().left() + element.layout.padding().right())
                                - contentWidth;
                            switch (element.layout.childHorizontalAlignment()) {
                            case LEFT:
                                extraSpace = 0f;
                                break;
                            case CENTER:
                                extraSpace /= 2f;
                                break;
                            default:
                                break;
                            }
                            extraSpace = Math.max(0, extraSpace);
                            node.offsetX += extraSpace;
                        } else {
                            for (int i = 0; i < element.children.size(); i++) {
                                Element child = element.children.get(i);
                                contentHeight += child.height;
                                contentWidth = Math.max(contentWidth, child.width);
                            }
                            float extraSpace = element.height - (element.layout.padding().top() + element.layout.padding().bottom())
                                - contentHeight;
                            switch (element.layout.childVerticalAlignment()) {
                            case TOP:
                                extraSpace = 0f;
                                break;
                            case CENTER:
                                extraSpace /= 2f;
                                break;
                            default:
                                break;
                            }
                            extraSpace = Math.max(0, extraSpace);
                            node.offsetY += extraSpace;
                        }

                        if (scrollData != null) {
                            scrollData.contentWidth = contentWidth + element.layout.padding().left() + element.layout.padding().right();
                            scrollData.contentHeight = contentHeight + element.layout.padding().top() + element.layout.padding().bottom();
                        }
                    }
                } else {
                    boolean closeClip = false;
                    if (element.elementId != null && (element.clipsHorizontal || element.clipsVertical)) {
                        closeClip = true;
                        IElementConfig.Clip clip = element.layout.config(IElementConfig.Clip.class).orElse(null);
                        scrollOffsetX = clip.xChildOffset();
                        scrollOffsetY = clip.yChildOffset();
                    }

                    // TODO: Support borders
                    //                    IElementConfig.Border borderConfig = element.layout.config(IElementConfig.Border.class).orElse(null);
                    //                    if (borderConfig != null) {
                    //                        
                    //                    }
                    if (closeClip) {
                        renderCommands
                            .push(new RenderCommand(RenderCommand.CLIPPING_END_ID, element, element.boundingBox));
                    }

                    nodes.removeLast();
                    continue;
                }

                for (int childIndex = 0; childIndex < element.children.size(); childIndex++) {
                    Element child = element.children.get(childIndex);
                    if (element.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT) {
                        node.offsetY = element.layout.padding().top();
                        float whiteSpaceAroundChild = element.height - (element.layout.padding().top() + element.layout.padding().bottom())
                            - child.height;
                        switch (element.layout.childVerticalAlignment()) {
                        default:
                            break;
                        case CENTER:
                            node.offsetY += whiteSpaceAroundChild / 2f;
                            break;
                        case BOTTOM:
                            node.offsetY += whiteSpaceAroundChild;
                            break;
                        }
                    } else {
                        node.offsetX = element.layout.padding().left();
                        float whiteSpaceAroundChild = element.width - (element.layout.padding().left() + element.layout.padding().right())
                            - child.width;
                        switch (element.layout.childHorizontalAlignment()) {
                        default:
                            break;
                        case CENTER:
                            node.offsetX += whiteSpaceAroundChild / 2f;
                            break;
                        case RIGHT:
                            node.offsetX += whiteSpaceAroundChild;
                            break;
                        }
                    }

                    TreeNode childNode = new TreeNode(child);
                    childNode.x = node.x + node.offsetX + scrollOffsetX;
                    childNode.y = node.y + node.offsetY + scrollOffsetY;
                    nodes.add(nodes.size() - childIndex, childNode);

                    if (element.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT) {
                        node.offsetX += child.width + element.layout.childGap();
                    } else {
                        node.offsetY += child.height + element.layout.childGap();
                    }
                }
            }

            if (rootHasToBeClosed) {
                renderCommands.push(new RenderCommand(RenderCommand.CLIPPING_END_ID, root, root.boundingBox));
            }

        }
        this.renderCommands.set(renderCommands);
    }

    /*
     * Internal functions
     */

    void addElement(Element element) {
        boolean changed = false;
        if (element.isText) {
            textElements.add(element);
            changed = true;
        }
        if (element.hasAspectRatio) {
            aspectRatioElements.add(element);
            changed = true;
        }
        if (element.parent == null || element.isFloating) {
            roots.add(element);
            changed = true;
        }
        IElementConfig.Clip clip = element.layout.config(IElementConfig.Clip.class).orElse(null);
        if (clip != null && element.elementId != null) {
            ScrollDataInternal data = scrollDataById(element.elementId);
            if (data == null) {
                data = new ScrollDataInternal();
                data.elementId = element.elementId;
                data.originX = data.originY = -1f;
            }
            data.element = element;
            data.openThisFrame = true;
            changed = true;
        }

        if (changed) {
            this.changed = true;
        }
    }

    void setElementId(String elementId, Element element) {
        if (elementId == null) {
            return;
        }
        if (id2elementMap.containsKey(elementId)) {
            throw new IllegalArgumentException("Attachment id '%s' is already in use.".formatted(elementId));
        }
        id2elementMap.put(elementId, element);
    }

    /*
     * Layout calculations
     */

    private void wrapText() {
        long time = System.currentTimeMillis();
        float[] tmpSize = new float[2];
        for (Element textElement : textElements) {
            IElementConfig.Text config = textElement.layout.config(IElementConfig.Text.class).get();
            TextElementData textData = textElement.data(TextElementData.class).get();
            textData.reset();
            MeasuredText measured = measuredText(time, config);
            float lineWidth = 0, lineHeight = config.lineHeight() > 0 ? config.lineHeight() : textData.preferredHeight;
            int lineLength = 0, lineStartOffset = 0;
            if (!measured.containsNewLines() && textData.preferredWidth <= textElement.width) {
                textData.add(new TextElementData.Line(textElement.width, textElement.height, config.text()));
                continue;
            }
            String text = config.text();
            config.font().calculateSize(" ", config.fontSize(), tmpSize);
            float spaceWidth = tmpSize[0];
            MeasuredWord word;
            for (int i = 0; i < measured.words.size(); i++) {
                word = measured.words.get(i);
                if (lineLength == 0 && lineWidth + word.width() > textElement.width) {
                    textData.add(
                        new TextElementData.Line(word.width(), lineHeight, text.substring(word.start(), word.start() + word.length())));
                    lineStartOffset = word.start() + word.length();
                } else if (word.length() == 0 || lineWidth + word.width() > textElement.width) {
                    boolean finalCharIsSpace = text.charAt(lineStartOffset + lineLength - 1) == ' ';
                    textData.add(new TextElementData.Line(lineWidth + (finalCharIsSpace ? -spaceWidth : 0), lineHeight,
                        text.substring(lineStartOffset, lineStartOffset + lineLength + (finalCharIsSpace ? -1 : 0))));
                    if (lineLength != 0 && word.length() != 0) {
                        i--; // We go back by one
                    }
                    lineWidth = 0;
                    lineLength = 0;
                    lineStartOffset = word.start();
                } else {
                    lineWidth += word.width() + config.letterSpacing();
                    lineLength += word.length();
                }
            }
            if (lineLength > 0) {
                textData.add(new TextElementData.Line(lineWidth - config.letterSpacing(), lineHeight,
                    text.substring(lineStartOffset, lineStartOffset + lineLength)));
            }
            textElement.height = lineHeight * textData.lines.size();
        }
    }

    private void scaleAspectVertical() {
        IElementConfig.AspectRatio config;
        for (Element element : aspectRatioElements) {
            config = element.layout.config(IElementConfig.AspectRatio.class).get();
            element.height = (1 - config.aspectRatio()) * element.width;
            // This should scale the max height but that is not really possible right now
            // so we will skip this
        }
    }

    private void scaleAspectHorizontal() {
        IElementConfig.AspectRatio config;
        for (Element element : aspectRatioElements) {
            config = element.layout.config(IElementConfig.AspectRatio.class).get();
            element.width = config.aspectRatio() * element.height;
        }
    }

    private void propergateVerticalEffects() {
        for (Element root : roots) {
            for (Element parent : root.parentElements) {
                if (parent.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT) {
                    float childHeightWithPadding;
                    float parentPadding = parent.layout.padding().top() + parent.layout.padding().bottom();
                    for (Element child : parent.children) {
                        childHeightWithPadding = Math.max(child.height + parentPadding, parent.height);
                        parent.height = Math.min(Math.max(childHeightWithPadding, parent.layout.height().minMax().min()),
                            parent.layout.height().minMax().max());
                    }
                } else {
                    float contentHeight = parent.layout.padding().top() + parent.layout.padding().bottom();
                    for (Element child : parent.children) {
                        contentHeight += child.height;
                    }
                    contentHeight += Math.max(parent.children.size() - 1, 0) * parent.layout.childGap();
                    parent.height = Math.min(Math.max(contentHeight, parent.layout.height().minMax().min()),
                        parent.layout.height().minMax().max());
                }
            }
        }
    }

    private Element rootOf(Element element) {
        if (element.parent == null) {
            return null;
        }
        while (element.parent != null) {
            element = element.parent;
        }
        return element;
    }

    private Element targetOfFloating(Element element, IElementConfig.Floating config) {
        switch (config.attachTo()) {
        case ATTACH_TO_ELEMENT_WITH_ID:
            if (config.elementId() == null) {
                return null;
            }
            return id2elementMap.get(config.elementId());
        case ATTACH_TO_PARENT:
            return element.parent;
        case ATTACH_TO_ROOT:
            return rootOf(element);
        default:
        case ATTACH_TO_NONE:
            return null;
        }
    }

    private void sizeContainersAlongAxis(boolean xAxis) {
        for (Element root : roots) {
            root.layout.config(IElementConfig.Floating.class).ifPresent(floating -> {
                Element attachElement = targetOfFloating(root, floating);
                // Unknown element, rip
                if (attachElement == null) {
                    return;
                }
                switch (root.layout.width().type()) {
                case GROW -> {
                    root.width = attachElement.width;
                }
                case PERCENTAGE -> {
                    root.width = attachElement.width * root.layout.width().percentage();
                }
                default -> {
                }
                }
                switch (root.layout.height().type()) {
                case GROW -> {
                    root.height = attachElement.height;
                }
                case PERCENTAGE -> {
                    root.height = attachElement.height * root.layout.height().percentage();
                }
                default -> {
                }
                }
            });

            if (root.layout.width().type() != ISizing.Type.PERCENTAGE) {
                root.width = Math.min(Math.max(root.width, root.layout.width().minMax().min()), root.layout.width().minMax().max());
            }
            if (root.layout.height().type() != ISizing.Type.PERCENTAGE) {
                root.height = Math.min(Math.max(root.height, root.layout.height().minMax().min()), root.layout.height().minMax().max());
            }

            ObjectArrayList<Element> stack = new ObjectArrayList<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                Element parent = stack.pop();
                float parentSize = xAxis ? parent.width : parent.height;
                float parentPadding = xAxis ? parent.layout.padding().left() + parent.layout.padding().right()
                    : parent.layout.padding().top() + parent.layout.padding().bottom();
                float parentChildGap = parent.layout.childGap();
                float innerSize = 0, totalPaddingAndChildGaps = parentPadding;
                boolean sizingAlongAxis = (xAxis && (parent.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT))
                    || (!xAxis && (parent.layout.layoutDirection() == LayoutDirection.TOP_TO_BOTTOM));
                boolean isClipping = (xAxis && parent.clipsHorizontal) || (!xAxis && parent.clipsVertical);

                boolean hasGrowable = false;
                ObjectArrayList<Element> resizeable = new ObjectArrayList<>(parent.children.size());

                // Pre pass
                Element child;
                ISizing sizing;
                float size;
                for (int i = 0; i < parent.children.size(); i++) {
                    child = parent.children.get(i);
                    sizing = xAxis ? child.layout.width() : child.layout.height();
                    size = xAxis ? child.width : child.height;
                    
                    if (!child.children.isEmpty()) {
                        stack.push(child);
                    }

                    if (sizing.type() != ISizing.Type.PERCENTAGE && sizing.type() != ISizing.Type.FIXED
                        && child.layout.config(IElementConfig.Text.class).filter(cfg -> cfg.wrapMode() != WrapMode.WRAP_WORDS).isEmpty()) {
                        resizeable.add(child);
                    }

                    if (sizingAlongAxis) {
                        innerSize += (sizing.type() == ISizing.Type.PERCENTAGE ? 0 : size);
                        if (sizing.type() == ISizing.Type.GROW) {
                            hasGrowable = true;
                        }
                        if (i != 0) {
                            innerSize += parentChildGap;
                            totalPaddingAndChildGaps += parentChildGap;
                        }
                    } else {
                        innerSize = Math.max(size, innerSize);
                    }
                }

                // Expand percentage containers to size
                for (int i = 0; i < parent.children.size(); i++) {
                    child = parent.children.get(i);
                    sizing = xAxis ? child.layout.width() : child.layout.height();
                    if (sizing.type() != ISizing.Type.PERCENTAGE) {
                        continue;
                    }
                    size = (parentSize - totalPaddingAndChildGaps) * sizing.percentage();
                    if (sizingAlongAxis) {
                        innerSize += size;
                    }
                    if (xAxis) {
                        child.width = size;
                    } else {
                        child.height = size;
                    }
                    child.updateAspectRatioBox();
                }

                if (sizingAlongAxis) {
                    float sizeToDistribute = parentSize - parentPadding - innerSize;
                    if (sizeToDistribute < 0) {
                        if (isClipping) {
                            continue;
                        }
                        float largest, secondLargest, sizeToAdd, minAxis, previousSize;
                        while (sizeToDistribute < -TOLERANCE && !resizeable.isEmpty()) {
                            largest = 0;
                            secondLargest = 0;
                            sizeToAdd = sizeToDistribute;
                            for (int i = 0; i < resizeable.size(); i++) {
                                child = resizeable.get(i);
                                size = xAxis ? child.width : child.height;
                                if (valueEquals(size, largest)) {
                                    continue;
                                }
                                if (size > largest) {
                                    secondLargest = largest;
                                    largest = size;
                                }
                                if (size < largest) {
                                    secondLargest = Math.min(secondLargest, size);
                                    sizeToAdd = secondLargest - largest;
                                }
                            }
                            sizeToAdd = Math.max(sizeToAdd, sizeToDistribute / resizeable.size());
                            for (int i = 0; i < resizeable.size(); i++) {
                                child = resizeable.get(i);
                                size = xAxis ? child.width : child.height;
                                minAxis = xAxis ? child.minWidth : child.minHeight;
                                previousSize = size;
                                if (valueEquals(size, largest)) {
                                    size += sizeToAdd;
                                    if (size <= minAxis) {
                                        size = minAxis;
                                        resizeable.remove(i--);
                                    }
                                    sizeToDistribute -= (size - previousSize);
                                    if (xAxis) {
                                        child.width = size;
                                    } else {
                                        child.height = size;
                                    }
                                }
                            }
                        }
                    } else if (sizeToDistribute > 0 && hasGrowable) {
                        for (int i = 0; i < resizeable.size(); i++) {
                            child = resizeable.get(i);
                            sizing = xAxis ? child.layout.width() : child.layout.height();
                            if (sizing.type() != ISizing.Type.GROW) {
                                resizeable.remove(i--);
                            }
                        }
                        float smallest, secondSmallest, sizeToAdd, maxSize, previousSize;
                        while (sizeToDistribute > TOLERANCE && !resizeable.isEmpty()) {
                            smallest = Float.MAX_VALUE;
                            secondSmallest = Float.MAX_VALUE;
                            sizeToAdd = sizeToDistribute;
                            for (int i = 0; i < resizeable.size(); i++) {
                                child = resizeable.get(i);
                                size = xAxis ? child.width : child.height;
                                if (valueEquals(size, smallest)) {
                                    continue;
                                }
                                if (size < smallest) {
                                    secondSmallest = smallest;
                                    smallest = size;
                                }
                                if (size > smallest) {
                                    secondSmallest = Math.min(secondSmallest, size);
                                    sizeToAdd = secondSmallest - smallest;
                                }
                            }
                            sizeToAdd = Math.min(sizeToAdd, sizeToDistribute / resizeable.size());
                            for (int i = 0; i < resizeable.size(); i++) {
                                child = resizeable.get(i);
                                size = xAxis ? child.width : child.height;
                                maxSize = xAxis ? child.layout.width().minMax().max() : child.layout.height().minMax().max();
                                previousSize = size;
                                if (valueEquals(size, smallest)) {
                                    size += sizeToAdd;
                                    if (size >= maxSize) {
                                        size = maxSize;
                                        resizeable.remove(i--);
                                    }
                                    sizeToDistribute -= (size - previousSize);
                                    if (xAxis) {
                                        child.width = size;
                                    } else {
                                        child.height = size;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    float minSize, maxSize;
                    for (int i = 0; i < resizeable.size(); i++) {
                        child = resizeable.get(i);
                        sizing = xAxis ? child.layout.width() : child.layout.height();
                        size = xAxis ? child.width : child.height;
                        minSize = xAxis ? child.minWidth : child.minHeight;
                        maxSize = parentSize - parentPadding;
                        if (isClipping) {
                            maxSize = Math.max(maxSize, innerSize);
                        }
                        if (sizing.type() == ISizing.Type.GROW) {
                            size = Math.max(maxSize, sizing.minMax().max());
                        }
                        size = Math.max(minSize, Math.min(size, maxSize));
                    }
                }
            }
        }
    }

    /*
     * Text cache
     */

    public final MeasuredText measuredText(long time, IElementConfig.Text config) {
        int textHash = config.text().hashCode(), fontId = config.font().id(), fontSize = config.fontSize();
        int hash = MeasuredText.hash(textHash, fontId, fontSize);
        MeasuredText measured = textCache.get(hash);
        if (measured != null) {
            measured.lastAccess = time;
            return measured;
        }
        measured = new MeasuredText(time, textHash, fontId, fontSize);

        IFont font = config.font();
        float[] dimensions = new float[2];
        int start = 0, end = 0;
        float lineWidth = 0f;
        float measuredWidth = 0f, measuredHeight = 0f;
        font.calculateSize(" ", fontSize, dimensions);
        float spaceWidth = dimensions[0];
        String text = config.text();
        int stringLength = text.length(), length;
        while (end < stringLength) {
            char current = text.charAt(end);
            if (current == ' ' || current == '\n') {
                length = end - start;
                if (length > 0) {
                    font.calculateSize(text.substring(start, start + length), fontSize, dimensions);
                }
                measured.minWidth = Math.max(dimensions[0], measured.minWidth);
                measuredHeight = Math.max(measuredHeight, dimensions[1]);
                if (current == ' ') {
                    dimensions[0] += spaceWidth;
                    measured.iWords.add(new MeasuredWord(start, length + 1, dimensions[0]));
                    lineWidth += dimensions[0];
                }
                if (current == '\n') {
                    if (length > 0) {
                        measured.iWords.add(new MeasuredWord(start, length, dimensions[0]));
                    }
                    measured.iWords.add(new MeasuredWord(end + 1, 0, 0f));
                    lineWidth += dimensions[0];
                    measuredWidth = Math.max(lineWidth, measuredWidth);
                    measured.containsNewLines = true;
                    lineWidth = 0f;
                }
                start = end + 1;
            }
            end++;
        }
        if (end - start > 0) {
            font.calculateSize(text.substring(start, end), fontSize, dimensions);
            measured.iWords.add(new MeasuredWord(start, end - start, dimensions[0]));
            lineWidth += dimensions[0];
            measuredHeight = Math.max(measuredHeight, dimensions[0]);
            measured.minWidth = Math.max(dimensions[0], measured.minWidth);
        }
        measuredWidth = Math.max(lineWidth, measuredWidth) - config.letterSpacing();

        measured.width = measuredWidth;
        measured.height = measuredHeight;

        if (measured.iWords.size() <= TEXT_CACHE_MAX_WORD_COUNT && textCache.get(hash) != measured) {
            if (textCache.size() + 1 > TEXT_CACHE_MAX_SIZE) {
                popLastTextCacheEntry();
            }
            textCache.put(hash, measured);
        }
        return measured;
    }

    private void popLastTextCacheEntry() {
        Int2ObjectMap.Entry<MeasuredText> entry = textCache.int2ObjectEntrySet().stream()
            .sorted((t1, t2) -> Long.compare(t1.getValue().lastAccess, t2.getValue().lastAccess)).findFirst().orElse(null);
        if (entry == null) {
            return;
        }
        textCache.remove(entry.getIntKey());
    }

    /*
     * Helper
     */

    private ScrollDataInternal scrollDataById(String id) {
        return scrollDataList.stream().filter(data -> data.elementId.equals(id)).findFirst().orElse(null);
    }

    private boolean valueEquals(float a, float b) {
        return (a = a - b) < TOLERANCE && a > -TOLERANCE;
    }

}
