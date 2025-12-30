package me.lauriichan.clay4j;

import java.util.Optional;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.clay4j.Layout.LayoutDirection;
import me.lauriichan.clay4j.buildergen.BuilderReference;
import me.lauriichan.clay4j.buildergen.FieldReference;
import me.lauriichan.clay4j.buildergen.GenerateBuilder;

@GenerateBuilder(name = "newElement", internal = true, rootName = "builder")
public final class Element_ implements AutoCloseable {

    public final LayoutContext context;

    public final long rootTime;

    public final Element_ parent;
    public final Layout layout;

    public final String elementId, clipElementId;

    public final boolean clipsVertical, clipsHorizontal;
    public final boolean isFloating, isText, hasAspectRatio;

    private final ObjectList<IElementData> elementData;

    private volatile boolean isClosed = false;

    final ObjectArrayList<Element_> children = new ObjectArrayList<>(), parentElements;

    BoundingBox boundingBox;

    float x, y;
    float minWidth, width;
    float minHeight, height;
    
    float percentageMaxY;
    
    boolean hovered = false;

    int zIndex = 0;

    Element_(@FieldReference LayoutContext context, @FieldReference("this") Element_ parent, @BuilderReference Layout layout,
        String elementId) {
        this.rootTime = parent == null ? System.currentTimeMillis() : parent.rootTime;
        this.context = context;
        this.parent = parent;
        this.layout = layout;
        this.elementId = elementId;
        this.isFloating = layout.config(IElementConfig.Floating.class).isPresent();
        this.parentElements = parent == null || isFloating ? new ObjectArrayList<>() : parent.parentElements;
        IElementConfig.Clip clip = layout.config(IElementConfig.Clip.class).orElse(null);
        if (clip != null) {
            clipsHorizontal = clip.horizontal();
            clipsVertical = clip.vertical();
        } else {
            clipsHorizontal = clipsVertical = false;
        }
        this.hasAspectRatio = layout.config(IElementConfig.AspectRatio.class).isPresent();
        // TODO: Root scroll containers still kinda don't work yet like this
        // This has to be set somewhere but not really a clue where yet
        this.clipElementId = null;
        this.isText = layout.config(IElementConfig.Text.class).isPresent();
        ObjectArrayList<IElementData> list = new ObjectArrayList<>();
        for (IElementConfig config : layout.configs()) {
            IElementData data = config.buildData((Element) (Object) this);
            if (data == null) {
                continue;
            }
            list.add(data);
        }
        this.elementData = ObjectLists.unmodifiable(list);
    }

    public <E extends IElementData> Optional<E> data(Class<E> type) {
        for (IElementData data : this.elementData) {
            if (type.isAssignableFrom(data.getClass())) {
                return Optional.of(type.cast(data));
            }
        }
        return Optional.empty();
    }
    
    public LayoutContext context() {
        return context;
    }
    
    public boolean isHovered() {
        return hovered;
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;
        
        if (parent != null && parent.isText) {
            throw new IllegalStateException("Text can't have child elements");
        }

        if (!isText && (!children.isEmpty() || parent == null || isFloating)) {
            parentElements.add(this);
        }
        context.addElement((Element) (Object) this);

        float leftRightPadding = layout.padding().left() + layout.padding().right();
        float topBottomPadding = layout.padding().top() + layout.padding().bottom();
        if (layout.layoutDirection() == LayoutDirection.LEFT_TO_RIGHT) {
            // Handle left to right layout
            minWidth = leftRightPadding;
            width = leftRightPadding;
            for (Element_ child : children) {
                width += child.width;
                height = Math.max(height, child.height + topBottomPadding);

                if (!clipsHorizontal) {
                    minWidth += child.minWidth;
                }
                if (!clipsVertical) {
                    minHeight = Math.max(minHeight, child.minHeight + topBottomPadding);
                }
            }
            float totalGaps = Math.max(children.size() - 1, 0) * layout.childGap();
            width += totalGaps;
            if (!clipsHorizontal) {
                minWidth += totalGaps;
            }
        } else {
            // Handle top to bottom layout
            minHeight = topBottomPadding;
            height = topBottomPadding;
            for (Element_ child : children) {
                width = Math.max(width, child.width + leftRightPadding);
                height += child.height;

                if (!clipsHorizontal) {
                    minWidth = Math.max(minWidth, child.minWidth + leftRightPadding);
                }
                if (!clipsVertical) {
                    minHeight += child.minHeight;
                }
            }
            float totalGaps = Math.max(children.size() - 1, 0) * layout.childGap();
            height += totalGaps;
            if (!clipsVertical) {
                minHeight += totalGaps;
            }
        }

        if (layout.width().type() != ISizing.Type.PERCENTAGE) {
            ISizing.MinMax minMax = layout.width().minMax();
            width = Math.min(Math.max(width, minMax.min()), minMax.max());
            minWidth = Math.min(Math.max(minWidth, minMax.min()), minMax.max());
        } else {
            width = 0f;
        }

        if (layout.height().type() != ISizing.Type.PERCENTAGE) {
            ISizing.MinMax minMax = layout.height().minMax();
            height = Math.min(Math.max(height, minMax.min()), minMax.max());
            minHeight = Math.min(Math.max(minHeight, minMax.min()), minMax.max());
        } else {
            height = 0f;
        }

        updateAspectRatioBox();

        context.setElementId(elementId, (Element) (Object) this);
        if (parent == null) {
            return;
        }
        // Floating elements are not added as children apparently
        if (!isFloating) {
            parent.children.add(this);
        }
    }

    final void updateAspectRatioBox() {
        layout.config(IElementConfig.AspectRatio.class).ifPresent(aspectRatio -> {
            if (aspectRatio.aspectRatio() <= 0) {
                return;
            }
            if (width == 0 && height != 0) {
                width = height * aspectRatio.aspectRatio();
            } else if (width != 0 && height == 0) {
                height = width * (1 / aspectRatio.aspectRatio());
            }
        });
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "elementId", elementId);
        appendField(builder, "clipElementId", clipElementId);
        appendField(builder, "x", x);
        appendField(builder, "y", y);
        appendField(builder, "z", zIndex);
        appendField(builder, "width", width);
        appendField(builder, "minWidth", minWidth);
        appendField(builder, "height", height);
        appendField(builder, "minHeight", minHeight);
        return builder.insert(0, "Element[").append(']').toString();
    }
    
    private void appendField(StringBuilder builder, String name, Object value) {
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        if (value == null) {
            value = "null";
        }
        builder.append(name).append('=').append(value);
    }

}
