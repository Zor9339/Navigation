
import ui.Navigation;

import javax.swing.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Создаём Scanner для чтения ввода из консоли
        Scanner scanner = new Scanner(System.in);
        boolean navigationOnly = true; // Значение по умолчанию
        String mapDirectoryPath = "C:/Main/maps";

        // Запрашиваем режим у пользователя
        System.out.println("Запустить в Edit Mode? (yes/no):");

        try {
            String input = scanner.nextLine().trim().toLowerCase();
            if (!input.isEmpty()) {
                if (input.equals("yes")) {
                    navigationOnly = false;
                } else if (input.equals("no")) {
                    navigationOnly = true;
                } else {
                    System.out.println("Incorrect input. Using default: Navigation Mode");
                }
            } else {
                System.out.println("Empty input. Using default: Navigation Mode");
            }
        } catch (Exception e) {
            System.err.println("Error while reading input: " + e.getMessage());
            System.out.println("Using default: Navigation Mode");
        } finally {
            scanner.close();
        }

        // Сообщаем, в каком режиме запускается приложение
        System.out.println("Starting app in mode: " + (navigationOnly ? "Navigation Mode" : "Edit Mode"));

        // Запускаем приложение
        final boolean finalNavigationOnly = navigationOnly;
        SwingUtilities.invokeLater(() -> {
            Navigation app = new Navigation(finalNavigationOnly, null, mapDirectoryPath);
            app.setVisible(true);
        });
    }
}