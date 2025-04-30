package util;

import javax.swing.*;
import java.io.*;
import java.util.Properties;

public class FileUtil {
    public static File loadMapDirectory(String mapDirectory) {
        Properties props = new Properties();
        String mapDirPath = "./maps"; // Папка по умолчанию

        if(mapDirectory != null) {
            mapDirPath = mapDirectory;
        } else {
            System.out.println("Using default directory: " + mapDirPath);
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

    public static void updateMapCombo(JComboBox<String> mapCombo, File mapDirectory) {
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
}
