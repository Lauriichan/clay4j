package me.lauriichan.clay4j.util;

public final class DebugPrinter {

    private final StringBuilder builder = new StringBuilder();
    private volatile boolean first = true;

    public DebugPrinter() {
        String name = Thread.currentThread().getStackTrace()[2].getClassName();
        String[] parts = name.split(name.contains("$") ? "$" : "\\.");
        builder.append(parts[parts.length - 1]).append('[');
    }

    public void append(String name, Object value) {
        if (first) {
            first = false;
        } else {
            builder.append(", ");
        }
        if (value == null) {
            value = "null";
        }
        builder.append(name).append('=').append(value);
    }

    @Override
    public String toString() {
        return builder.toString() + ']';
    }

}
