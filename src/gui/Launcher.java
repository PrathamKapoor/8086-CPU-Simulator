package gui;

/**
 * Bootstrap launcher for JavaFX applications packaged in a shaded FAT JAR.
 * Avoids the "JavaFX runtime components are missing" check in modern JDKs
 * by providing a non-Application entrypoint that delegates to MainGUI.
 */
public class Launcher {
    public static void main(String[] args) {
        MainGUI.main(args);
    }
}
