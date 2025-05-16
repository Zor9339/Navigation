package ui;

import model.CampusMap;
import model.Edge;
import model.Node;
import util.FileUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;

public class Navigation extends JFrame {
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
    private JButton deleteButton;
    private JButton addBuildingButton;
    private JButton addRoadButton;
    private JButton editModeButton;
    private JButton navigationModeButton;
    private JButton finishBuildingButton;
    private JButton clearMapButton;
    private JButton saveButton;
    private JButton openButton;
    private JButton newMapButton;
    private JComboBox<String> mapCombo;
    private JLabel startLabel;
    private JLabel endLabel;
    private JButton findPathButton;
    private JLayeredPane layeredPane;
    private JButton resetZoomButton;
    private JButton cancelButton;
    private final boolean navigationOnly;
    private final File mapDirectory;

    // Процент ширины окна для controlPanel
    private static final double CONTROL_PANEL_WIDTH_PERCENT = 0.20; // Увеличили до 20%
    private static final int MIN_CONTROL_PANEL_WIDTH = 150; // Увеличили минимальную ширину
    private static final int CONTROL_PANEL_PADDING = 20; // Отступы (10 + 10)

    public Navigation(boolean navigationOnly, String title, String mapDirectoryPath) {
        this.navigationOnly = navigationOnly;
        currentMap = new CampusMap();
        currentFile = null;
        mapName = "Untitled";
        if(title != null) {
            setTitle(title);
        } else {
            setTitle("Navigation");
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1000, 700));

        mapDirectory = FileUtil.loadMapDirectory(mapDirectoryPath);

        titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setBackground(new Color(230, 230, 250));
        mapNameLabel = new JLabel(mapName);
        mapNameLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mapNameLabel.setForeground(new Color(25, 25, 112));
        titlePanel.add(mapNameLabel);

        initializeUI();
        setupModeButtonListeners();

        if (navigationOnly) {
            switchToNavigationMode();
        } else {
            switchToNavigationMode();
        }

