package wowbot;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.LinkedList;
import java.util.Random;

import static java.lang.Thread.sleep;


public class FishingBot {

    static {
        System.load("C:\\Users\\aonyk\\Downloads\\opencv\\build\\java\\x64\\opencv_java454.dll");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("FishingBot  запущен!");

        Robot robot = new Robot();
        Random random = new Random();
        String templatePath = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/bobber.png";

        Mat template = Imgcodecs.imread(templatePath);
        if (template.empty()) {
            System.err.println("Не удалось загрузить шаблон: " + templatePath);
            return;
        }
        boolean running = true;

        while (running) {
            long startTime = System.currentTimeMillis();
            Point bobberPoint = null;

            while (System.currentTimeMillis() - startTime < 10_000 && bobberPoint == null) {
                Thread.sleep(500 + random.nextInt(700)); //
                BufferedImage screen = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                Mat screenMat = bufferedImageToMat(screen);
                Mat filtered = filterWaterColor(screenMat);

                bobberPoint = findBobber(filtered, template);

                if (bobberPoint != null) {
                    System.out.println("Поплавок найден в: " + bobberPoint);
                    Thread.sleep(800 + random.nextInt(500)); //todo рандом перед нахождением поплавка(человеческий фактор)

                    if (detectRealBite(robot, bobberPoint)) {
                        if (random.nextInt(10) == 0) {
                            System.out.println("Задумался.");
                            Thread.sleep(889 + random.nextInt(1250)); // задумался
                        }
                        if (random.nextInt(15) == 0) {
                            System.out.println("Пропускаю клёв ."); // отвлекся
                        } else {
                            System.out.println("Клёв! Выполняю подсечку...");
                            robot.mouseMove((int) bobberPoint.x, (int) bobberPoint.y);
                            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK); // todo Button1 & Button3 (Актуал/Ката)
                            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                            Thread.sleep(2494 + random.nextInt(1400)); //заброс
                        }
                        if (random.nextInt(20) == 0) {
                            System.out.println("Чуть-чуть двигаю камерой как игрок...");
                            robot.keyPress(KeyEvent.VK_RIGHT); // кнопка движения камеры
                            Thread.sleep(100 + random.nextInt(300));
                            robot.keyRelease(KeyEvent.VK_RIGHT);
                        }

                        System.out.println("Забрасываю удочку ...");
                        robot.keyPress(KeyEvent.VK_E);
                        robot.keyRelease(KeyEvent.VK_E);
                        Thread.sleep(3000 + new Random().nextInt(2000));// todo Пауза перед сканом
                    }
                } else {

                    System.out.println("Поплавок не найден за 10 секунд. Закидываю удочку заново клавишей E...");
                    robot.keyPress(KeyEvent.VK_E);
                    robot.keyRelease(KeyEvent.VK_E);
                    Thread.sleep(4000 + random.nextInt(2000));
                }
            }
        }
    }

    public static Mat filterWaterColor(Mat image) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        Scalar lower = new Scalar(80, 50, 50);
        Scalar upper = new Scalar(140, 255, 255);

        Mat mask = new Mat();
        Core.inRange(hsv, lower, upper, mask);

        Mat result = new Mat();
        Core.bitwise_not(mask, mask);
        image.copyTo(result, mask);

        return result;
    }

    public static Point findBobber(Mat screen, Mat template) {
        int resultCols = screen.cols() - template.cols() + 1;
        int resultRows = screen.rows() - template.rows() + 1;

        if (resultCols <= 0 || resultRows <= 0) return null;

        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
        Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED);


        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

        if (mmr.maxVal >= 0.5) {
            return new Point(mmr.maxLoc.x + (double) template.width() / 2,
                    mmr.maxLoc.y + (double) template.height() / 2);
        }

        return null;
    }

    public static boolean detectRealBite(Robot robot, Point bobberPoint) throws InterruptedException {
        Rectangle splashZone = new Rectangle(
                (int) bobberPoint.x - 40,
                (int) bobberPoint.y - 40,
                80, 80
        );

        Mat prevFrame = bufferedImageToMat(robot.createScreenCapture(splashZone));
        Mat grayPrev = new Mat();
        Imgproc.cvtColor(prevFrame, grayPrev, Imgproc.COLOR_BGR2GRAY);

        LinkedList<Integer> history = new LinkedList<>();

        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < 7000) {
            sleep(150);

            Mat currentFrame = bufferedImageToMat(robot.createScreenCapture(splashZone));
            Mat grayCurrent = new Mat();
            Imgproc.cvtColor(currentFrame, grayCurrent, Imgproc.COLOR_BGR2GRAY);

            Mat diff = new Mat();
            Core.absdiff(grayPrev, grayCurrent, diff);

            Imgproc.threshold(diff, diff, 30, 255, Imgproc.THRESH_BINARY);
            int nonZeroCount = Core.countNonZero(diff);

            history.add(nonZeroCount);
            if (history.size() > 5) history.removeFirst();

            System.out.println("Всплесков: " + nonZeroCount);

            if (history.size() >= 3) {
                int avgBefore = (history.get(0) + history.get(1)) / 2;
                int now = history.getLast();

                if (now > avgBefore * 2 && now > 660) { // TODO подгонять под локацию
                    System.out.println(" КЛЁВ !!! Всплесков: " + now);
                    return true;
                }
            }

            grayPrev = grayCurrent.clone();
        }

        System.out.println("Клёва не было");
        return false;
    }


    public static Mat bufferedImageToMat(BufferedImage bi) {
        if (bi.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage convertedImg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g2d = convertedImg.createGraphics();
            g2d.drawImage(bi, 0, 0, null);
            g2d.dispose();
            bi = convertedImg;
        }

        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }
}
