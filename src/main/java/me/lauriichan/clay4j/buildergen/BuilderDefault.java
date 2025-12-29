package me.lauriichan.clay4j.buildergen;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(SOURCE)
@Target({
    FIELD,
    METHOD
})
public @interface BuilderDefault {
    
    /**
     * Parameter name
     */
    String[] value();

}
