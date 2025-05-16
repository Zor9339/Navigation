package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampusMapTest {
    private CampusMap campusMap;

    @BeforeEach
    void setUp() {
        campusMap = new CampusMap();
    }

    // Тесты для addBuilding()
    @Test
    void addBuilding_shouldAddBuildingWithValidParameters() {
        Polygon shape = createTestPolygon();
        Point connectionPoint = new Point(5, 5);

        assertDoesNotThrow(() ->
                campusMap.addBuilding("Main", shape, connectionPoint));

        assertTrue(campusMap.getNodes().containsKey("B_Main"));
    }

    @Test
    void addBuilding_shouldAllowAddingBuildingsWithSameNameButDifferentCase() {
        Polygon shape = createTestPolygon();
        Point point = new Point(0, 0);

        campusMap.addBuilding("library", shape, point);
        campusMap.addBuilding("Library", shape, point);

        assertEquals(2, campusMap.getBuildings().size());
    }

    // Тесты для addJunction()
    @Test
    void addJunction_shouldAddJunctionWithValidPosition() {
        Point position = new Point(100, 100);
        String junctionId = assertDoesNotThrow(() -> campusMap.addJunction(position));

        assertTrue(junctionId.startsWith("J_"));
        assertTrue(campusMap.getNodes().containsKey(junctionId));
    }

    @Test
    void addJunction_shouldGenerateUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            ids.add(campusMap.addJunction(new Point(i, i)));
        }
        assertEquals(10, ids.size());
    }

    // Тесты для addRoad()
    @Test
    void addRoad_shouldConnectExistingNodes() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        String junctionId = campusMap.addJunction(new Point(50, 0));

        assertDoesNotThrow(() -> campusMap.addRoad("B_A", junctionId));
        assertEquals(1, campusMap.getGraph().get("B_A").size());
    }

    @Test
    void addRoad_shouldNotConnectNonexistentNodes() {
        campusMap.addBuilding("Valid", createTestPolygon(), new Point(0, 0));

        // Не должно выбрасывать исключение, просто ничего не делает
        assertDoesNotThrow(() -> {
            campusMap.addRoad("B_Valid", "nonexistent");
            campusMap.addRoad("nonexistent1", "nonexistent2");
            campusMap.addRoad("B_Valid", "B_Valid");
        });

        assertTrue(campusMap.getEdges().isEmpty());
    }

    // Тесты для deleteNode()
    @Test
    void deleteNode_shouldRemoveBuildingAndConnectedRoads() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addBuilding("B", createTestPolygon(), new Point(100, 0));
        campusMap.addRoad("B_A", "B_B");

        campusMap.deleteNode("B_A");

        assertFalse(campusMap.getNodes().containsKey("B_A"));
        assertTrue(campusMap.getEdges().isEmpty());
    }

    @Test
    void deleteNode_shouldDoNothingForNonexistentNode() {
        assertDoesNotThrow(() -> campusMap.deleteNode("nonexistent"));
    }

    // Тесты для deleteEdge()
    @Test
    void deleteEdge_shouldRemoveExistingEdge() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addBuilding("B", createTestPolygon(), new Point(100, 0));
        campusMap.addRoad("B_A", "B_B");
        Edge edge = new ArrayList<>(campusMap.getEdges()).get(0);

        campusMap.deleteEdge(edge);

        assertTrue(campusMap.getEdges().isEmpty());
    }

    @Test
    void deleteEdge_shouldDoNothingForNullEdge() {
        assertDoesNotThrow(() -> campusMap.deleteEdge(null));
    }

    // Тесты для findShortestPath()
    @Test
    void findShortestPath_shouldReturnPathForConnectedNodes() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addBuilding("B", createTestPolygon(), new Point(100, 0));
        campusMap.addRoad("B_A", "B_B");

        List<String> path = campusMap.findShortestPath("B_A", "B_B");

        assertEquals(Arrays.asList("B_A", "B_B"), path);
    }

    @Test
    void findShortestPath_shouldReturnEmptyListForUnconnectedNodes() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addBuilding("B", createTestPolygon(), new Point(100, 0));

        List<String> path = campusMap.findShortestPath("B_A", "B_B");

        assertTrue(path.isEmpty());
    }

    @Test
    void findShortestPath_shouldHandleNonexistentNodes() {
        List<String> path = campusMap.findShortestPath("nonexistent1", "nonexistent2");
        assertTrue(path.isEmpty());
    }

    // Тесты для getters
    @Test
    void getNodes_shouldReturnAllNodes() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addJunction(new Point(50, 50));

        assertEquals(2, campusMap.getNodes().size());
    }

    @Test
    void getBuildings_shouldReturnOnlyBuildings() {
        campusMap.addBuilding("A", createTestPolygon(), new Point(0, 0));
        campusMap.addJunction(new Point(50, 50));

        assertEquals(1, campusMap.getBuildings().size());
        assertTrue(campusMap.getBuildings().contains("B_A"));
    }

    // Вспомогательные методы
    private Polygon createTestPolygon() {
        return new Polygon(new int[]{0, 10, 10, 0}, new int[]{0, 0, 10, 10}, 4);
    }
}