        // Добавляем слушатель изменения размеров окна после полной инициализации
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateControlPanelWidth();
            }
        });

        pack();
        setLocationRelativeTo(null);

        // Вызываем updateControlPanelWidth() после pack() и полной инициализации
        updateControlPanelWidth();
    }

    public Navigation(boolean navigationOnly) {
        this(navigationOnly, "Navigation", null);
    }

    private void initializeUI() {
        layeredPane = new JLayeredPane();
        add(layeredPane, BorderLayout.CENTER);

        mapPanel = new MapPanel(currentMap, this);
        mapPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(mapPanel, JLayeredPane.DEFAULT_LAYER);

        resetZoomButton = new JButton("1:1");
        resetZoomButton.setFont(new Font("Arial", Font.PLAIN, 10));
        resetZoomButton.setPreferredSize(new Dimension(30, 30));
        resetZoomButton.setSize(30, 30);
        resetZoomButton.setVisible(false);
        resetZoomButton.setToolTipText("Reset Zoom to Default (1:1)");
        styleButton(resetZoomButton, false);
        resetZoomButton.addActionListener(evt -> {
            System.out.println("Reset Zoom button clicked");
            mapPanel.resetZoom();
            updateControlPanelWidth();
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
        styleButton(addBuildingButton, false);
        addBuildingButton.addActionListener(evt -> {
            String name = JOptionPane.showInputDialog(this, "Enter building name:");
            if (name != null && !name.trim().isEmpty()) {
                showShapeSelectionDialog(name);
            }
        });

        finishBuildingButton = new JButton("Finish Drawing");
        finishBuildingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(finishBuildingButton, false);
        finishBuildingButton.addActionListener(evt -> {
            mapPanel.finishDrawingBuilding();
            if (mapPanel.isSelectingConnectionPoint()) {
                JOptionPane.showMessageDialog(this, "Click on the building contour to set the entry point.");
            }
            updateBuildingCombos();
        });

        addRoadButton = new JButton("Add Road");
        addRoadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(addRoadButton, false);
        addRoadButton.addActionListener(evt -> {
            mapPanel.startDrawingRoad();
            JOptionPane.showMessageDialog(this, "Click on a building entry point or junction to start. Click to add points, double-click to finish.");
        });

        clearMapButton = new JButton("Clear Map");
        clearMapButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(clearMapButton, false);
        clearMapButton.addActionListener(evt -> {
            currentMap = new CampusMap();
            currentFile = null;
            mapName = "Untitled";
            mapNameLabel.setText(mapName);
            getContentPane().removeAll();
            initializeUI();
            setupModeButtonListeners();
            if (navigationOnly) {
                switchToNavigationMode();
            } else {
                switchToNavigationMode();
            }
            updateBuildingCombos();
            revalidate();
            repaint();
            updateControlPanelWidth();
            JOptionPane.showMessageDialog(this, "Map cleared!");
        });

        saveButton = new JButton("Save");
        saveButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(saveButton, false);
        saveButton.addActionListener(evt -> {
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
                        FileUtil.updateMapCombo(mapCombo, mapDirectory);
                        updateNewMapButtonState();
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
        styleButton(openButton, false);
        openButton.addActionListener(evt -> {
            JFileChooser fileChooser = new JFileChooser(mapDirectory);
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files (*.map)", "map"));
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadMap(fileChooser.getSelectedFile());
                updateNewMapButtonState();
            }
        });

        newMapButton = new JButton("New Map");
        newMapButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(newMapButton, false);
        newMapButton.setEnabled(false);
        newMapButton.addActionListener(evt -> {
            currentMap = new CampusMap();
            currentFile = null;
            mapName = "Untitled";
            mapNameLabel.setText(mapName);
            getContentPane().removeAll();
            initializeUI();
            setupModeButtonListeners();
            if (navigationOnly) {
                switchToNavigationMode();
            } else {
                switchToEditMode();
            }
            updateBuildingCombos();
            revalidate();
            repaint();
            updateNewMapButtonState();
            updateControlPanelWidth();
            JOptionPane.showMessageDialog(this, "New map created!");
        });

        mapCombo = createStyledComboBox();
        mapCombo.addActionListener(evt -> {
            String selectedMap = (String) mapCombo.getSelectedItem();
            if (selectedMap != null && !selectedMap.equals("Select Map")) {
                File mapFile = new File(mapDirectory, selectedMap + ".map");
                System.out.println("Loading map: " + mapFile.getAbsolutePath());
                loadMap(mapFile);
                updateNewMapButtonState();
            }
        });
        FileUtil.updateMapCombo(mapCombo, mapDirectory);

        startLabel = createStyledLabel("Start Building:");
        startCombo = createStyledComboBox();
        endLabel = createStyledLabel("End Building:");
        endCombo = createStyledComboBox();
        findPathButton = new JButton("Find Shortest Path");
        findPathButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(findPathButton, false);
        findPathButton.addActionListener(evt -> {
            String start = (String) startCombo.getSelectedItem();
            String end = (String) endCombo.getSelectedItem();
            if (start != null && end != null) {
                java.util.List<String> path = currentMap.findShortestPath("B_" + start, "B_" + end);
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
        styleButton(deleteButton, false);
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(evt -> {
            Object selected = mapPanel.getSelectedObject();
            if (selected instanceof Node) {
                Node node = (Node) selected;
                currentMap.deleteNode(node.getId());
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

        cancelButton = new JButton("Cancel");
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        styleButton(cancelButton, false);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(evt -> {
            mapPanel.resetModes();
        });
    }

    private void setupModeButtonListeners() {
        if (!navigationOnly) {
            editModeButton.addActionListener(evt -> {
                System.out.println("Switching to Edit Mode");
                switchToEditMode();
            });
            navigationModeButton.addActionListener(evt -> {
                System.out.println("Switching to Navigation Mode");
                switchToNavigationMode();
            });
        }
    }

    private void updateNewMapButtonState() {
        if (newMapButton != null) {
            newMapButton.setEnabled(currentFile != null);
        }
    }

    private JComboBox<String> createStyledComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        comboBox.setBackground(new Color(255, 255, 255));
        comboBox.setForeground(new Color(60, 64, 67));
        comboBox.setFont(new Font("Arial", Font.PLAIN, 12));
        updateComboBoxSize(comboBox);

        // Устанавливаем кастомный рендерер с тултипами
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String text = value != null ? value.toString() : "";
                label.setText(text);
                label.setToolTipText(text); // Добавляем тултип
                return label;
            }
        });

        return comboBox;
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(new Color(60, 64, 67));
        return label;
    }

    private void addComponentWithSpacing(JPanel panel, Component component) {
        panel.add(component);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    private void updateUI() {
        controlPanel.revalidate();
        controlPanel.repaint();
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private void updateModeButtonStyles(boolean isEditMode) {
        if (!navigationOnly) {
            if (isEditMode) {
                editModeButton.setBackground(new Color(66, 133, 244));
                editModeButton.setForeground(Color.WHITE);
                navigationModeButton.setBackground(new Color(255, 255, 255));
                navigationModeButton.setForeground(Color.BLACK);
            } else {
                editModeButton.setBackground(new Color(255, 255, 255));
                editModeButton.setForeground(Color.BLACK);
                navigationModeButton.setBackground(new Color(66, 133, 244));
                navigationModeButton.setForeground(Color.WHITE);
            }
        }
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
        updateButtonSize(button);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                if (!isModeButton || button.getBackground().equals(new Color(255, 255, 255))) {
                    button.setBackground(new Color(230, 230, 230));
                }
            }
            @Override
            public void mouseExited(MouseEvent evt) {
                if (!isModeButton || button.getBackground().equals(new Color(230, 230, 230))) {
                    button.setBackground(new Color(255, 255, 255));
                }
            }
        });
    }

    private void showShapeSelectionDialog(String buildingName) {
        JDialog dialog = new JDialog(this, "Select Building Shape", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);

        JPanel messagePanel = new JPanel();
        messagePanel.setBackground(new Color(245, 245, 245));
        JLabel messageLabel = new JLabel("Select building shape:");
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        messageLabel.setForeground(new Color(60, 64, 67));
        messagePanel.add(messageLabel);
        dialog.add(messagePanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBackground(new Color(245, 245, 245));

        JButton rectangleButton = new JButton("Rectangle");
        styleButton(rectangleButton, false);
        rectangleButton.addActionListener(evt -> {
            mapPanel.startDrawingBuilding(buildingName, "rectangle");
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Click and drag to draw the shape. Then edit vertices (if any) or double-click to add points. Press 'Finish Drawing' when done.");
        });

        JButton circleButton = new JButton("Circle");
        styleButton(circleButton, false);
        circleButton.addActionListener(evt -> {
            mapPanel.startDrawingBuilding(buildingName, "circle");
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Click and drag to draw the shape. Then edit vertices (if any) or double-click to add points. Press 'Finish Drawing' when done.");
        });

        buttonPanel.add(rectangleButton);
        buttonPanel.add(circleButton);
        dialog.add(buttonPanel, BorderLayout.CENTER);

        dialog.setVisible(true);
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
            initializeUI();
            setupModeButtonListeners();
            if (navigationOnly) {
                switchToNavigationMode();
            } else {
                switchToNavigationMode();
            }
            updateBuildingCombos();
            revalidate();
            repaint();
            updateControlPanelWidth();
            JOptionPane.showMessageDialog(this, "Map loaded successfully!");
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println(ex);
            JOptionPane.showMessageDialog(this, "Error loading map!");
        }
    }

    private void switchToEditMode() {
        System.out.println("Entering Edit Mode");
        mapPanel.setEditMode(true);
        mapPanel.setCurrentPath(new ArrayList<>());
        controlPanel.removeAll();
        addComponentWithSpacing(controlPanel, addBuildingButton);
        addComponentWithSpacing(controlPanel, finishBuildingButton);
        addComponentWithSpacing(controlPanel, addRoadButton);
        addComponentWithSpacing(controlPanel, clearMapButton);
        addComponentWithSpacing(controlPanel, saveButton);
        addComponentWithSpacing(controlPanel, openButton);
        addComponentWithSpacing(controlPanel, newMapButton);
        addComponentWithSpacing(controlPanel, startLabel);
        addComponentWithSpacing(controlPanel, startCombo);
        addComponentWithSpacing(controlPanel, endLabel);
        addComponentWithSpacing(controlPanel, endCombo);
        addComponentWithSpacing(controlPanel, findPathButton);
        addComponentWithSpacing(controlPanel, deleteButton);
        addComponentWithSpacing(controlPanel, cancelButton);
        updateModeButtonStyles(true);
        updateCancelButtonVisibility();
        updateNewMapButtonState();
        updateControlPanelWidth();
        updateUI();
        System.out.println("Edit Mode: controlPanel components: " + controlPanel.getComponentCount());
    }

    private void switchToNavigationMode() {
        System.out.println("Entering Navigation Mode");
        mapPanel.setEditMode(false);
        mapPanel.setCurrentPath(new ArrayList<>());
        controlPanel.removeAll();
        addComponentWithSpacing(controlPanel, mapCombo);
        addComponentWithSpacing(controlPanel, startLabel);
        addComponentWithSpacing(controlPanel, startCombo);
        addComponentWithSpacing(controlPanel, endLabel);
        addComponentWithSpacing(controlPanel, endCombo);
        addComponentWithSpacing(controlPanel, findPathButton);
        updateModeButtonStyles(false);
        FileUtil.updateMapCombo(mapCombo, mapDirectory);
        updateControlPanelWidth();
        updateUI();
        System.out.println("Navigation Mode: controlPanel components: " + controlPanel.getComponentCount());
    }

    public void updateBuildingCombos() {
        ArrayList<String> buildingIds = new ArrayList<>(currentMap.getBuildings());
        Collections.sort(buildingIds, (id1, id2) -> {
            String name1 = id1.startsWith("B_") ? id1.substring(2) : id1;
            String name2 = id2.startsWith("B_") ? id2.substring(2) : id2;
            return name1.compareTo(name2);
        });

        startCombo.removeAllItems();
        endCombo.removeAllItems();

        for (String buildingId : buildingIds) {
            String buildingName = buildingId.startsWith("B_") ? buildingId.substring(2) : buildingId;
            startCombo.addItem(buildingName);
            endCombo.addItem(buildingName);
        }
        adjustComboBoxPopupWidth(startCombo);
        adjustComboBoxPopupWidth(endCombo);
        updateComboBoxSize(startCombo);
        updateComboBoxSize(endCombo);
    }

    private void updateResetZoomButtonPosition() {
        int buttonWidth = 50;
        int buttonHeight = 50;
        int margin = 10;
        resetZoomButton.setBounds(
                layeredPane.getWidth() - buttonWidth - margin,
                layeredPane.getHeight() - buttonHeight - margin,
                buttonWidth, buttonHeight
        );
    }

    public void updateResetZoomButtonVisibility(boolean visible) {
        resetZoomButton.setVisible(visible);
        updateResetZoomButtonPosition();
    }

    public void updateCancelButtonVisibility() {
        if (cancelButton != null && mapPanel != null) {
            cancelButton.setVisible(mapPanel.isInAddMode());
            updateButtonSize(cancelButton);
            controlPanel.revalidate();
            controlPanel.repaint();
        }
    }

    public void setAddBuildingButtonBackground(Color color) {
        if (addBuildingButton != null) {
            addBuildingButton.setBackground(color);
        }
    }

    public void setAddRoadButtonBackground(Color color) {
        if (addRoadButton != null) {
            addRoadButton.setBackground(color);
        }
    }

    public void setDeleteButtonEnabled(boolean enabled) {
        if (deleteButton != null) {
            deleteButton.setEnabled(enabled);
        }
    }

    // Метод для пересчёта ширины controlPanel в процентах от ширины окна
    private void updateControlPanelWidth() {
        // Проверка на null
        if (controlPanel == null) {
            return;
        }

        // Получаем текущую ширину окна
        int windowWidth = getWidth();
        // Вычисляем ширину controlPanel как процент от ширины окна
        int controlPanelWidth = (int) (windowWidth * CONTROL_PANEL_WIDTH_PERCENT);
        // Убеждаемся, что ширина не меньше минимальной
        controlPanelWidth = Math.max(controlPanelWidth, MIN_CONTROL_PANEL_WIDTH);

        // Устанавливаем ширину controlPanel (учитываем отступы)
        controlPanel.setPreferredSize(new Dimension(controlPanelWidth, 0));
        controlPanel.setMaximumSize(new Dimension(controlPanelWidth, Integer.MAX_VALUE));

        // Обновляем размеры всех кнопок и комбобоксов
        updateButtonSize(addBuildingButton);
        updateButtonSize(finishBuildingButton);
        updateButtonSize(addRoadButton);
        updateButtonSize(clearMapButton);
        updateButtonSize(saveButton);
        updateButtonSize(openButton);
        updateButtonSize(newMapButton);
        updateButtonSize(findPathButton);
        updateButtonSize(deleteButton);
        updateButtonSize(cancelButton);
        updateComboBoxSize(mapCombo);
        adjustComboBoxPopupWidth(mapCombo);
        updateComboBoxSize(startCombo);
        adjustComboBoxPopupWidth(startCombo);
        updateComboBoxSize(endCombo);
        adjustComboBoxPopupWidth(endCombo);

        // Обновляем UI
        updateUI();
    }

    // Метод для обновления размеров кнопок
    private void updateButtonSize(JButton button) {
        if (button != null && controlPanel != null) {
            int buttonWidth = controlPanel.getPreferredSize().width - CONTROL_PANEL_PADDING;
            button.setMaximumSize(new Dimension(buttonWidth, 30));
            button.setPreferredSize(new Dimension(buttonWidth, 30));
        }
    }

    // Метод для обновления размеров комбобоксов
    private void updateComboBoxSize(JComboBox<String> comboBox) {
        if (comboBox != null && controlPanel != null) {
            int comboWidth = controlPanel.getPreferredSize().width - CONTROL_PANEL_PADDING;
            comboBox.setMaximumSize(new Dimension(comboWidth, 30));
            comboBox.setPreferredSize(new Dimension(comboWidth, 30));
        }
    }

    // Метод для настройки ширины выпадающего списка JComboBox
    private void adjustComboBoxPopupWidth(JComboBox<String> comboBox) {
        if (comboBox == null || comboBox.getItemCount() == 0) {
            return;
        }

        // Находим самый длинный элемент
        String longestItem = "";
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            String item = comboBox.getItemAt(i);
            if (item != null && item.length() > longestItem.length()) {
                longestItem = item;
            }
        }

        // Устанавливаем прототип для вычисления ширины
        if (!longestItem.isEmpty()) {
            comboBox.setPrototypeDisplayValue(longestItem + "  "); // Добавляем небольшой отступ
        }

        // Обновляем UI
        comboBox.revalidate();
        comboBox.repaint();
    }
}