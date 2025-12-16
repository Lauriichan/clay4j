package me.lauriichan.clay4j.buildergen;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(SOURCE)
@Target(TYPE)
public @interface GenerateBuilder {

    String name() default "builder";

    boolean internal() default false;

    // If this is not defined it will not be generated
    // Also this requires internal() to be true
    String rootName() default "";

}
