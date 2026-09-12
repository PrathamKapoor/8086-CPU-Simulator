package utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Logger — captures simulation events for display in the Console panel.
 */
public class Logger {
    private static final List<String>    log = new ArrayList<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void log(String msg) {
        String entry = "[" + LocalTime.now().format(FMT) + "] " + msg;
        log.add(entry);
        System.out.println(entry);
    }

    public static List<String> getLog()   { return new ArrayList<>(log); }
    public static String getLastEntry()   { return log.isEmpty() ? "" : log.get(log.size()-1); }
    public static void clear()            { log.clear(); }
}
