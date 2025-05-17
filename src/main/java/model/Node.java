package model;

import java.awt.Point;
import java.awt.Polygon;
import java.io.Serializable;

public class Node implements Serializable {
    String id;
    Point position;
    Polygon shape;
    Point connectionPoint;

    public Node(String id, Point position, Polygon shape, Point connectionPoint) {
        this.id = id;
        this.position = position;
        this.shape = shape;
        this.connectionPoint = connectionPoint;
    }

    public boolean isBuilding() {
        return shape != null;
    }

    public String getId() {
        return id;
    }

    public Point getPosition() {
        return position;
    }

    public Polygon getShape() {
        return shape;
    }

    public Point getConnectionPoint() {
        return connectionPoint;
    }
}