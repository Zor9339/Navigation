package model;

import java.awt.Point;
import java.awt.Polygon;
import java.io.Serializable;
import java.util.*;

public class CampusMap implements Serializable {
    private static final long serialVersionUID = 1L;

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

    public List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
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
        // Проверка на несуществующие узлы
        if (start == null || end == null || !graph.containsKey(start) || !graph.containsKey(end)) {
            return Collections.emptyList();
        }

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