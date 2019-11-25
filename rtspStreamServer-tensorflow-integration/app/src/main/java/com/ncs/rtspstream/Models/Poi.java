package com.ncs.rtspstream.Models;

public class Poi {
    private String name;
    private int level;
    private int x_pos;
    private int y_pos;

    public Poi(){

    }

    public Poi(String name, int level, int x_pos, int y_pos)
    {
        this.name = name;
        this.level = level;
        this.x_pos = x_pos;
        this.y_pos = y_pos;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getX_pos() {
        return x_pos;
    }

    public void setX_pos(int x_pos) {
        this.x_pos = x_pos;
    }

    public int getY_pos() {
        return y_pos;
    }

    public void setY_pos(int y_pos) {
        this.y_pos = y_pos;
    }
}