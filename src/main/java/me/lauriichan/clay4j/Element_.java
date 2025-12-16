package me.lauriichan.clay4j;

import java.util.Optional;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.clay4j.Layout.LayoutDirection;
import me.lauriichan.clay4j.buildergen.BuilderReference;
import me.lauriichan.clay4j.buildergen.FieldReference;
import me.lauriichan.clay4j.buildergen.GenerateBuilder;
import me.lauriichan.clay4j.data.TextElementData;

@GenerateBuilder(name = "newElement", internal = true, rootName = "builder")
public final class Element_ implements AutoCloseable {

    private final LayoutContext context;
    private final ObjectList<IElementData> elementData;

    private final long rootTime;
    
    final ObjectArrayList<Element_> children = new ObjectArrayList<>(), parentElements;

    final Element_ parent;
    final Layout layout;

    final String attachId;

    float minWidth, width;
    float minHeight, height;

    final boolean clipsVertical, clipsHorizontal;
    final boolean isFloating, isText;

    final IRenderable renderable;

    volatile boolean isClosed = false;

    Element_(@FieldReference LayoutContext context, @FieldReference("this") Element_ parent, @BuilderReference Layout layout,
        String attachId, IRenderable renderable) {
        this.parentElements = parent == null ? new ObjectArrayList<>() : parent.parentElements;
        this.rootTime = parent == null ? System.currentTimeMillis() : parent.rootTime;
        this.context = context;
        this.parent = parent;
        this.layout = layout;
        this.attachId = attachId;
        this.renderable = renderable;
        IElementConfig.Clip clip = layout.config(IElementConfig.Clip.class).orElse(null);
        if (clip != null) {
            clipsHorizontal = clip.horizontal();
            clipsVertical = clip.vertical();
        } else {
            clipsHorizontal = clipsVertical = false;
        }
        this.isFloating = layout.config(IElementConfig.Floating.class).isPresent();
        this.isText = layout.config(IElementConfig.Text.class).isPresent();
        this.elementData = ObjectLists.unmodifiable(buildElementData());
    }
    
    private ObjectList<IElementData> buildElementData() {
        ObjectArrayList<IElementData> list = new ObjectArrayList<>();
        if (isText) {
            IElementConfig.Text text = layout.config(IElementConfig.Text.class).get();
            LayoutContext.MeasuredText measured = context.measuredText(rootTime, text);
            width = measured.width;
            height = text.lineHeight() > 0 ? text.lineHeight() : measured.height;
            minWidth = measured.minWidth;
            minHeight = height;
            list.add(new TextElementData(measured.width, measured.height));
        }
        // TODO: Add scroll data
        return list;
    }

    public <E extends IElementData> Optional<E> data(Class<E> type) {
        for (IElementData data : this.elementData) {
            if (type.isAssignableFrom(data.getClass())) {
                return Optional.of(type.cast(type));
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        if (!isText && !children.isEmpty()) {
            parentElements.add(this);
        }
        if (isText) {
            context.addText(this);
        }

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

        context.setAttachId(attachId, this);
        if (parent == null) {
            context.addRoot(this);
            return;
        }
        if (parent.isText) {
            throw new IllegalStateException("Text can't have child elements");
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

}
