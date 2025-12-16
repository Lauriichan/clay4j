# Clay4j

[Clay (short for C Layout)](https://github.com/nicbarker/clay) is a high performance 2D UI layout library written in C by Nic Baker, meanwhile this is a partial reimplementation of Clay but written in Java.

## Basic usage

This is still work in progress but you _can try_ to use it.
```Java
LayoutContext context = new LayoutContext();

layout.reset();
Element.Builder builder = layout.newRoot();
builder.layout().childGap(4).layoutDirection(LayoutDirection.TOP_TO_BOTTOM).padding(NO_PADDING);
try (Element root = builder.build()) {

    builder = root.newElement();
    builder.layout().width(ISizing.grow()).height(ISizing.fit(32f, 48f)).padding(NO_PADDING);
    try (Element titleBar = builder.build()) {

        builder = titleBar.newElement();
        builder.layout().height(ISizing.grow()).width(ISizing.grow());
        try (Element left = builder.build()) {

        }

        builder = titleBar.newElement();
        builder.layout().height(ISizing.grow()).width(ISizing.grow());
        try (Element right = builder.build()) {
            // Create a spacer
            builder = right.newElement();
            builder.layout().width(ISizing.grow());
            builder.build().close();
        }
    }
    
    builder = root.newElement();
    builder.layout().height(ISizing.grow()).width(ISizing.grow()).padding(NO_PADDING);
    try (Element panorama = builder.build()) {
        // Create a spacer
        builder = panorama.newElement();
        builder.layout().height(ISizing.grow()).width(ISizing.grow());
        builder.build().close();

        builder = panorama.newElement();
        builder.layout().height(ISizing.fit(240f, 320f)).width(ISizing.grow());
        try (Element controlBar = builder.build()) {

        }

        // Create a spacer
        builder = panorama.newElement();
        builder.layout().height(ISizing.grow(0f, 120f)).width(ISizing.grow());
        builder.build().close();
    }
}
layout.finalize();
```

This is as far as you can get with the library until now.
There is no way to retrieve the information about the layout yet except if you keep yourself a reference to each Element you care about.

It is not yet able to resize/wrap text and it doesn't calculate the positions of elements yet.

## Goal

The reason for this reimplementation is simple, I wanted an actual layouting library in java which I can use in my projects and I was so fascinated by Clay so I decided to reimplement it in Java.
Don't except this port to be amazing in very possible way or feel super similar to the original Clay, it's Java and it's okay to use I think.
