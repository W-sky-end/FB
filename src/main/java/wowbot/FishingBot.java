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
import java.io.File;
import java.util.*;
import java.util.List;

public class FishingBot {

    static {
        System.load("C:\\Users\\aonyk\\Downloads\\opencv\\build\\java\\x64\\opencv_java454.dll");
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        Random random = new Random();
        String bobberFolderPath = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/bobbers/";
        List<Mat> splashTemplates = loadBobberTemplatesFromFolder("C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/splashes/");

        List<Mat> templates = loadBobberTemplatesFromFolder(bobberFolderPath);
        if (templates.isEmpty()) {
            System.err.println("Не удалось загрузить ни одного шаблона поплавка из: " + bobberFolderPath);
            return;
        }

        Rectangle fishingArea = new Rectangle(426, 160, 940, 400); // 🔴 красная рамка

        while (true) {
            long startTime = System.currentTimeMillis();
            Point bobberPoint = null;

            while (System.currentTimeMillis() - startTime < 10_000 && bobberPoint == null) {
                Thread.sleep(500 + random.nextInt(700));
                BufferedImage screen = robot.createScreenCapture(fishingArea);
                Mat screenMat = bufferedImageToMat(screen);
                Mat filtered = filterWaterColor(screenMat);

                bobberPoint = findBobber(filtered, templates, fishingArea);
            }

            if (bobberPoint == null) {
                System.out.println("Поплавок не найден за 10 секунд. Закидываю удочку заново клавишей E...");
                robot.keyPress(KeyEvent.VK_E);
                robot.keyRelease(KeyEvent.VK_E);
                Thread.sleep(10000 + random.nextInt(2000));
                continue;
            }

            System.out.println("Поплавок найден в: " + bobberPoint);
            Thread.sleep(800 + random.nextInt(500));

            boolean bite = detectRealBite(robot, bobberPoint, splashTemplates);

            if (bite) {
                if (random.nextInt(10) == 0) {
                    System.out.println("Задумался.");
                    Thread.sleep(889 + random.nextInt(1250));
                }
                if (random.nextInt(15) == 0) {
                    System.out.println("Пропускаю клёв.");
                } else {
                    System.out.println("Клёв! Выполняю подсечку...");
                    java.awt.Point currentMouse = MouseInfo.getPointerInfo().getLocation();
                    smoothMouseMove(robot,
                            (int) currentMouse.getX(), (int) currentMouse.getY(),
                            (int) bobberPoint.x, (int) bobberPoint.y,
                            20, 5, 10);
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                    Thread.sleep(2494 + random.nextInt(1400));
                }
            } else {
                System.out.println("Перезакидываю удочку (клёва не было)...");
            }

            System.out.println("Забрасываю удочку ...");
            robot.keyPress(KeyEvent.VK_E);
            robot.keyRelease(KeyEvent.VK_E);
            Thread.sleep(3000 + random.nextInt(2000));
        }
    }

