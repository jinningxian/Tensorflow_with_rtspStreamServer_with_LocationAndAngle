package net.majorkernelpanic.streaming.video;

public class Point {
    public double x;
    public double y;

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    double angle;
    public Point(double x, double y, double angle) {
        //angle %=360;
        this.x = x;
        this.y = y;
        this.angle = angle;
    }
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
        this.angle=0.0;
    }


}
