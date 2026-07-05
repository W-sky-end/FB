package wowbot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class DebugUtils {

    private static final String DEBUG_FOLDER = "debug";


    public static void saveImage(BufferedImage img, String prefix) {
        try {
            File folder = new File(DEBUG_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            String fileName = prefix + "_" + System.currentTimeMillis() + ".png";
            File output = new File(folder, fileName);

            ImageIO.write(img, "png", output);
            System.out.println("📸 Сохранил debug-скрин: " + output.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void saveLog(String text) {
        try {
            File folder = new File(DEBUG_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            File logFile = new File(folder, "fishing_log.txt");
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            fw.write(System.currentTimeMillis() + ": " + text + "\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
