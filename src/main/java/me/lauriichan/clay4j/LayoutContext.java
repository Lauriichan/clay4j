package me.lauriichan.clay4j;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.lauriichan.clay4j.IElementConfig.Text.WrapMode;
import me.lauriichan.clay4j.Layout.LayoutDirection;

public final class LayoutContext {

    static record MeasuredWord(int start, int length, float width) {}

    static class MeasuredText {

        final int textHash, fontId, fontSize;
        final ObjectArrayList<MeasuredWord> words = new ObjectArrayList<>();

        volatile long lastAccess;

        float minWidth;
        float width, height;
        boolean containsNewLines = false;

        public MeasuredText(long lastAccess, int textHash, int fontId, int fontSize) {
            this.lastAccess = lastAccess;
            this.textHash = textHash;
            this.fontId = fontId;
            this.fontSize = fontSize;
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

    private final Object2ObjectOpenHashMap<String, Element> attachmentElements = new Object2ObjectOpenHashMap<>();

    private final Int2ObjectMap<MeasuredText> textCache = new Int2ObjectArrayMap<>(TEXT_CACHE_MAX_SIZE);

    private volatile boolean changed;

    public boolean hasChanged() {
        return changed;
    }

    public void reset() {
        roots.clear();
        textElements.clear();
        attachmentElements.clear();
        changed = true;
    }

    public Element.Builder newRoot() {
        return Element.builder(this);
    }

    /*
     * Internal functions
     */

    void addRoot(Object object) {
        if (!(object instanceof Element element)) {
            throwElementException();
            return;
        }
        roots.add(element);
        changed = true;
    }

    void addText(Object object) {
        if (!(object instanceof Element element)) {
            throwElementException();
            return;
        }
        textElements.add(element);
        changed = true;
    }

    void setAttachId(String attachId, Object object) {
        if (attachId == null) {
            return;
        }
        if (!(object instanceof Element element)) {
            throwElementException();
            return;
        }
        if (attachmentElements.containsKey(attachId)) {
            throw new IllegalArgumentException("Attachment id '%s' is already in use.".formatted(attachId));
        }
        attachmentElements.put(attachId, element);
    }

    /*
     * Text cache
     */

    final MeasuredText measuredText(long time, IElementConfig.Text config) {
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
                    measured.words.add(new MeasuredWord(start, length + 1, dimensions[0]));
                    lineWidth += dimensions[0];
                }
                if (current == '\n') {
                    if (length > 0) {
                        measured.words.add(new MeasuredWord(start, length, dimensions[0]));
                    }
                    measured.words.add(new MeasuredWord(end + 1, 0, 0f));
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
            measured.words.add(new MeasuredWord(start, end - start, dimensions[0]));
            lineWidth += dimensions[0];
            measuredHeight = Math.max(measuredHeight, dimensions[0]);
            measured.minWidth = Math.max(dimensions[0], measured.minWidth);
        }
        measuredWidth = Math.max(lineWidth, measuredWidth) - config.letterSpacing();

        measured.width = measuredWidth;
        measured.height = measuredHeight;

        if (measured.words.size() <= TEXT_CACHE_MAX_WORD_COUNT && textCache.get(hash) != measured) {
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
     * Layout calculations
     */

    public void finalize() {
        changed = false;

        // Size along x-axis
        sizeContainersAlongAxis(true);
        // Wrap text and propergate effects
        wrapText();
        // Size along y-axis
        sizeContainersAlongAxis(false);
    }

    private void wrapText() {
        long time = System.currentTimeMillis();
        for (Element textElement : textElements) {
            IElementConfig.Text config = textElement.layout.config(IElementConfig.Text.class).orElse(null);
            MeasuredText measured = measuredText(time, config);
            ObjectArrayList<String> wrappedLines = new ObjectArrayList<>();
            float lineWidth = 0, lineHeight = config.lineHeight(); // TODO: Add preferred text dimensions
            
        }
    }

    private void sizeContainersAlongAxis(boolean xAxis) {
        for (Element root : roots) {

            root.layout.config(IElementConfig.Floating.class).ifPresent(floating -> {
                if (floating.elementId() == null) {
                    return;
                }
                Element attachElement = attachmentElements.get(floating.elementId());
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

            for (Element parent : root.parentElements) {
                float parentSize = xAxis ? parent.width : parent.height;
                float parentPadding = xAxis ? parent.layout.padding().left() + parent.layout.padding().right()
                    : parent.layout.padding().top() + parent.layout.padding().bottom();
                float parentChildGap = parent.layout.childGap();
                float innerSize = 0, totalPaddingAndChildGaps = parentPadding;
                boolean sizingAlongAxis = (xAxis && parent.layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT)
                    || (!xAxis && parent.layout.layoutDirection() == LayoutDirection.TOP_TO_BOTTOM);
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
                            size = Math.min(maxSize, sizing.minMax().max());
                        }
                        size = Math.max(minSize, Math.min(size, maxSize));
                    }
                }
            }
        }
    }

    /*
     * Helper
     */

    private boolean valueEquals(float a, float b) {
        return (a = a - b) < TOLERANCE && a > -TOLERANCE;
    }

    private void throwElementException() {
        throw new IllegalArgumentException("Expected " + Element.class.getName());
    }

}
