
import ui.Navigation;

import javax.swing.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Создаём Scanner для чтения ввода из консоли
        Scanner scanner = new Scanner(System.in);
        boolean navigationOnly = true; // Значение по умолчанию

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
                    System.out.println("Некорректный ввод. Используется значение по умолчанию: Navigation Mode");
                }
            } else {
                System.out.println("Ввод пустой. Используется значение по умолчанию: Navigation Mode");
            }
        } catch (Exception e) {
            System.err.println("Ошибка при чтении ввода: " + e.getMessage());
            System.out.println("Используется значение по умолчанию: Navigation Mode");
        } finally {
            scanner.close();
        }

        // Сообщаем, в каком режиме запускается приложение
        System.out.println("Запуск приложения в режиме: " + (navigationOnly ? "Navigation Mode" : "Edit Mode"));

        // Запускаем приложение
        final boolean finalNavigationOnly = navigationOnly;
        SwingUtilities.invokeLater(() -> {
            Navigation app = new Navigation(finalNavigationOnly);
            app.setVisible(true);
        });
    }
}