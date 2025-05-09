package wowbot;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class FishingBot {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Бот запущен. Поиск поплавка...");

        Robot robot = new Robot();
        String templatePath = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/bober.png";

        Mat template = Imgcodecs.imread(templatePath);
        if (template.empty()) {
            System.err.println("Не удалось загрузить шаблон: " + templatePath);
            return;
        }

        while (true) {
            BufferedImage screen = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            File screenFile = new File("screen.png");
            ImageIO.write(screen, "png", screenFile);

            Mat screenMat = Imgcodecs.imread("screen.png");

            Point bobberPoint = matchTemplate(screenMat, template);

            if (bobberPoint != null) {
                System.out.println("Поплавок найден в: " + bobberPoint);
                robot.mouseMove((int) bobberPoint.x, (int) bobberPoint.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                System.out.println("Подсечка выполнена!");
                Thread.sleep(5000);
            } else {
                System.out.println("Поплавок не найден. Повтор через 2 секунды...");
                Thread.sleep(2000);
            }
        }
    }

    public static Point matchTemplate(Mat screen, Mat template) {
        int resultCols = screen.cols() - template.cols() + 1;
        int resultRows = screen.rows() - template.rows() + 1;

        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
        Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED);
        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

        if (mmr.maxVal >= 0.5) { // Чем выше, тем лучше совпадение
            return new Point(mmr.maxLoc.x + template.width() / 2, mmr.maxLoc.y + template.height() / 2);
        }

        return null;
    }
}
