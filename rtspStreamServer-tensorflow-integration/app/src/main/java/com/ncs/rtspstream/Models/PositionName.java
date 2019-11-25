package com.ncs.rtspstream.Models;

public class PositionName {
    private String appPosName;
    private double posX;
    private double posY;
    private double heading;

    public PositionName() {

    }

    public PositionName(String appPosName, double posX, double posY, int heading) {
        this.appPosName = appPosName;
        this.posX = posX;
        this.posY = posY;
        this.heading = heading;
    }

    public String getAppPosName() {
        return appPosName;
    }

    public void setAppPosName(String appPosName) {
        this.appPosName = appPosName;
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public double getHeading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }
}
