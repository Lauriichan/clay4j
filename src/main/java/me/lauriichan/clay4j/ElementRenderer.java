package me.lauriichan.clay4j;

public abstract class ElementRenderer {

    public static record RenderCommand(String id, int zIndex, Element element, BoundingBox boundingBox, Object data) {
        public RenderCommand(String id, Element element, BoundingBox boundingBox, Object data) {
            this(id, 0, element, boundingBox, data);
        }

        public RenderCommand(String id, int zIndex, Element element, BoundingBox boundingBox) {
            this(id, zIndex, element, boundingBox, null);
        }

        public RenderCommand(String id, Element element, BoundingBox boundingBox) {
            this(id, 0, element, boundingBox, null);
        }
    }
    
    public static final String TEXT_RENDERER_ID = "text";
    public static final String RECTANGLE_RENDERER_ID = "rectangle";
    
    public static final String CLIPPING_START_ID = "clipping_start";
    public static final String CLIPPING_END_ID = "clipping_end";

    private final String id;

    public ElementRenderer(String id) {
        this.id = id;
    }
    
    public final String id() {
        return id;
    }

    public abstract void render(Element element, BoundingBox boundingBox, Object data);

}
