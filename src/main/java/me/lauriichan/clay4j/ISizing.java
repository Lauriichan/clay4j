package me.lauriichan.clay4j;

import java.util.Objects;

public sealed interface ISizing {

    public static enum Type {

        FIT,
        FIXED,
        GROW,
        PERCENTAGE;

    }

    public static ISizing percentage(float percentage) {
        return new Percentage(percentage);
    }

    public static ISizing fixed(float value) {
        return new MinMax(MinMax.Behaviour.FIXED, value, value);
    }

    public static ISizing grow() {
        return grow(0, Float.MAX_VALUE);
    }

    public static ISizing grow(float value) {
        return grow(value, value);
    }

    public static ISizing grow(float min, float max) {
        return new MinMax(MinMax.Behaviour.GROW, min, max);
    }

    public static ISizing fit() {
        return fit(0, Float.MAX_VALUE);
    }

    public static ISizing fit(float value) {
        return fit(value, value);
    }

    public static ISizing fit(float min, float max) {
        return new MinMax(MinMax.Behaviour.FIT, min, max);
    }

    public static record MinMax(Behaviour behaviour, float min, float max) implements ISizing {

        public static enum Behaviour {

            FIXED,
            FIT,
            GROW;

        }

        public MinMax(Behaviour behaviour, float min, float max) {
            this.behaviour = Objects.requireNonNull(behaviour);
            this.min = Math.max(min, 0);
            this.max = Math.max(max, 0);
        }

        public Type type() {
            switch (behaviour) {
            default:
            case FIT:
                return Type.FIT;
            case FIXED:
                return Type.FIXED;
            case GROW:
                return Type.GROW;
            }
        }

    }

    public static record Percentage(float percentage) implements ISizing {

        public Percentage(float percentage) {
            this.percentage = Math.clamp(percentage, 0f, 1f);
        }

        public Type type() {
            return Type.PERCENTAGE;
        }

    }

    Type type();

    default MinMax minMax() {
        return (MinMax) this;
    }

    default float percentage() {
        return ((Percentage) this).percentage();
    }

}
