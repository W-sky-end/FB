package wowbot;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.*;
import java.util.List;

import static java.lang.Thread.sleep;

public class FishingBot {

    /* =============================  CONFIG  ============================= */
    //YOLO
    private static final String YOLO_BOBBER_PATH = "C:\\Users\\aonyk\\wskyprjct\\Lessons\\FB\\src\\main\\resources\\yolo\\bobber.onnx";
    private static final float YOLO_CONFIDENCE = 0.65f;
    private static Net bobberNet;
    private static final String YOLO_BITE_PATH = "C:\\Users\\aonyk\\wskyprjct\\Lessons\\FB\\src\\main\\resources\\yolo\\bite.onnx";
    private static Net biteNet;

    // OpenCV
    private static final String OPENCV_DLL_PATH = "C:\\Users\\aonyk\\wskyprjct\\Lessons\\FB\\src\\main\\resources\\opencv\\build\\java\\x64\\opencv_java480.dll";
    // Папки ресурсовe
    private static final String BOBBER_FOLDER = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/bobbers/";
    private static final String SPLASH_FOLDER = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/splashes/";

    // Область поиска поплавка (x, y, width, height)
    private static final Rectangle FISHING_AREA = new Rectangle(550, 300, 700, 450);
    // Пороговые значенияr
    private static final double BOBBER_THRESHOLD = 0.4; // чувствительность поиска поплавка
    private static final double SPLASH_THRESHOLD = 0.575; // чувстreeвительносeть распознавания всплеска         //595
    private static final double DIFF_MULTIPLIER = 1.9;   // множитель динамического порога (разница кадров)   //1.9
    private static final int DIFF_MIN_PIXELS = 307;//минимум "белых" пикселей для фиксации клёва// 307

    // Масштабы для matchemplate (под разные дистанции)
    private static final double[] TEMPLATE_SCALES = {0.3, 0.5, 0.7, 0.9, 1.0, 1.1, 1.3, 1.5, 1.7};

    // История и анти-залипание (для diff)к
    private static final int HISTORY_LEN = 6;    // длина истории noenZeroCounter
    private static final int ZERO_STREAK_LIMIT = 100;  // скоклько подряд нулей до перезакида

    // Тайминги
    private static final int SEARCH_BOBBER_TIMEOUT = 10_000;  // макс. время поиска поплавка
    private static final int CAST_DELAY_MIN = 3_000;   // задержка после заброса (min)
    private static final int CAST_DELAY_MAX = 5_000;   // задержка после заброса (max)r
    private static final int CHECK_INTERVAL = 70;      // период опроса (мс)
    private static final int REACTION_DELAY = 100;     // микрозадержка перед подсечкой
    private static final int FAIL_SAFE_TIMEOUT = 20_000;  // потолок ожидания клёва (мс)

    // Фильтр воды (HSV)
    private static final Scalar WATER_HSV_LOWER = new Scalar(80, 50, 50);
    private static final Scalar WATER_HSV_UPPER = new Scalar(140, 255, 255);

    // Авто-приманка
    private static final boolean ENABLE_BAIT = true;
    private static final int BAIT_INTERVAL_MS = 10 * 60 * 1000; // каждые 5 минут
    private static final int BAIT_WAIT_MS = 10_000;         // ждём после клика
    private static final int BAIT_X = 720;  // ← подставь свои координаты слота приманки
    private static final int BAIT_Y = 1050; // ← подставь свои координаты слота приманки
    private static final int BAIT_KEY = KeyEvent.VK_R; // что нажать перед кликом (если нужно открыть меню)e

    /* =================================================================== */

