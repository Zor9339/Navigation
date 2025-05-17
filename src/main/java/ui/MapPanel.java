package ui;

import model.CampusMap;
import model.Edge;
import model.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;

public class MapPanel extends JPanel {
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
    private String buildingShapeType; // "rectangle" или "circle"
    private Point tempCircleCenter; // Центр временной окружности
    private int tempCircleRadius; // Радиус временной окружности
    private Navigation parentFrame; // Ссылка на родительский фрейм

    public MapPanel(CampusMap map, Navigation parentFrame) {
        this.map = map;
        this.parentFrame = parentFrame;
        this.currentPath = new ArrayList<>();
        this.isEditMode = true;
        resetModes();
        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(245, 245, 245));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingBuilding) {
                    startPoint = scaledPoint;
                    if (buildingShapeType.equals("circle")) {
                        tempCircleCenter = scaledPoint;
                        tempCircleRadius = 0;
                    }
                } else if (editingBuilding) {
                    draggingVertexIndex = findVertexAt(scaledPoint.x, scaledPoint.y);
                } else if (drawingRoad && roadStartNode == null) {
                    roadStartNode = findNodeAt(scaledPoint.x, scaledPoint.y);
                    if (roadStartNode != null) {
                        tempRoadPoints.add(map.getNodes().get(roadStartNode).getPosition());
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
                    if (buildingShapeType.equals("rectangle")) {
                        int x1 = Math.min(startPoint.x, scaledPoint.x);
                        int y1 = Math.min(startPoint.y, scaledPoint.y);
                        int x2 = Math.max(startPoint.x, scaledPoint.x);
                        int y2 = Math.max(startPoint.y, scaledPoint.y);
                        tempPolygon = new Polygon();
                        tempPolygon.addPoint(x1, y1);
                        tempPolygon.addPoint(x2, y1);
                        tempPolygon.addPoint(x2, y2);
                        tempPolygon.addPoint(x1, y2);
                    } else if (buildingShapeType.equals("circle")) {
                        tempCircleRadius = (int) Math.sqrt(Math.pow(scaledPoint.x - tempCircleCenter.x, 2) + Math.pow(scaledPoint.y - tempCircleCenter.y, 2));
                        tempPolygon = approximateCircle(tempCircleCenter, tempCircleRadius);
                    }
                    drawingBuilding = false;
                    editingBuilding = true;
                    startPoint = null;
                    updateCancelButtonVisibility();
                    repaint();
                }
                draggingVertexIndex = -1;
            }

            @Override
            public void mouseDragged(MouseEvent evt) {
                if (!isEditMode) return;
                Point scaledPoint = getScaledPoint(evt.getPoint());
                if (drawingBuilding && startPoint != null) {
                    if (buildingShapeType.equals("circle")) {
                        tempCircleRadius = (int) Math.sqrt(Math.pow(scaledPoint.x - tempCircleCenter.x, 2) + Math.pow(scaledPoint.y - tempCircleCenter.y, 2));
                    }
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
                            tempRoadPoints.add(map.getNodes().get(nodeId).getPosition());
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
                    parentFrame.setDeleteButtonEnabled(selectedObject != null);
                    updateCancelButtonVisibility();
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
                scale = Math.max(scale - 0.1, 0.1);
            }

            double scaleFactor = scale / oldScale;

            translateX = mouseX - scaleFactor * (mouseX - translateX);
            translateY = mouseY - scaleFactor * (mouseY - translateY);

            parentFrame.updateResetZoomButtonVisibility(scale != 1.0);

            repaint();
        });
    }

    public Point getScaledPoint(Point p) {
        double x = (p.x - translateX) / scale;
        double y = (p.y - translateY) / scale;
        return new Point((int) x, (int) y);
    }

    public void resetModes() {
        drawingBuilding = false;
        editingBuilding = false;
        selectingConnectionPoint = false;
        drawingRoad = false;
        tempPolygon = new Polygon();
        tempCircleCenter = null;
        tempCircleRadius = 0;
        tempRoadPoints = new ArrayList<>();
        roadStartNode = null;
        startPoint = null;
        currentBuildingName = null;
        buildingShapeType = null;
        clearSelection();
        parentFrame.setAddBuildingButtonBackground(new Color(255, 255, 255));
        parentFrame.setAddRoadButtonBackground(new Color(255, 255, 255));
        updateCancelButtonVisibility();
        repaint();
    }

    private Node findBuildingAt(Point p) {
        for (Node node : map.getNodes().values()) {
            if (node.isBuilding()) {
                if (node.getShape().contains(p) || isPointOnPolygonContour(node.getShape(), p)) {
                    return node;
                }
            }
        }
        return null;
    }

    private String findNodeAt(int x, int y) {
        for (Node node : map.getNodes().values()) {
            Point pos = node.getPosition();
            if (Math.sqrt(Math.pow(pos.x - x, 2) + Math.pow(pos.y - y, 2)) < 15 / scale) {
                return node.getId();
            }
        }
        return null;
    }

    private Edge findEdgeAt(Point p) {
        for (String from : map.getGraph().keySet()) {
            for (Map.Entry<String, Edge> neighbor : map.getGraph().get(from).entrySet()) {
                String to = neighbor.getKey();
                Point p1 = map.getNodes().get(from).getPosition();
                Point p2 = map.getNodes().get(to).getPosition();
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
                if (node.getConnectionPoint() != null &&
                        Math.abs(node.getConnectionPoint().x - p.x) < 5 / scale &&
                        Math.abs(node.getConnectionPoint().y - p.y) < 5 / scale) {
                    return false;
                }
                if (node.getShape().contains(p) || isPointOnPolygonContour(node.getShape(), p)) {
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

    private Polygon approximateCircle(Point center, int radius) {
        Polygon circle = new Polygon();
        int numPoints = 32;
        for (int i = 0; i < numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints;
            int x = center.x + (int) (radius * Math.cos(angle));
            int y = center.y + (int) (radius * Math.sin(angle));
            circle.addPoint(x, y);
        }
        return circle;
    }

    public void startDrawingBuilding(String name, String shapeType) {
        resetModes();
        drawingBuilding = true;
        currentBuildingName = name;
        buildingShapeType = shapeType;
        parentFrame.setAddBuildingButtonBackground(new Color(230, 230, 230));
        updateCancelButtonVisibility();
        repaint();
    }

    public void finishDrawingBuilding() {
        if (editingBuilding) {
            selectingConnectionPoint = true;
            editingBuilding = false;
            updateCancelButtonVisibility();
            repaint();
        } else {
            JOptionPane.showMessageDialog(null, "Complete the shape first!");
        }
    }

    public boolean isSelectingConnectionPoint() {
        return selectingConnectionPoint;
    }

    public void startDrawingRoad() {
        resetModes();
        drawingRoad = true;
        parentFrame.setAddRoadButtonBackground(new Color(230, 230, 230));
        updateCancelButtonVisibility();
        repaint();
    }

    public void setCurrentPath(List<String> path) {
        currentPath = path;
        repaint();
    }

    public void clearSelection() {
        selectedObject = null;
        parentFrame.setDeleteButtonEnabled(false);
        repaint();
    }

    public Object getSelectedObject() {
        return selectedObject;
    }

    public void setEditMode(boolean editMode) {
        isEditMode = editMode;
        if (!isEditMode) {
            resetModes();
        }
        repaint();
    }

    private void updateBuildingCombos() {
        parentFrame.updateBuildingCombos();
    }

    public void resetZoom() {
        scale = 1.0;
        translateX = 0;
        translateY = 0;
        parentFrame.updateResetZoomButtonVisibility(false);
        revalidate();
        repaint();
    }

    private void updateCancelButtonVisibility() {
        parentFrame.updateCancelButtonVisibility();
    }

    public boolean isInAddMode() {
        return drawingBuilding || editingBuilding || selectingConnectionPoint || drawingRoad;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.translate(translateX, translateY);
        g2d.scale(scale, scale);

        // Отрисовка дорог
        g2d.setColor(new Color(200, 200, 200));
        Set<String> drawnEdges = new HashSet<>();
        for (String from : map.getGraph().keySet()) {
            for (Map.Entry<String, Edge> neighbor : map.getGraph().get(from).entrySet()) {
                String to = neighbor.getKey();
                String edgeKey = from.compareTo(to) < 0 ? from + "-" + to : to + "-" + from;
                if (!drawnEdges.contains(edgeKey)) {
                    Point fromPos = map.getNodes().get(from).getPosition();
                    Point toPos = map.getNodes().get(to).getPosition();
                    if (isEditMode && selectedObject instanceof Edge && selectedObject == neighbor.getValue()) {
                        g2d.setColor(new Color(255, 165, 0));
                        g2d.setStroke(new BasicStroke((float) (6 / scale)));
                    } else {
                        g2d.setColor(new Color(200, 200, 200));
                        g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    }
                    g2d.drawLine(fromPos.x, fromPos.y, toPos.x, toPos.y);
                    drawnEdges.add(edgeKey);
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
            }
        }

        // Отрисовка временной дороги
        if (isEditMode && drawingRoad && !tempRoadPoints.isEmpty()) {
            g2d.setColor(new Color(200, 200, 200));
            g2d.setStroke(new BasicStroke((float) (4 / scale)));
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
            g2d.setColor(new Color(66, 133, 244));
            g2d.setStroke(new BasicStroke((float) (3 / scale)));
            for (int i = 0; i < currentPath.size() - 1; i++) {
                String from = currentPath.get(i);
                String to = currentPath.get(i + 1);
                Point fromPos = map.getNodes().get(from).getPosition();
                Point toPos = map.getNodes().get(to).getPosition();
                g2d.drawLine(fromPos.x, fromPos.y, toPos.x, toPos.y);
            }
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
        }

        // Отрисовка зданий и точек
        for (Node node : map.getNodes().values()) {
            if (node.isBuilding()) {
                g2d.setColor(new Color(100, 149, 237, 180));
                g2d.fillPolygon(node.getShape());
                g2d.setColor(new Color(25, 25, 112));
                g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2d.drawPolygon(node.getShape());
                if (isEditMode && selectedObject == node) {
                    g2d.setColor(new Color(255, 165, 0));
                    g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    g2d.drawPolygon(node.getShape());
                    g2d.drawOval(node.getConnectionPoint().x - (int) (7 / scale), node.getConnectionPoint().y - (int) (7 / scale), (int) (14 / scale), (int) (14 / scale));
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
                g2d.setColor(new Color(34, 139, 34));
                g2d.fillOval(node.getConnectionPoint().x - (int) (5 / scale), node.getConnectionPoint().y - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                g2d.setColor(Color.WHITE);
                g2d.fillOval(node.getConnectionPoint().x - (int) (2 / scale), node.getConnectionPoint().y - (int) (2 / scale), (int) (4 / scale), (int) (4 / scale));
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.PLAIN, (int) (12 / scale)));
                Rectangle bounds = node.getShape().getBounds();
                g2d.drawString(node.getId().substring(2), bounds.x + bounds.width + 5, bounds.y + bounds.height / 2);
            } else {
                g2d.setColor(new Color(180, 180, 180));
                g2d.fillOval(node.getPosition().x - (int) (5 / scale), node.getPosition().y - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                if (isEditMode && selectedObject == node) {
                    g2d.setColor(new Color(255, 165, 0));
                    g2d.setStroke(new BasicStroke((float) (4 / scale)));
                    g2d.drawOval(node.getPosition().x - (int) (7 / scale), node.getPosition().y - (int) (7 / scale), (int) (14 / scale), (int) (14 / scale));
                    g2d.setStroke(new BasicStroke((float) (1 / scale)));
                }
            }
        }

        // Отрисовка временного здания
        if (isEditMode && drawingBuilding && startPoint != null) {
            Point mousePos = getScaledPoint(getMousePosition() != null ? getMousePosition() : startPoint);
            g2d.setColor(new Color(100, 149, 237, 180));
            if (buildingShapeType.equals("rectangle")) {
                int x = Math.min(startPoint.x, mousePos.x);
                int y = Math.min(startPoint.y, mousePos.y);
                int w = Math.abs(startPoint.x - mousePos.x);
                int h = Math.abs(startPoint.y - mousePos.y);
                g2d.fillRect(x, y, w, h);
                g2d.setColor(new Color(25, 25, 112));
                g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2d.drawRect(x, y, w, h);
            } else if (buildingShapeType.equals("circle")) {
                g2d.fillOval(tempCircleCenter.x - tempCircleRadius, tempCircleCenter.y - tempCircleRadius, 2 * tempCircleRadius, 2 * tempCircleRadius);
                g2d.setColor(new Color(25, 25, 112));
                g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2d.drawOval(tempCircleCenter.x - tempCircleRadius, tempCircleCenter.y - tempCircleRadius, 2 * tempCircleRadius, 2 * tempCircleRadius);
            }
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
        }

        // Отрисовка редактируемого здания
        if (isEditMode && (editingBuilding || selectingConnectionPoint) && tempPolygon.npoints > 0) {
            g2d.setColor(new Color(100, 149, 237, 180));
            g2d.fillPolygon(tempPolygon);
            g2d.setColor(new Color(25, 25, 112));
            g2d.setStroke(new BasicStroke((float) (1.5 / scale)));
            g2d.drawPolygon(tempPolygon);
            g2d.setStroke(new BasicStroke((float) (1 / scale)));
            if (editingBuilding) {
                g2d.setColor(new Color(180, 180, 180)); // Точки для редактирования
                for (int i = 0; i < tempPolygon.npoints; i++) {
                    g2d.fillOval(tempPolygon.xpoints[i] - (int) (5 / scale), tempPolygon.ypoints[i] - (int) (5 / scale), (int) (10 / scale), (int) (10 / scale));
                }
            }
        }
    }
}