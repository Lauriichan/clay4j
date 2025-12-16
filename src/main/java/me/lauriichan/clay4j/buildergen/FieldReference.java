package me.lauriichan.clay4j.buildergen;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(SOURCE)
@Target(PARAMETER)
public @interface FieldReference {
    
    String value() default "";
    
}
