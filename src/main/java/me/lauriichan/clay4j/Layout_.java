package me.lauriichan.clay4j;

import java.util.Optional;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.clay4j.buildergen.BuilderDefault;
import me.lauriichan.clay4j.buildergen.BuilderTransformer;
import me.lauriichan.clay4j.buildergen.GenerateBuilder;
import me.lauriichan.clay4j.buildergen.ListReference;
import me.lauriichan.clay4j.buildergen.ListReference.Unmodifiable;

@GenerateBuilder
public record Layout_(ISizing width, ISizing height, Padding padding, int childGap, VAlignment childVerticalAlignment, HAlignment childHorizontalAlignment,
    LayoutDirection layoutDirection, boolean renderBackground, @ListReference(unmodifiable = @Unmodifiable(type = ObjectLists.class, method = "unmodifiable")) ObjectList<IElementConfig> configs) {

    @BuilderDefault({
        "width",
        "height"
    })
    public static final ISizing DEFAULT_SIZING = ISizing.fit();
    @BuilderDefault("padding")
    public static final Padding DEFAULT_PADDING = new Padding(4);
    @BuilderDefault("childGap")
    public static final int DEFAULT_CHILD_GAP = 4;
    @BuilderDefault("childVerticalAlignment")
    public static final VAlignment DEFAULT_CHILD_VERTICAL_ALIGNMENT = VAlignment.TOP;
    @BuilderDefault("childHorizontalAlignment")
    public static final HAlignment DEFAULT_CHILD_HORIZONTAL_ALIGNMENT = HAlignment.LEFT;
    @BuilderDefault("layoutDirection")
    public static final LayoutDirection DEFAULT_LAYOUT_DIRECTION = LayoutDirection.LEFT_TO_RIGHT;
    @BuilderDefault("renderBackground")
    public static final boolean DEFAULT_RENDER_BACKGROUND = false;
    
    @BuilderDefault("configs")
    private static final ObjectList<IElementConfig> newConfigList() {
        return new ObjectArrayList<>();
    }
    
    @BuilderTransformer("configs")
    private static ObjectList<IElementConfig> sort(ObjectList<IElementConfig> list) {
        if (list == null) {
            return ObjectLists.emptyList();
        }
        list.sort((e1, e2) -> Integer.compare(e1.priority(), e2.priority()));
        return list;
    }
    
    public <E extends IElementConfig> Optional<E> config(Class<E> type) {
        for (IElementConfig config : configs) {
            if (type.isAssignableFrom(config.getClass())) {
                return Optional.of(type.cast(config));
            }
        }
        return Optional.empty();
    }

    @GenerateBuilder
    public static record Padding(int left, int right, int top, int bottom) {
        
        @BuilderDefault({
            "left",
            "right",
            "top",
            "bottom"
        })
        public static final int DEFAULT_PADDING = 4;

        public Padding(int all) {
            this(all, all, all, all);
        }

    }

    public static enum VAlignment {
        TOP,
        CENTER,
        BOTTOM;
    }

    public static enum HAlignment {
        LEFT,
        CENTER,
        RIGHT;
    }

    public static enum LayoutDirection {

        LEFT_TO_RIGHT,
        TOP_TO_BOTTOM;

    }

}
