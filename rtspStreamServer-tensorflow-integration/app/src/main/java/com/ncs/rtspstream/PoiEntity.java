package com.ncs.rtspstream;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

/**
 * This class defines the database for the directory list
 */

@Entity(tableName = "poi_table")
public class PoiEntity {

    @NonNull
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    private String placeName;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "multimedia")
    private String multimedia;

    @ColumnInfo(name = "marker_icon")
    private int markerIcon;

    @ColumnInfo(name = "level")
    private int level;

    @ColumnInfo(name = "x_pos")
    private int x;

    @ColumnInfo(name = "y_pos")
    private int y;

    @ColumnInfo(name = "tts")
    private String tts;

    @ColumnInfo(name = "guide_id")
    private int guideId;

    public int getId() {
        return id;
    }

    public int getGuideId() {
        return guideId;
    }

    public void setGuideId(int guideId) {
        this.guideId = guideId;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMultimedia() {
        return multimedia;
    }

    public void setMultimedia(String multimedia) {
        this.multimedia = multimedia;
    }

    public int getMarkerIcon() {
        return markerIcon;
    }

    public void setMarkerIcon(int markerIcon) {
        this.markerIcon = markerIcon;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public String getTts() {
        return tts;
    }

    public void setTts(String tts) {
        this.tts = tts;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}