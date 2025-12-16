package me.lauriichan.clay4j.buildergen;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(SOURCE)
@Target({
    PARAMETER,
    RECORD_COMPONENT
})
public @interface BuilderReference {
    
    String value() default "builder";
    
}
