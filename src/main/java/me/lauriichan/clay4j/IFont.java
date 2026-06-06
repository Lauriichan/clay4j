package me.lauriichan.clay4j;

public interface IFont {
    
    int id();

    void calculateSize(String text, float fontSize, float[] size);

}
