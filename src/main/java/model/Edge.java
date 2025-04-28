package model;

import java.io.Serializable;

public class Edge implements Serializable {
    String from;
    String to;
    double length;
    int weight;

    public Edge(String from, String to, double length) {
        this.from = from;
        this.to = to;
        this.length = length;
        this.weight = 0; // Инициализируется позже
    }
}