    static {
        System.load(OPENCV_DLL_PATH);
        bobberNet = Dnn.readNetFromONNX(YOLO_BOBBER_PATH);
        biteNet = Dnn.readNetFromONNX(YOLO_BITE_PATH);
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        Random rnd = new Random();

        List<Mat> bobberTemplates = loadTemplatesFromFolder(BOBBER_FOLDER);
        List<Mat> splashTemplates = loadTemplatesFromFolder(SPLASH_FOLDER);

        if (bobberTemplates.isEmpty()) {
            System.err.println("❌ Нет шаблонов поплавка в: " + BOBBER_FOLDER);
            return;
        }

        long lastBait = System.currentTimeMillis() - BAIT_INTERVAL_MS; // чтобы сработало сразу при старте, если нужно

        while (true) {

            // 🔹 Авто-приманка
            if (ENABLE_BAIT && System.currentTimeMillis() - lastBait >= BAIT_INTERVAL_MS) {
                applyBait(robot);
                lastBait = System.currentTimeMillis();
            }

            // 🔹 Поиск поплавка
            long start = System.currentTimeMillis();
            Point bobberPoint = null;

            while (System.currentTimeMillis() - start < SEARCH_BOBBER_TIMEOUT && bobberPoint == null) {
                sleep(500 + rnd.nextInt(700));
                BufferedImage screen = robot.createScreenCapture(FISHING_AREA);
                //старый метод ===================================================================================================================================================eууer
//                Mat screenMat = bufferedImageToMat(screen);
//                Mat filtered = filterWaterColor(screenMat);
//                bobberPoint = findBobber(filtered, bobberTemplates, FISHING_AREA);
                //новы метод
               bobberPoint = findBobberWithYolo(screen);
            }

            if (bobberPoint == null) {
                System.out.println("⚠️ Поплавок не найден. Перезакидываю (E)...");
                pressKey(robot, KeyEvent.VK_E);
                sleep(10_000 + rnd.nextInt(2_000));
                continue;
            }
            System.out.println("🎯 Поплавок: " + bobberPoint);
            sleep(800 + rnd.nextInt(500));

            // 🔹 Ожидание клёва
            boolean bite = detectRealBite(robot, bobberPoint, splashTemplates);

            if (bite) {
                if (rnd.nextInt(10) == 0) {
                    sleep(889 + rnd.nextInt(1250));
                }
                if (rnd.nextInt(15) != 0) {
                    System.out.println("✅ Подсечка!");
                    java.awt.Point cur = MouseInfo.getPointerInfo().getLocation();
                    smoothMouseMove(robot, (int) cur.getX(), (int) cur.getY(), (int) bobberPoint.x, (int) bobberPoint.y, 20, 5, 10);
                    rightClick(robot);
                    sleep(2_494 + rnd.nextInt(1_400));
                } else {
                    System.out.println("🙃 Осознанно пропустил клёв.");
                }
            } else {
                System.out.println("♻️ Клёва нет — перезакид.");
            }

            // 🔹 Новый заброс
            System.out.println("🎣 Заброс (E)...");
            pressKey(robot, KeyEvent.VK_E);
            sleep(CAST_DELAY_MIN + rnd.nextInt(CAST_DELAY_MAX - CAST_DELAY_MIN));
        }
    }

    /* ========================  Приманка  ======================== */
    private static void applyBait(Robot robot) throws InterruptedException {
        System.out.println("🪝 Обновляю приманку...");
        if (BAIT_KEY != -1) {
            pressKey(robot, BAIT_KEY);
            sleep(400);
        }
        robot.mouseMove(BAIT_X, BAIT_Y);
        leftClick(robot);
        sleep(BAIT_WAIT_MS);
        System.out.println("🟢 Приманка активна.");
    }

    /* =====================  Шаблоны/Поиск  ===================== */

    private static List<Mat> loadTemplatesFromFolder(String folderPath) {
        List<Mat> list = new ArrayList<>();
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return list;

        for (File f : Objects.requireNonNull(folder.listFiles())) {
            if (f.getName().toLowerCase().endsWith(".png")) {
                Mat t = Imgcodecs.imread(f.getAbsolutePath());
                if (!t.empty()) list.add(t);
            }
        }
        System.out.println("📦 Загрузил " + list.size() + " шаблон(ов) из " + folderPath);
        return list;
    }

