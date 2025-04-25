import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

class RoundBorder implements Border {
    private int radius;
    private Color color;

    public RoundBorder() {
        this.radius = 10;
        this.color = new Color(200, 200, 200); // Серая рамка
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}

class Node implements Serializable {
    String id;
    Point position;
    Polygon shape; // null для точек преломления
    Point connectionPoint; // null для точек преломления

    Node(String id, Point position, Polygon shape, Point connectionPoint) {
        this.id = id;
        this.position = position;
        this.shape = shape;
        this.connectionPoint = connectionPoint;
    }

    boolean isBuilding() {
        return shape != null;
    }
}

class Edge implements Serializable {
    String from;
    String to;
    double length;
    int weight;

    Edge(String from, String to, double length) {
        this.from = from;
        this.to = to;
        this.length = length;
        this.weight = 0; // Инициализируется позже
    }
}

class CampusMap implements Serializable {
    private Map<String, Node> nodes;
    private Map<String, Map<String, Edge>> graph;
    private List<Edge> edges;

    public CampusMap() {
        nodes = new HashMap<>();
        graph = new HashMap<>();
        edges = new ArrayList<>();
    }

    public void addBuilding(String name, Polygon shape, Point connectionPoint) {
        String id = "B_" + name;
        nodes.put(id, new Node(id, connectionPoint, shape, connectionPoint));
        graph.putIfAbsent(id, new HashMap<>());
    }

    public String addJunction(Point position) {
        String id = "J_" + UUID.randomUUID().toString();
        nodes.put(id, new Node(id, position, null, null));
        graph.putIfAbsent(id, new HashMap<>());
        return id;
    }

    public void addRoad(String fromId, String toId) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId) || fromId.equals(toId)) return;
        Point fromPos = nodes.get(fromId).position;
        Point toPos = nodes.get(toId).position;
        double length = Math.sqrt(Math.pow(toPos.x - fromPos.x, 2) + Math.pow(toPos.y - fromPos.y, 2));

        Edge edge = new Edge(fromId, toId, length);
        graph.get(fromId).put(toId, edge);
        graph.get(toId).put(fromId, edge);
        edges.add(edge);

        updateEdgeWeights();
    }

    public void deleteNode(String nodeId) {
        if (!nodes.containsKey(nodeId)) return;

        List<Edge> edgesToRemove = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.from.equals(nodeId) || edge.to.equals(nodeId)) {
                edgesToRemove.add(edge);
            }
        }
        edges.removeAll(edgesToRemove);

        graph.remove(nodeId);
        for (Map<String, Edge> neighbors : graph.values()) {
            neighbors.remove(nodeId);
        }

        nodes.remove(nodeId);

        updateEdgeWeights();
    }

    public void deleteEdge(Edge edge) {
        if (edge == null) return;

        graph.get(edge.from).remove(edge.to);
        graph.get(edge.to).remove(edge.from);

        edges.remove(edge);

        updateEdgeWeights();
    }

    private void updateEdgeWeights() {
        edges.sort(Comparator.comparingDouble(e -> e.length));

        int n = edges.size();
        for (int i = 0; i < n; i++) {
            edges.get(i).weight = i;
        }
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public Set<String> getBuildings() {
        Set<String> buildings = new HashSet<>();
        for (Node node : nodes.values()) {
            if (node.isBuilding()) {
                buildings.add(node.id);
            }
        }
        return buildings;
    }

    public Map<String, Map<String, Edge>> getGraph() {
        return graph;
    }

    public List<String> findShortestPath(String start, String end) {
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<String> queue = new PriorityQueue<>(Comparator.comparingInt(distances::get));
        Set<String> visited = new HashSet<>();

        for (String nodeId : graph.keySet()) {
            distances.put(nodeId, Integer.MAX_VALUE);
        }
        distances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(end)) break;

            for (Map.Entry<String, Edge> neighbor : graph.get(current).entrySet()) {
                String next = neighbor.getKey();
                int weight = neighbor.getValue().weight;
                int newDist = distances.get(current) + weight;

                if (newDist < distances.get(next)) {
                    distances.put(next, newDist);
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }

        List<String> path = new ArrayList<>();
        String current = end;
        while (current != null) {
            path.add(current);
            current = previous.get(current);
        }
        Collections.reverse(path);
        return path.isEmpty() || !path.get(0).equals(start) ? Collections.emptyList() : path;
    }
}

class MapPanel extends JPanel {
    private CampusMap map;
    private List<String> currentPath;
    private boolean drawingBuilding;
    private boolean editingBuilding;
    private boolean selectingConnectionPoint;
    private boolean drawingRoad;
    private String currentBuildingName;
    private Polygon tempPolygon;
    private Point startPoint;
    private List<Point> tempRoadPoints;
    private String roadStartNode;
    private int draggingVertexIndex = -1;
    private Object selectedObject;
    private boolean isEditMode;
    private double scale = 1.0;
    private double translateX = 0;
    private double translateY = 0;