    public static List<Mat> loadBobberTemplatesFromFolder(String folderPath) {
        List<Mat> templates = new ArrayList<>();
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Папка с шаблонами не найдена: " + folderPath);
            return templates;
        }

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (file.getName().toLowerCase().endsWith(".png")) {
                Mat template = Imgcodecs.imread(file.getAbsolutePath());
                if (!template.empty()) {
                    templates.add(template);
                    System.out.println("Загружен шаблон: " + file.getName());
                }
            }
        }
        return templates;
    }

    // 🔹 Улучшенный поиск поплавка с масштабированием (5 размеров)
    public static Point findBobber(Mat screen, List<Mat> templates, Rectangle offset) {
        double bestMatchVal = 0;
        Point bestPoint = null;

        // коэффициенты масштабирования
        double[] scales = {0.6, 0.8, 1.0, 1.2, 1.4};

        for (Mat template : templates) {
            for (double scale : scales) {
                Mat resized = new Mat();
                Size newSize = new Size(template.width() * scale, template.height() * scale);
                Imgproc.resize(template, resized, newSize);

                int resultCols = screen.cols() - resized.cols() + 1;
                int resultRows = screen.rows() - resized.rows() + 1;

                if (resultCols <= 0 || resultRows <= 0) continue;

                Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
                Imgproc.matchTemplate(screen, resized, result, Imgproc.TM_CCOEFF_NORMED);

                Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

                if (mmr.maxVal > bestMatchVal && mmr.maxVal >= 0.33) {
                    bestMatchVal = mmr.maxVal;
                    bestPoint = new Point(
                            mmr.maxLoc.x + resized.width() / 2.0 + offset.x,
                            mmr.maxLoc.y + resized.height() / 2.0 + offset.y
                    );
                }

                resized.release();
                result.release();
            }
        }

        if (bestPoint != null) {
            System.out.println("🎯 Поплавок найден! Совпадение: " + bestMatchVal);
        }

        return bestPoint;
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

    public static boolean detectRealBite(Robot robot, Point bobberPoint, List<Mat> splashTemplates) throws InterruptedException {
        Rectangle splashZone = new Rectangle((int) bobberPoint.x - 40, (int) bobberPoint.y - 40, 80, 80);

        Mat prevFrame = bufferedImageToMat(robot.createScreenCapture(splashZone));
        Mat grayPrev = new Mat();
        Imgproc.cvtColor(prevFrame, grayPrev, Imgproc.COLOR_BGR2GRAY);

        LinkedList<Integer> history = new LinkedList<>();
        long startTime = System.currentTimeMillis();
        int zeroStreak = 0;

        while (System.currentTimeMillis() - startTime < 20000) {
            Thread.sleep(150);

            Mat currentFrame = bufferedImageToMat(robot.createScreenCapture(splashZone));
            Mat grayCurrent = new Mat();
            Imgproc.cvtColor(currentFrame, grayCurrent, Imgproc.COLOR_BGR2GRAY);

            // 🔹 1. Проверка по шаблонам всплесков
            if (detectSplash(currentFrame, splashTemplates, 0.35)) {
                System.out.println("🎣 КЛЁВ !!! (по шаблону всплесков)");
                Thread.sleep(800 + new Random().nextInt(400));
                return true;
            }

            // 🔹 2. Старый способ (разница кадров)
            Mat diff = new Mat();
            Core.absdiff(grayPrev, grayCurrent, diff);

            Scalar mean = Core.mean(diff);
            double dynamicThreshold = Math.max(15, mean.val[0] * 2.0);
            Imgproc.threshold(diff, diff, dynamicThreshold, 255, Imgproc.THRESH_BINARY);

            Imgproc.morphologyEx(diff, diff, Imgproc.MORPH_OPEN,
                    Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));

            int nonZeroCount = Core.countNonZero(diff);
            history.add(nonZeroCount);
            if (history.size() > 6) history.removeFirst();

            System.out.println("Всплесков: " + nonZeroCount);

            if (nonZeroCount == 0) {
                zeroStreak++;
                if (zeroStreak >= 50) {
                    System.out.println("❌ Поплавок затих — перезакидываю...");
                    return false;
                }
            } else {
                zeroStreak = 0;
            }

            if (history.size() >= 3) {
                int avg = (history.get(0) + history.get(1) + history.get(2)) / 3;
                int now = history.getLast();

                if (now > avg * 2 && now > 230) {
                    System.out.println("🎣 КЛЁВ !!! (по разнице кадров)");
                    Thread.sleep(1000 + new Random().nextInt(200));
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

    public static void smoothMouseMove(Robot robot, int startX, int startY, int endX, int endY, int steps,
                                       int minDelay, int maxDelay) throws InterruptedException {
        double dx = (double) (endX - startX) / steps;
        double dy = (double) (endY - startY) / steps;

        Random random = new Random();

        for (int i = 1; i <= steps; i++) {
            int x = (int) (startX + dx * i);
            int y = (int) (startY + dy * i);
            robot.mouseMove(x, y);
            Thread.sleep(minDelay + random.nextInt(maxDelay - minDelay + 1));
        }
    }

    public static boolean detectSplash(Mat frame, List<Mat> splashTemplates, double threshold) {
        for (Mat template : splashTemplates) {
            int resultCols = frame.cols() - template.cols() + 1;
            int resultRows = frame.rows() - template.rows() + 1;

            if (resultCols <= 0 || resultRows <= 0) continue;

            Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
            Imgproc.matchTemplate(frame, template, result, Imgproc.TM_CCOEFF_NORMED);

            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            if (mmr.maxVal >= threshold) {
                return true;
            }
        }
        return false;
    }
}