    public static Point findBobber(Mat screen, List<Mat> templates, Rectangle offset) {
        double bestMatchVal = 0;
        Point bestPoint = null;
        List<MatchCandidate> candidates = new ArrayList<>();
        double[] scales = {0.5, 0.7, 0.9, 1.0, 1.1, 1.3, 1.5};

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
                if (mmr.maxVal >= BOBBER_THRESHOLD) {
                    Point point = new Point(
                            mmr.maxLoc.x + resized.width() / 2.0 + offset.x,
                            mmr.maxLoc.y + resized.height() / 2.0 + offset.y
                    );
                    candidates.add(new MatchCandidate(point, mmr.maxVal));
                }
                resized.release();
                result.release();
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));

        if (!candidates.isEmpty()) {
            bestPoint = candidates.get(0).point;
            bestMatchVal = candidates.get(0).score;
            System.out.println("🎯 Поплавок совпадение: " + bestMatchVal);

        }

        return bestPoint;
    }

    private static class MatchCandidate {
        Point point;
        double score;

        MatchCandidate(Point p, double s) {
            point = p;
            score = s;
        }
    }

    /* =====================  Клёв (детект)  ===================== */

    private static boolean detectRealBite(Robot robot, Point bobberPoint, List<Mat> splashTemplates) throws InterruptedException {
        Rectangle zone = new Rectangle((int) bobberPoint.x - 40, (int) bobberPoint.y - 40, 80, 80);

        Mat prev = bufferedImageToMat(robot.createScreenCapture(zone));
        Mat grayPrev = new Mat();
        Imgproc.cvtColor(prev, grayPrev, Imgproc.COLOR_BGR2GRAY);

        LinkedList<Integer> hist = new LinkedList<>();
        long start = System.currentTimeMillis();
        int zeroStreak = 0;
        boolean bobberWasSeen = false;
        int biteCount = 0;

  //у      while (System.currentTimeMillis() - start < FAIL_SAFE_TIMEOUT) {

//            {
//                try {
//                    BufferedImage biteZone = robot.createScreenCapture(zone);
//                    ImageIO.write(biteZone, "png", new File("C:\\Users\\aonyk\\Desktop\\dataset\\auto" + System.currentTimeMillis() + ".png"));
//                } catch (Exception ignored) {}
//            }

 //           sleep(CHECK_INTERVAL);



//            Mat cur = bufferedImageToMat(robot.createScreenCapture(zone));
//            Mat grayCur = new Mat();
//            Imgproc.cvtColor(cur, grayCur, Imgproc.COLOR_BGR2GRAY);
//            //перенес с 3 для елки
//            Mat diff = new Mat();
//            Core.absdiff(grayPrev, grayCur, diff);
//            Scalar mean = Core.mean(diff);
//            double thr = Math.max(15, mean.val[0] * DIFF_MULTIPLIER);
//            Imgproc.threshold(diff, diff, thr, 255, Imgproc.THRESH_BINARY);
//            int nonZero = Core.countNonZero(diff);
//
//            // 1)Елка
//
//            if (nonZero > 300) {
//                BufferedImage zoneScreen = robot.createScreenCapture(zone);
//                int biteResult = detectBiteWithYolo(zoneScreen);
//                if (biteResult == 1) {
//                    System.out.println("💥 КЛЁВ (YOLO bite)!");
//                    Thread.sleep(REACTION_DELAY);
//                    return true;
//                }
//            }
//
//
//
//            // 2) По шаблонам всплесков
//            if (detectSplash(cur, splashTemplates, SPLASH_THRESHOLD)) {
//                System.out.println("💥 КЛЁВ (шаблон)!");
//
//                //скрин
////                try {
////                    BufferedImage biteZone = robot.createScreenCapture(zone);
////                    ImageIO.write(biteZone, "png", new File("C:\\Users\\aonyk\\Desktop\\dataset\\bite\\bite" + System.currentTimeMillis() + ".png"));
////                } catch (Exception ignored) {}
////                sleep(REACTION_DELAY);
////                return true;
//           }
//
//            // 3) По движению
//
//
//            System.out.println("В: " + nonZero);
//
//            hist.add(nonZero);
//            if (hist.size() > HISTORY_LEN) hist.removeFirst();
//
//            if (nonZero == 0) {
//                if (++zeroStreak >= ZERO_STREAK_LIMIT) {
//                    System.out.println("😴 Тишина (нулями) — выходим.");
//                    return false;
//                }
//            } else zeroStreak = 0;
//
//            if (hist.size() >= 3) {
//                int avg = (hist.get(0) + hist.get(1) + hist.get(2)) / 3;
//                int now = hist.getLast();
//                if (now > avg * 2 && now > DIFF_MIN_PIXELS) {
//                    System.out.println("💥 КЛЁВ (движение)!");
//                    //скрин
////                    try {
////                        BufferedImage biteZone = robot.createScreenCapture(zone);
////                        ImageIO.write(biteZone, "png", new File("C:\\Users\\aonyk\\Desktop\\dataset\\bite\\" + System.currentTimeMillis() + ".png"));
////                    } catch (Exception ignored) {}
//                    sleep(REACTION_DELAY);
//                    return true;
//                }
//            }
//            grayPrev = grayCur.clone();
//
////            //3 проверка //TODO
////            Mat hsvCur = new Mat();
////            Imgproc.cvtColor(cur, hsvCur, Imgproc.COLOR_BGR2HSV);
////            Scalar meanHsv = Core.mean(hsvCur);r
////            if (meanHsv.val[1] < 70 ) {           // 3я настройка <________________________eу____________
////                System.out.println(" КЛЁВ (HSV брызги,3 проверка)!");
////                Thread.sleep(REACTION_DELAY);
////                return true;
////            }
   //     }
        // новый
        int missCount = 0;
        int seenCount = 0;
        int highCount = 0;

        while (System.currentTimeMillis() - start < FAIL_SAFE_TIMEOUT) {
            sleep(CHECK_INTERVAL);

            Mat cur = bufferedImageToMat(robot.createScreenCapture(zone));
            Mat grayCur = new Mat();
            Imgproc.cvtColor(cur, grayCur, Imgproc.COLOR_BGR2GRAY);

            Mat diff = new Mat();
            Core.absdiff(grayPrev, grayCur, diff);
            Imgproc.threshold(diff, diff, 25, 255, Imgproc.THRESH_BINARY);
            int changed = Core.countNonZero(diff);
            int total = 80 * 80;
            double ratio = (double) changed / total;

            System.out.println("Изменение: " + String.format("%.1f", ratio * 100) + "%");

            

            // bobberNet следиту
            BufferedImage zoneScreen = robot.createScreenCapture(zone);
            boolean visible = bobberVisibleInZone(zoneScreen);
            if (visible) seenCount++;

            grayPrev = grayCur.clone();

            // антизависание
            if (seenCount == 0) {
                if (++zeroStreak >= ZERO_STREAK_LIMIT) {
                    return false;
                }
            }
        }
        System.out.println("⛔ Клёва не было.");
        return false;

    }

    /* =======================  Утилиты  ======================= */

    private static Mat filterWaterColor(Mat img) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsv, WATER_HSV_LOWER, WATER_HSV_UPPER, mask);
        Mat out = new Mat();
        Core.bitwise_not(mask, mask);
        img.copyTo(out, mask);
        return out;
    }

    private static Mat bufferedImageToMat(BufferedImage bi) {
        if (bi.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage c = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = c.createGraphics();
            g.drawImage(bi, 0, 0, null);
            g.dispose();
            bi = c;
        }
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    private static boolean detectSplash(Mat frame, List<Mat> templates, double thr) {
        for (Mat t : templates) {
            int cols = frame.cols() - t.cols() + 1;
            int rows = frame.rows() - t.rows() + 1;
            if (cols <= 0 || rows <= 0) continue;
            Mat res = new Mat(rows, cols, CvType.CV_32FC1);
            Imgproc.matchTemplate(frame, t, res, Imgproc.TM_CCOEFF_NORMED);
            if (Core.minMaxLoc(res).maxVal >= thr) return true;
        }
        return false;
    }




    private static void smoothMouseMove(Robot r, int sx, int sy, int ex, int ey, int steps, int minDelay, int maxDelay) throws InterruptedException {
        double dx = (double) (ex - sx) / steps, dy = (double) (ey - sy) / steps;
        Random rnd = new Random();
        for (int i = 1; i <= steps; i++) {
            r.mouseMove((int) (sx + dx * i), (int) (sy + dy * i));
            sleep(minDelay + rnd.nextInt(Math.max(1, maxDelay - minDelay + 1)));
        }
    }

    private static void leftClick(Robot r) {
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void rightClick(Robot r) {
        r.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    private static void pressKey(Robot r, int key) {
        r.keyPress(key);
        r.keyRelease(key);
    }
    private static Point findBobberWithYolo(BufferedImage screen) {
        Mat img = bufferedImageToMat(screen);
        Mat blob = Dnn.blobFromImage(img, 1.0 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        bobberNet.setInput(blob);

        List<Mat> outputLayers = new ArrayList<>();
        bobberNet.forward(outputLayers, bobberNet.getUnconnectedOutLayersNames());

        Mat output = outputLayers.get(0);
        Mat reshaped = output.reshape(1, 5); // [5, 8400] — один класс!

        float bestConf = 0;
        Point bestPoint = null;

        for (int i = 0; i < 8400; i++) {
            float conf = (float) reshaped.get(4, i)[0];
            if (conf > YOLO_CONFIDENCE && conf > bestConf) {
                bestConf = conf;
                float cx = (float) reshaped.get(0, i)[0];
                float cy = (float) reshaped.get(1, i)[0];
                double realX = cx / 640.0 * FISHING_AREA.width + FISHING_AREA.x;
                double realY = cy / 640.0 * FISHING_AREA.height + FISHING_AREA.y;
                bestPoint = new Point(realX, realY);
            }
        }

        if (bestPoint != null) {
            System.out.println("🎯 YOLO поплавок: " + bestPoint + " conf=" + bestConf);
        }
        return bestPoint;
    }
    private static int detectBiteWithYolo(BufferedImage zone) {
        Mat img = bufferedImageToMat(zone);
        Mat blob = Dnn.blobFromImage(img, 1.0 / 255.0, new Size(640, 640), new Scalar(0), true, false);
        biteNet.setInput(blob);

        List<Mat> outputLayers = new ArrayList<>();
        biteNet.forward(outputLayers, biteNet.getUnconnectedOutLayersNames());

        Mat output = outputLayers.get(0);
        Mat reshaped = output.reshape(1, 6); // [6, 8400] — два класса!

        float bestConf = 0;
        int bestClass = -1;

        for (int i = 0; i < 8400; i++) {
            float conf0 = (float) reshaped.get(4, i)[0]; // bobber_calm
            float conf1 = (float) reshaped.get(5, i)[0]; // bobber_bite

            float maxConf = Math.max(conf0, conf1);
            if (maxConf > YOLO_CONFIDENCE && maxConf > bestConf) {
                bestConf = maxConf;
                bestClass = conf0 > conf1 ? 0 : 1;
            }
        }

        System.out.println("BITE YOLO: class=" + bestClass + " conf=" + bestConf);
        return bestClass; // 0=calm, 1=bite, -1=ничего
    }
    private static boolean bobberVisibleInZone(BufferedImage zone) {
        Mat img = bufferedImageToMat(zone);
        Mat blob = Dnn.blobFromImage(img, 1.0/255.0, new Size(640,640), new Scalar(0), true, false);
        biteNet.setInput(blob); // ← biteNet вместо bobberNet!
        List<Mat> out = new ArrayList<>();
        biteNet.forward(out, biteNet.getUnconnectedOutLayersNames());
        Mat reshaped = out.get(0).reshape(1, 6);
        for (int i = 0; i < 8400; i++) {
            float conf0 = (float) reshaped.get(4, i)[0]; // bobber_calm
            if (conf0 > 0.5f) return true; // видим поплавок спокойный
        }
        return false;
    }

}

