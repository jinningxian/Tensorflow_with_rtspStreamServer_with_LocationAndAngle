package com.ncs.rtspstream.Models;

public class DirectoryModel {

    public String text;
    public String category;
    public String level;
    public int drawable;
    public String color;

    public DirectoryModel(String t, String category, String level, int d, String c)
    {
        text = t;
        this.category = category;
        this.level = level;
        drawable = d;
        color=c;
    }

}
