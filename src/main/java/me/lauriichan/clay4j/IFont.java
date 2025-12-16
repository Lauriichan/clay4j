package me.lauriichan.clay4j;

public interface IFont {
    
    int id();

    void calculateSize(String text, int fontSize, float[] size);

}