    public MapPanel(CampusMap map) {
        this.map = map;
        this.currentPath = new ArrayList<>();
        this.isEditMode = true;
        resetModes();
        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(245, 245, 245)); // Светлый фон

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingBuilding) {
                    startPoint = scaledPoint;
                } else if (editingBuilding) {
                    draggingVertexIndex = findVertexAt(scaledPoint.x, scaledPoint.y);
                } else if (drawingRoad && roadStartNode == null) {
                    roadStartNode = findNodeAt(scaledPoint.x, scaledPoint.y);
                    if (roadStartNode != null) {
                        tempRoadPoints.add(map.getNodes().get(roadStartNode).position);
                        repaint();
                    } else {
                        JOptionPane.showMessageDialog(null, "Click on a building entry point or junction!");
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingBuilding && startPoint != null) {
                    int x1 = Math.min(startPoint.x, scaledPoint.x);
                    int y1 = Math.min(startPoint.y, scaledPoint.y);
                    int x2 = Math.max(startPoint.x, scaledPoint.x);
                    int y2 = Math.max(startPoint.y, scaledPoint.y);
                    tempPolygon = new Polygon();
                    tempPolygon.addPoint(x1, y1);
                    tempPolygon.addPoint(x2, y1);
                    tempPolygon.addPoint(x2, y2);
                    tempPolygon.addPoint(x1, y2);
                    drawingBuilding = false;
                    editingBuilding = true;
                    startPoint = null;
                    repaint();
                }
                draggingVertexIndex = -1;
            }

            @Override
            public void mouseDragged(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingBuilding && startPoint != null) {
                    repaint();
                } else if (editingBuilding && draggingVertexIndex >= 0) {
                    tempPolygon.xpoints[draggingVertexIndex] = scaledPoint.x;
                    tempPolygon.ypoints[draggingVertexIndex] = scaledPoint.y;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingRoad && roadStartNode != null) {
                    if (evt.getClickCount() == 1) {
                        String nodeId = findNodeAt(scaledPoint.x, scaledPoint.y);
                        if (nodeId != null && !nodeId.equals(roadStartNode)) {
                            tempRoadPoints.add(map.getNodes().get(nodeId).position);
                            map.addRoad(roadStartNode, nodeId);
                            resetModes();
                            updateBuildingCombos();
                            repaint();
                        } else if (!isPointOnOrInsideBuilding(scaledPoint)) {
                            String junctionId = map.addJunction(scaledPoint);
                            tempRoadPoints.add(scaledPoint);
                            map.addRoad(roadStartNode, junctionId);
                            roadStartNode = junctionId;
                            repaint();
                        } else {
                            JOptionPane.showMessageDialog(null, "Cannot place road point on or inside a building!");
                        }
                    } else if (evt.getClickCount() == 2) {
                        String endNode = findNodeAt(scaledPoint.x, scaledPoint.y);
                        if (endNode == null) {
                            if (!isPointOnOrInsideBuilding(scaledPoint)) {
                                endNode = map.addJunction(scaledPoint);
                                tempRoadPoints.add(scaledPoint);
                            } else {
                                JOptionPane.showMessageDialog(null, "Cannot end road on or inside a building!");
                                return;
                            }
                        }
                        if (!endNode.equals(roadStartNode)) {
                            map.addRoad(roadStartNode, endNode);
                            resetModes();
                            updateBuildingCombos();
                            repaint();
                        } else {
                            JOptionPane.showMessageDialog(null, "Select a different point to end the road!");
                        }
                    }
                } else if (selectingConnectionPoint) {
                    if (isPointOnPolygonContour(tempPolygon, scaledPoint)) {
                        map.addBuilding(currentBuildingName, tempPolygon, scaledPoint);
                        resetModes();
                        updateBuildingCombos();
                        repaint();
                    } else {
                        JOptionPane.showMessageDialog(null, "Please click on the building contour!");
                    }
                } else if (editingBuilding && evt.getClickCount() == 2) {
                    int segmentIndex = findSegmentAt(scaledPoint);
                    if (segmentIndex >= 0) {
                        Polygon newPolygon = new Polygon();
                        for (int i = 0; i <= segmentIndex; i++) {
                            newPolygon.addPoint(tempPolygon.xpoints[i], tempPolygon.ypoints[i]);
                        }
                        newPolygon.addPoint(scaledPoint.x, scaledPoint.y);
                        for (int i = segmentIndex + 1; i < tempPolygon.npoints; i++) {
                            newPolygon.addPoint(tempPolygon.xpoints[i], tempPolygon.ypoints[i]);
                        }
                        tempPolygon = newPolygon;
                        repaint();
                    }
                } else if (!drawingBuilding && !editingBuilding && !selectingConnectionPoint && !drawingRoad) {
                    resetModes();
                    setCurrentPath(new ArrayList<>());
                    Node selectedBuilding = findBuildingAt(scaledPoint);
                    if (selectedBuilding != null) {
                        selectedObject = selectedBuilding;
                    } else {
                        String nodeId = findNodeAt(scaledPoint.x, scaledPoint.y);
                        if (nodeId != null) {
                            selectedObject = map.getNodes().get(nodeId);
                        } else {
                            selectedObject = findEdgeAt(scaledPoint);
                        }
                    }
                    UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(MapPanel.this);
                    if (frame != null) {
                        frame.deleteButton.setEnabled(selectedObject != null);
                    }
                    repaint();
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        addMouseWheelListener(evt -> {
            Point mousePoint = evt.getPoint();
            double mouseX = mousePoint.x;
            double mouseY = mousePoint.y;

            double oldScale = scale;

            int notches = evt.getWheelRotation();
            if (notches < 0) {
                scale = Math.min(scale + 0.1, 2.0);
            } else {
                scale = Math.max(scale - 0.1, 0.5);
            }

            double scaleFactor = scale / oldScale;

            translateX = mouseX - scaleFactor * (mouseX - translateX);
            translateY = mouseY - scaleFactor * (mouseY - translateY);

            UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
            if (frame != null) {
                frame.updateResetZoomButtonVisibility(scale != 1.0);
            }

            repaint();
        });
    }

    private Point getScaledPoint(Point p) {
        double x = (p.x - translateX) / scale;
        double y = (p.y - translateY) / scale;
        return new Point((int) x, (int) y);
    }

    private void resetModes() {
        drawingBuilding = false;
        editingBuilding = false;
        selectingConnectionPoint = false;
        drawingRoad = false;
        tempPolygon = new Polygon();
        tempRoadPoints = new ArrayList<>();
        roadStartNode = null;
        startPoint = null;
        currentBuildingName = null;
        clearSelection();
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.addBuildingButton.setBackground(new Color(255, 255, 255));
            frame.addRoadButton.setBackground(new Color(255, 255, 255));
        }
        repaint();
    }

    private Node findBuildingAt(Point p) {
        for (Node node : map.getNodes().values()) {
            if (node.isBuilding()) {
                if (node.shape.contains(p) || isPointOnPolygonContour(node.shape, p)) {
                    return node;
                }
            }
        }
        return null;
    }

    private String findNodeAt(int x, int y) {
        for (Node node : map.getNodes().values()) {
            Point pos = node.position;
            if (Math.sqrt(Math.pow(pos.x - x, 2) + Math.pow(pos.y - y, 2)) < 15 / scale) {
                return node.id;
            }
        }
        return null;
    }

    private Edge findEdgeAt(Point p) {
        for (String from : map.getGraph().keySet()) {
            for (Map.Entry<String, Edge> neighbor : map.getGraph().get(from).entrySet()) {
                String to = neighbor.getKey();
                Point p1 = map.getNodes().get(from).position;
                Point p2 = map.getNodes().get(to).position;
                if (distanceToSegment(p, p1, p2) < 5 / scale) {
                    return neighbor.getValue();
                }
            }
        }
        return null;
    }

    private int findVertexAt(int x, int y) {
        for (int i = 0; i < tempPolygon.npoints; i++) {
            if (Math.abs(tempPolygon.xpoints[i] - x) < 5 / scale && Math.abs(tempPolygon.ypoints[i] - y) < 5 / scale) {
                return i;
            }
        }
        return -1;
    }

    private int findSegmentAt(Point p) {
        for (int i = 0; i < tempPolygon.npoints; i++) {
            Point p1 = new Point(tempPolygon.xpoints[i], tempPolygon.ypoints[i]);
            Point p2 = new Point(tempPolygon.xpoints[(i + 1) % tempPolygon.npoints], tempPolygon.ypoints[(i + 1) % tempPolygon.npoints]);
            if (distanceToSegment(p, p1, p2) < 5 / scale) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPointOnPolygonContour(Polygon polygon, Point p) {
        for (int i = 0; i < polygon.npoints; i++) {
            Point p1 = new Point(polygon.xpoints[i], polygon.ypoints[i]);
            Point p2 = new Point(polygon.xpoints[(i + 1) % polygon.npoints], polygon.ypoints[(i + 1) % polygon.npoints]);
            if (distanceToSegment(p, p1, p2) < 5 / scale) {
                return true;
            }
        }
        return false;
    }

    private boolean isPointOnOrInsideBuilding(Point p) {
        for (Node node : map.getNodes().values()) {
            if (node.isBuilding()) {
                if (node.connectionPoint != null &&
                        Math.abs(node.connectionPoint.x - p.x) < 5 / scale &&
                        Math.abs(node.connectionPoint.y - p.y) < 5 / scale) {
                    return false;
                }
                if (node.shape.contains(p) || isPointOnPolygonContour(node.shape, p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceToSegment(Point p, Point p1, Point p2) {
        double x = p.x, y = p.y;
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;

        double l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return Math.sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1));

        double t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2));
        double projectionX = x1 + t * (x2 - x1);
        double projectionY = y1 + t * (y2 - y1);

        return Math.sqrt((x - projectionX) * (x - projectionX) + (y - projectionY) * (y - projectionY));
    }

    public void startDrawingBuilding(String name) {
        resetModes();
        drawingBuilding = true;
        currentBuildingName = name;
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.addBuildingButton.setBackground(new Color(230, 230, 230));
        }
        repaint();
    }

    public void finishDrawingBuilding() {
        if (editingBuilding) {
            selectingConnectionPoint = true;
            editingBuilding = false;
            repaint();
        } else {
            JOptionPane.showMessageDialog(null, "Complete the rectangle first!");
        }
    }

    public boolean isSelectingConnectionPoint() {
        return selectingConnectionPoint;
    }

    public void startDrawingRoad() {
        resetModes();
        drawingRoad = true;
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.addRoadButton.setBackground(new Color(230, 230, 230));
        }
        repaint();
    }

    public void setCurrentPath(List<String> path) {
        currentPath = path;
        repaint();
    }

    public void clearSelection() {
        selectedObject = null;
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.deleteButton.setEnabled(false);
        }
        repaint();
    }

    public Object getSelectedObject() {
        return selectedObject;
    }

    public void setEditMode(boolean editMode) {
        isEditMode = editMode;
        if (!isEditMode) {
            resetModes();
            clearSelection();
        }
        repaint();
    }

    private void updateBuildingCombos() {
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.updateBuildingCombos();
        }
    }

    public void resetZoom() {
        scale = 1.0;
        translateX = 0;
        translateY = 0;
        UniversityCampusNavigation frame = (UniversityCampusNavigation) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.updateResetZoomButtonVisibility(false);
        }
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.translate(translateX, translateY);
        g2d.scale(scale, scale);

        // Отрисовка дорог
        System.out.println("Drawing roads with color: 200, 200, 200"); // Отладка
        g2d.setColor(new Color(200, 200, 200)); // Серый, как на фото
        Set<String> drawnEdges = new HashSet<>();
        for (String from : map.getGraph().keySet()) {
            for (Map.Entry<String, Edge> neighbor : map.getGraph().get(from).entrySet()) {
                String to = neighbor.getKey();
                String edgeKey = from.compareTo(to) < 0 ? from + "-" + to : to + "-" + from;
                if (!drawnEdges.contains(edgeKey)) {
                    Point fromPos = map.getNodes().get(from).position;
                    Point toPos = map.getNodes().get(to).position;
                    if (isEditMode && selectedObject instanceof Edge && selectedObject == neighbor.getValue()) {
                        System.out.println("Highlighting edge: " + from + " to " + to); // Отладка
                        g2d.setColor(new Color(255, 165, 0)); // Оранжевый для выделения
                        g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    } else {
                        g2d.setColor(new Color(200, 200, 200));
                        g2d.setStroke(new BasicStroke((float) (2 / scale))); // Толщина как на фото
                    }
                    g2d.drawLine(fromPos.x, fromPos.y, toPos.x, toPos.y);
                    drawnEdges.add(edgeKey);
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
            }
        }

        // Отрисовка временной дороги
        if (isEditMode && drawingRoad && !tempRoadPoints.isEmpty()) {
            System.out.println("Drawing temporary road"); // Отладка
            g2d.setColor(new Color(200, 200, 200)); // Светло-серый
            g2d.setStroke(new BasicStroke((float) (2 / scale)));
            Path2D road = new Path2D.Double();
            road.moveTo(tempRoadPoints.get(0).x, tempRoadPoints.get(0).y);
            for (int i = 1; i < tempRoadPoints.size(); i++) {
                road.lineTo(tempRoadPoints.get(i).x, tempRoadPoints.get(i).y);
            }
            g2d.draw(road);
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
        }

        // Отрисовка пути
        if (!currentPath.isEmpty()) {
            System.out.println("Drawing path with color: 66, 133, 244"); // Отладка
            g2d.setColor(new Color(66, 133, 244)); // Синий, как в Google Maps
            g2d.setStroke(new BasicStroke((float) (3 / scale)));
            for (int i = 0; i < currentPath.size() - 1; i++) {
                String from = currentPath.get(i);
                String to = currentPath.get(i + 1);
                Point fromPos = map.getNodes().get(from).position;
                Point toPos = map.getNodes().get(to).position;
                g2d.drawLine(fromPos.x, fromPos.y, toPos.x, toPos.y);
            }
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
        }

        // Отрисовка зданий и точек
        for (Node node : map.getNodes().values()) {
            if (node.isBuilding()) {
                // Здание
                g2d.setColor(new Color(100, 149, 237, 180)); // Полупрозрачный синий
                g2d.fillPolygon(node.shape);
                g2d.setColor(new Color(25, 25, 112)); // Тёмно-синий контур
                g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2d.drawPolygon(node.shape);
                if (isEditMode && selectedObject == node) {
                    System.out.println("Highlighting building: " + node.id); // Отладка
                    g2d.setColor(new Color(255, 165, 0)); // Оранжевый для выделения
                    g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    g2d.drawPolygon(node.shape);
                    g2d.drawOval(node.connectionPoint.x - (int) (7 / scale), node.connectionPoint.y - (int) (7 / scale), (int) (14 / scale), (int) (14 / scale));
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
                // Точка входа (Google Maps стиль)
                System.out.println("Drawing entry point at: " + node.connectionPoint.x + ", " + node.connectionPoint.y); // Отладка
                g2d.setColor(new Color(34, 139, 34)); // Зелёный круг
                g2d.fillOval(node.connectionPoint.x - (int) (5 / scale), node.connectionPoint.y - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                g2d.setColor(Color.WHITE); // Белая точка
                g2d.fillOval(node.connectionPoint.x - (int) (2 / scale), node.connectionPoint.y - (int) (2 / scale), (int) (4 / scale), (int) (4 / scale));
                // Название здания (только чёрный шрифт)
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.PLAIN, (int) (12 / scale)));
                Rectangle bounds = node.shape.getBounds();
                g2d.drawString(node.id.substring(2), bounds.x + bounds.width + 5, bounds.y + bounds.height / 2);
            } else {
                // Junction
                g2d.setColor(new Color(220, 20, 60)); // Красный
                g2d.fillOval(node.position.x - (int) (5 / scale), node.position.y - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                if (isEditMode && selectedObject == node) {
                    System.out.println("Highlighting junction: " + node.id); // Отладка
                    g2d.setColor(new Color(255, 165, 0)); // Оранжевый для выделения
                    g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    g2d.drawOval(node.position.x - (int) (7 / scale), node.position.y - (int) (7 / scale), (int) (14 / scale), (int) (14 / scale));
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
            }
        }

        // Отрисовка временного здания
        if (isEditMode && drawingBuilding && startPoint != null) {
            System.out.println("Drawing temporary building"); // Отладка
            g2d.setColor(new Color(100, 149, 237, 180));
            Point mousePos = getScaledPoint(getMousePosition() != null ? getMousePosition() : startPoint);
            int x = Math.min(startPoint.x, mousePos.x);
            int y = Math.min(startPoint.y, mousePos.y);
            int w = Math.abs(startPoint.x - mousePos.x);
            int h = Math.abs(startPoint.y - mousePos.y);
            g2d.fillRect(x, y, w, h);
            g2d.setColor(new Color(25, 25, 112));
            g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
            g2d.drawRect(x, y, w, h);
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
        }

        // Отрисовка редактируемого здания
        if (isEditMode && (editingBuilding || selectingConnectionPoint) && tempPolygon.npoints > 0) {
            System.out.println("Drawing editable building"); // Отладка
            g2d.setColor(new Color(100, 149, 237, 180));
            g2d.fillPolygon(tempPolygon);
            g2d.setColor(new Color(25, 25, 112));
            g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
            g2d.drawPolygon(tempPolygon);
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
            if (editingBuilding) {
                g2d.setColor(new Color(220, 20, 60));
                for (int i = 0; i < tempPolygon.npoints; i++) {
                    g2d.fillOval(tempPolygon.xpoints[i] - (int) (5 / scale), tempPolygon.ypoints[i] - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                }
            }
        }
    }
}

class UniversityCampusNavigation extends JFrame {
    private CampusMap currentMap;
    private File currentFile;
    private String mapName;
    private MapPanel mapPanel;
    private JPanel controlPanel;
    private JPanel modePanel;
    private JPanel titlePanel;
    private JLabel mapNameLabel;
    public JComboBox<String> startCombo;
    public JComboBox<String> endCombo;
    public JButton deleteButton;
    public JButton addBuildingButton;
    public JButton addRoadButton;
    private JButton editModeButton;
    private JButton navigationModeButton;
    private JButton finishBuildingButton;
    private JButton clearMapButton;
    private JButton saveButton;
    private JButton openButton;
    private JComboBox<String> mapCombo;
    private JLabel startLabel;
    private JLabel endLabel;
    private JButton findPathButton;
    private JLayeredPane layeredPane;
    private JButton resetZoomButton;
    private final boolean navigationOnly;
    private final File mapDirectory;

    public UniversityCampusNavigation(boolean navigationOnly) {
        this.navigationOnly = navigationOnly;
        currentMap = new CampusMap();
        currentFile = null;
        mapName = "Untitled";
        setTitle("University Campus Navigation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1000, 700));

        mapDirectory = loadMapDirectory();

        // Панель для названия карты
        titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setBackground(new Color(230, 230, 250));
        mapNameLabel = new JLabel(mapName);
        mapNameLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mapNameLabel.setForeground(new Color(25, 25, 112));
        titlePanel.add(mapNameLabel);

        layeredPane = new JLayeredPane();
        add(layeredPane, BorderLayout.CENTER);

        mapPanel = new MapPanel(currentMap);
        mapPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(mapPanel, JLayeredPane.DEFAULT_LAYER);

        resetZoomButton = new JButton("1:1");
        resetZoomButton.setFont(new Font("Arial", Font.PLAIN, 10));
        resetZoomButton.setPreferredSize(new Dimension(30, 30));
        resetZoomButton.setSize(30, 30);
        resetZoomButton.setVisible(false);
        styleButton(resetZoomButton, false);
        resetZoomButton.addActionListener(event -> {
            System.out.println("Reset Zoom button clicked");
            mapPanel.resetZoom();
        });
        layeredPane.add(resetZoomButton, JLayeredPane.PALETTE_LAYER);
        updateResetZoomButtonPosition();

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent evt) {
                mapPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                updateResetZoomButtonPosition();
            }
        });

        if (!navigationOnly) {
            modePanel = new JPanel(new FlowLayout());
            modePanel.setBackground(new Color(230, 230, 250));
            editModeButton = new JButton("Edit Mode");
            navigationModeButton = new JButton("Navigation Mode");
            styleButton(editModeButton, true);
            styleButton(navigationModeButton, true);
            modePanel.add(editModeButton);
            modePanel.add(navigationModeButton);

            // Панель для размещения modePanel и titlePanel
            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.setBackground(new Color(230, 230, 250));
            northPanel.add(modePanel, BorderLayout.NORTH);
            northPanel.add(titlePanel, BorderLayout.CENTER);
            add(northPanel, BorderLayout.NORTH);
        } else {
            add(titlePanel, BorderLayout.NORTH);
        }

        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        controlPanel.setBackground(new Color(245, 245, 245));
        add(controlPanel, BorderLayout.WEST);

        addBuildingButton = new JButton("Add Building");
        addBuildingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addBuildingButton.setMaximumSize(new Dimension(130, 30));
        styleButton(addBuildingButton, false);
        addBuildingButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter building name:");
            if (name != null && !name.trim().isEmpty()) {
                mapPanel.startDrawingBuilding(name);
                JOptionPane.showMessageDialog(this, "Click and drag to draw a rectangle. Then edit vertices or double-click to add points. Press 'Finish Drawing' when done.");
            }
        });

        finishBuildingButton = new JButton("Finish Drawing");
        finishBuildingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        finishBuildingButton.setMaximumSize(new Dimension(130, 30));
        styleButton(finishBuildingButton, false);
        finishBuildingButton.addActionListener(e -> {
            mapPanel.finishDrawingBuilding();
            if (mapPanel.isSelectingConnectionPoint()) {
                JOptionPane.showMessageDialog(this, "Click on the building contour to set the entry point.");
            }
            updateBuildingCombos();
        });

        addRoadButton = new JButton("Add Road");
        addRoadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addRoadButton.setMaximumSize(new Dimension(130, 30));
        styleButton(addRoadButton, false);
        addRoadButton.addActionListener(e -> {
            mapPanel.startDrawingRoad();
            JOptionPane.showMessageDialog(this, "Click on a building entry point or junction to start. Click to add points, double-click to finish.");
        });

        clearMapButton = new JButton("Clear Map");
        clearMapButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        clearMapButton.setMaximumSize(new Dimension(130, 30));
        styleButton(clearMapButton, false);
        clearMapButton.addActionListener(e -> {
            currentMap = new CampusMap();
            currentFile = null;
            mapName = "Untitled";
            mapNameLabel.setText(mapName);
            getContentPane().removeAll();
            layeredPane = new JLayeredPane();
            mapPanel = new MapPanel(currentMap);
            mapPanel.setBounds(0, 0, 800, 600);
            layeredPane.add(mapPanel, JLayeredPane.DEFAULT_LAYER);
            resetZoomButton = new JButton("1:1");
            resetZoomButton.setFont(new Font("Arial", Font.PLAIN, 10));
            resetZoomButton.setPreferredSize(new Dimension(30, 30));
            resetZoomButton.setSize(30, 30);
            resetZoomButton.setVisible(false);
            styleButton(resetZoomButton, false);
            resetZoomButton.addActionListener(event -> {
                System.out.println("Reset Zoom button clicked");
                mapPanel.resetZoom();
            });
            layeredPane.add(resetZoomButton, JLayeredPane.PALETTE_LAYER);
            layeredPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent evt) {
                    mapPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                    updateResetZoomButtonPosition();
                }
            });
            updateResetZoomButtonPosition();
            controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
            controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            controlPanel.setBackground(new Color(245, 245, 245));
            if (!navigationOnly) {
                modePanel = new JPanel(new FlowLayout());
                modePanel.setBackground(new Color(230, 230, 250));
                editModeButton = new JButton("Edit Mode");
                navigationModeButton = new JButton("Navigation Mode");
                styleButton(editModeButton, true);
                styleButton(navigationModeButton, true);
                modePanel.add(editModeButton);
                modePanel.add(navigationModeButton);
                JPanel northPanel = new JPanel(new BorderLayout());
                northPanel.setBackground(new Color(230, 230, 250));
                northPanel.add(modePanel, BorderLayout.NORTH);
                northPanel.add(titlePanel, BorderLayout.CENTER);
                getContentPane().add(northPanel, BorderLayout.NORTH);
            } else {
                getContentPane().add(titlePanel, BorderLayout.NORTH);
            }
            getContentPane().add(layeredPane, BorderLayout.CENTER);
            getContentPane().add(controlPanel, BorderLayout.WEST);
            if (navigationOnly) {
                switchToNavigationMode();
            } else {
                switchToNavigationMode();
                editModeButton.addActionListener(evt -> {
                    System.out.println("Switching to Edit Mode");
                    switchToEditMode();
                });
                navigationModeButton.addActionListener(evt -> {
                    System.out.println("Switching to Navigation Mode");
                    switchToNavigationMode();
                });
            }
            updateBuildingCombos();
            revalidate();
            repaint();
            JOptionPane.showMessageDialog(this, "Map cleared!");
        });

        saveButton = new JButton("Save");
        saveButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveButton.setMaximumSize(new Dimension(130, 30));
        styleButton(saveButton, false);
        saveButton.addActionListener(e -> {
            if (currentFile == null) {
                JFileChooser fileChooser = new JFileChooser(mapDirectory);
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files (*.map)", "map"));
                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    if (!selectedFile.getName().endsWith(".map")) {
                        selectedFile = new File(selectedFile.getAbsolutePath() + ".map");
                    }
                    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(selectedFile))) {
                        oos.writeObject(currentMap);
                        currentFile = selectedFile;
                        mapName = selectedFile.getName();
                        if (mapName.endsWith(".map")) {
                            mapName = mapName.substring(0, mapName.length() - 4);
                        }
                        mapNameLabel.setText(mapName);
                        JOptionPane.showMessageDialog(this, "Map saved successfully!");
                        updateMapCombo();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Error saving map!");
                    }
                }
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(currentFile))) {
                    oos.writeObject(currentMap);
                    JOptionPane.showMessageDialog(this, "Map saved successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving map!");
                }
            }
        });

        openButton = new JButton("Open");
        openButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        openButton.setMaximumSize(new Dimension(130, 30));
        styleButton(openButton, false);
        openButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(mapDirectory);
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files (*.map)", "map"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadMap(fileChooser.getSelectedFile());
            }
        });

        mapCombo = new JComboBox<>();
        mapCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        mapCombo.setMaximumSize(new Dimension(130, 30));
        mapCombo.setBackground(new Color(255, 255, 255));
        mapCombo.setForeground(new Color(60, 64, 67));
        mapCombo.setFont(new Font("Arial", Font.PLAIN, 12));
        mapCombo.addActionListener(e -> {
            String selectedMap = (String) mapCombo.getSelectedItem();
            if (selectedMap != null && !selectedMap.equals("Select Map")) {
                File mapFile = new File(mapDirectory, selectedMap + ".map");
                System.out.println("Loading map: " + mapFile.getAbsolutePath());
                loadMap(mapFile);
            }
        });
        updateMapCombo();

        startLabel = new JLabel("Start Building:");
        startLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        startLabel.setForeground(new Color(60, 64, 67));
        startCombo = new JComboBox<>();
        startCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        startCombo.setMaximumSize(new Dimension(130, 30));
        startCombo.setBackground(new Color(255, 255, 255));
        startCombo.setForeground(new Color(60, 64, 67));
        startCombo.setFont(new Font("Arial", Font.PLAIN, 12));
        endLabel = new JLabel("End Building:");
        endLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        endLabel.setForeground(new Color(60, 64, 67));
        endCombo = new JComboBox<>();
        endCombo.setAlignmentX(Component.CENTER_ALIGNMENT);
        endCombo.setMaximumSize(new Dimension(130, 30));
        endCombo.setBackground(new Color(255, 255, 255));
        endCombo.setForeground(new Color(60, 64, 67));
        endCombo.setFont(new Font("Arial", Font.PLAIN, 12));
        findPathButton = new JButton("Find Shortest Path");
        findPathButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        findPathButton.setMaximumSize(new Dimension(130, 30));
        styleButton(findPathButton, false);
        findPathButton.addActionListener(e -> {
            String start = (String) startCombo.getSelectedItem();
            String end = (String) endCombo.getSelectedItem();
            if (start != null && end != null) {
                List<String> path = currentMap.findShortestPath("B_" + start, "B_" + end);
                mapPanel.setCurrentPath(path);
                if (path.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No path found between the selected buildings!");
                }
            } else {
                JOptionPane.showMessageDialog(this, "Select start and end buildings!");
            }
        });

        deleteButton = new JButton("Delete");
        deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteButton.setMaximumSize(new Dimension(130, 30));
        styleButton(deleteButton, false);
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> {
            Object selected = mapPanel.getSelectedObject();
            if (selected instanceof Node) {
                Node node = (Node) selected;
                currentMap.deleteNode(node.id);
                mapPanel.clearSelection();
                updateBuildingCombos();
                mapPanel.repaint();
            } else if (selected instanceof Edge) {
                Edge edge = (Edge) selected;
                currentMap.deleteEdge(edge);
                mapPanel.clearSelection();
                mapPanel.repaint();
            }
        });

        if (navigationOnly) {
            switchToNavigationMode();
        } else {
            switchToNavigationMode();
            editModeButton.addActionListener(evt -> {
                System.out.println("Switching to Edit Mode");
                switchToEditMode();
            });
            navigationModeButton.addActionListener(evt -> {
                System.out.println("Switching to Navigation Mode");
                switchToNavigationMode();
            });
        }

        pack();
        setLocationRelativeTo(null);
    }

    private void styleButton(JButton button, boolean isModeButton) {
        button.setBackground(new Color(255, 255, 255));
        button.setForeground(Color.BLACK);
        button.setFont(new Font("Arial", Font.PLAIN, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.setFocusPainted(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isModeButton || button.getBackground().equals(new Color(255, 255, 255))) {
                    button.setBackground(new Color(230, 230, 230));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!isModeButton || button.getBackground().equals(new Color(230, 230, 230))) {
                    button.setBackground(new Color(255, 255, 255));
                }
            }
        });
    }

    private File loadMapDirectory() {
        Properties props = new Properties();
        File configFile = new File("config.properties");
        String mapDirPath = "./maps"; // Папка по умолчанию

        if (!configFile.exists()) {
            System.out.println("Config file 'config.properties' not found in: " + configFile.getAbsolutePath());
            System.out.println("Using default directory: " + mapDirPath);
        } else {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                mapDirPath = props.getProperty("map.directory", mapDirPath).trim();
                System.out.println("Loaded map.directory from config: " + mapDirPath);
            } catch (IOException e) {
                System.out.println("Error reading config file: " + e.getMessage());
                System.out.println("Using default directory: " + mapDirPath);
            }
        }

        File mapDir = new File(mapDirPath);

        if (!mapDir.isAbsolute()) {
            System.out.println("Path is relative: " + mapDirPath + ", resolving to absolute: " + mapDir.getAbsolutePath());
        }

        if (!mapDir.exists()) {
            System.out.println("Directory does not exist: " + mapDir.getAbsolutePath() + ", creating...");
            boolean created = mapDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create directory: " + mapDir.getAbsolutePath());
                mapDir = new File("./maps");
                mapDir.mkdirs();
            }
        }

        if (!mapDir.isDirectory()) {
            System.err.println("Path is not a directory: " + mapDir.getAbsolutePath());
            mapDir = new File("./maps");
            if (!mapDir.exists()) {
                mapDir.mkdirs();
            }
        }

        System.out.println("Using map directory: " + mapDir.getAbsolutePath());
        return mapDir;
    }

    private void updateMapCombo() {
        mapCombo.removeAllItems();
        mapCombo.addItem("Select Map");
        File[] mapFiles = mapDirectory.listFiles((dir, name) -> name.endsWith(".map"));
        if (mapFiles != null) {
            for (File mapFile : mapFiles) {
                String mapName = mapFile.getName();
                if (mapName.endsWith(".map")) {
                    mapName = mapName.substring(0, mapName.length() - 4);
                }
                mapCombo.addItem(mapName);
            }
        } else {
            System.out.println("No .map files found in: " + mapDirectory.getAbsolutePath());
        }
        mapCombo.setSelectedIndex(0);
    }

    private void loadMap(File mapFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(mapFile))) {
            currentMap = (CampusMap) ois.readObject();
            currentFile = mapFile;
            mapName = mapFile.getName();
            if (mapName.endsWith(".map")) {
                mapName = mapName.substring(0, mapName.length() - 4);
            }
            mapNameLabel.setText(mapName);
            getContentPane().removeAll();
            layeredPane = new JLayeredPane();
            mapPanel = new MapPanel(currentMap);
            mapPanel.setBounds(0, 0, 800, 600);
            layeredPane.add(mapPanel, JLayeredPane.DEFAULT_LAYER);
            resetZoomButton = new JButton("1:1");
            resetZoomButton.setFont(new Font("Arial", Font.PLAIN, 10));
            resetZoomButton.setPreferredSize(new Dimension(30, 30));
            resetZoomButton.setSize(30, 30);
            resetZoomButton.setVisible(false);
            styleButton(resetZoomButton, false);
            resetZoomButton.addActionListener(event -> {
                System.out.println("Reset Zoom button clicked");
                mapPanel.resetZoom();
            });
            layeredPane.add(resetZoomButton, JLayeredPane.PALETTE_LAYER);
            layeredPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent evt) {
                    mapPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                    updateResetZoomButtonPosition();
                }
            });
            updateResetZoomButtonPosition();
            controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
            controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            controlPanel.setBackground(new Color(245, 245, 245));
            if (!navigationOnly) {
                modePanel = new JPanel(new FlowLayout());
                modePanel.setBackground(new Color(230, 230, 250));
                editModeButton = new JButton("Edit Mode");
                navigationModeButton = new JButton("Navigation Mode");
                styleButton(editModeButton, true);
                styleButton(navigationModeButton, true);
                modePanel.add(editModeButton);
                modePanel.add(navigationModeButton);
                JPanel northPanel = new JPanel(new BorderLayout());
                northPanel.setBackground(new Color(230, 230, 250));
                northPanel.add(modePanel, BorderLayout.NORTH);
                northPanel.add(titlePanel, BorderLayout.CENTER);
                getContentPane().add(northPanel, BorderLayout.NORTH);
            } else {
                getContentPane().add(titlePanel, BorderLayout.NORTH);
            }
            getContentPane().add(layeredPane, BorderLayout.CENTER);
            getContentPane().add(controlPanel, BorderLayout.WEST);
            if (navigationOnly) {
                switchToNavigationMode();
            } else {
                switchToNavigationMode();
                editModeButton.addActionListener(evt -> {
                    System.out.println("Switching to Edit Mode");
                    switchToEditMode();
                });
                navigationModeButton.addActionListener(evt -> {
                    System.out.println("Switching to Navigation Mode");
                    switchToNavigationMode();
                });
            }
            updateBuildingCombos();
            revalidate();
            repaint();
            JOptionPane.showMessageDialog(this, "Map loaded successfully!");
        } catch (IOException | ClassNotFoundException ex) {
            JOptionPane.showMessageDialog(this, "Error loading map!");
        }
    }

    private void switchToEditMode() {
        System.out.println("Entering Edit Mode");
        mapPanel.setEditMode(true);
        mapPanel.setCurrentPath(new ArrayList<>());
        controlPanel.removeAll();
        controlPanel.add(addBuildingButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(finishBuildingButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(addRoadButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(clearMapButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(saveButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(openButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(startLabel);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(startCombo);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(endLabel);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(endCombo);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(findPathButton);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(deleteButton);
        if (!navigationOnly) {
            editModeButton.setBackground(new Color(66, 133, 244));
            editModeButton.setForeground(Color.WHITE);
            navigationModeButton.setBackground(new Color(255, 255, 255));
            navigationModeButton.setForeground(Color.BLACK);
        }
        controlPanel.revalidate();
        controlPanel.repaint();
        getContentPane().revalidate();
        getContentPane().repaint();
        System.out.println("Edit Mode: controlPanel components: " + controlPanel.getComponentCount());
    }

    private void switchToNavigationMode() {
        System.out.println("Entering Navigation Mode");
        mapPanel.setEditMode(false);
        mapPanel.setCurrentPath(new ArrayList<>());
        controlPanel.removeAll();
        controlPanel.add(mapCombo);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(startLabel);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(startCombo);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(endLabel);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(endCombo);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        controlPanel.add(findPathButton);
        if (!navigationOnly) {
            editModeButton.setBackground(new Color(255, 255, 255));
            editModeButton.setForeground(Color.BLACK);
            navigationModeButton.setBackground(new Color(66, 133, 244));
            navigationModeButton.setForeground(Color.WHITE);
        }
        updateMapCombo();
        controlPanel.revalidate();
        controlPanel.repaint();
        getContentPane().revalidate();
        getContentPane().repaint();
        System.out.println("Navigation Mode: controlPanel components: " + controlPanel.getComponentCount());
    }

    public void updateBuildingCombos() {
        startCombo.removeAllItems();
        endCombo.removeAllItems();
        for (String buildingId : currentMap.getBuildings()) {
            String buildingName = buildingId.startsWith("B_") ? buildingId.substring(2) : buildingId;
            startCombo.addItem(buildingName);
            endCombo.addItem(buildingName);
        }
    }

    private void updateResetZoomButtonPosition() {
        int buttonSize = 30;
        int margin = 10;
        resetZoomButton.setBounds(layeredPane.getWidth() - buttonSize - margin,
                layeredPane.getHeight() - buttonSize - margin,
                buttonSize, buttonSize);
    }

    public void updateResetZoomButtonVisibility(boolean visible) {
        resetZoomButton.setVisible(visible);
        updateResetZoomButtonPosition();
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Select mode:");
        System.out.println("1. Navigation Only");
        System.out.println("2. Full Mode (with Edit Mode)");
        System.out.print("Enter choice (1 or 2): ");

        int choice;
        try {
            choice = scanner.nextInt();
        } catch (Exception e) {
            choice = 1;
        }
        scanner.close();

        boolean navigationOnly = (choice == 1);

        SwingUtilities.invokeLater(() -> {
            UniversityCampusNavigation app = new UniversityCampusNavigation(navigationOnly);
            app.setVisible(true);
        });
    }
}