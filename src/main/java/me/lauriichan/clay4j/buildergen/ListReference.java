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
public @interface ListReference {
    
    public @interface Unmodifiable {
        
        Class<?> type() default Object.class;
        
        String method() default "";
        
    }
    
    String add() default "add";
    
    String remove() default "remove";
    
    String contains() default "contains";
    
    String clear() default "clear";
    
    Unmodifiable unmodifiable() default @Unmodifiable;
    
    boolean unique() default true;
    
}
