package me.lauriichan.clay4j.buildergen;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Repeatable(BuilderSetter.BuilderSetters.class)
@Retention(SOURCE)
@Target(TYPE)
public @interface BuilderSetter {

    /**
     * Name of setter
     */
    String name();
    
    /**
     * Single param value
     */
    boolean singleValue() default true;

    /**
     * Parameter names
     */
    String[] values();

    @Retention(SOURCE)
    @Target(TYPE)
    public static @interface BuilderSetters {

        BuilderSetter[] value();

    }

}
