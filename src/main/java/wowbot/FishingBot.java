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

    /* =============================  CONFIG  ============================= */

    // OpenCV
    private static final String OPENCV_DLL_PATH = "C:\\Users\\aonyk\\Downloads\\opencv\\build\\java\\x64\\opencv_java454.dll";

    // Папки ресурсов
    private static final String BOBBER_FOLDER = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/bobbers/";
    private static final String SPLASH_FOLDER = "C://Users/aonyk/wskyprjct/Lessons/FB/src/main/resources/splashes/";

    // Область поиска поплавка (x, y, width, height)
    private static final Rectangle FISHING_AREA = new Rectangle(550, 300, 550, 350);
    // Пороговые значенияr
    private static final double BOBBER_THRESHOLD = 0.3; // чувствительность поиска поплавка
    private static final double SPLASH_THRESHOLD = 0.595; // чувстreeвительносeть распознавания всплеска         //55/60e - для хантерлендску
    private static final double DIFF_MULTIPLIER = 1.9;   // множитель динамического порога (разница кадров)   //1.9
    private static final int DIFF_MIN_PIXELS = 307;//минимум "белых" пикселей для фиксации клёва

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
                Thread.sleep(500 + rnd.nextInt(700));
                BufferedImage screen = robot.createScreenCapture(FISHING_AREA);
                Mat screenMat = bufferedImageToMat(screen);
                Mat filtered = filterWaterColor(screenMat);
                bobberPoint = findBobber(filtered, bobberTemplates, FISHING_AREA);
            }

            if (bobberPoint == null) {
                System.out.println("⚠️ Поплавок не найден. Перезакидываю (E)...");
                pressKey(robot, KeyEvent.VK_E);
                Thread.sleep(10_000 + rnd.nextInt(2_000));
                continue;
            }

            System.out.println("🎯 Поплавок: " + bobberPoint);
            Thread.sleep(800 + rnd.nextInt(500));

            // 🔹 Ожидание клёва
            boolean bite = detectRealBite(robot, bobberPoint, splashTemplates);

            if (bite) {
                if (rnd.nextInt(10) == 0) {
                    Thread.sleep(889 + rnd.nextInt(1250));
                }
                if (rnd.nextInt(15) != 0) {
                    System.out.println("✅ Подсечка!");
                    java.awt.Point cur = MouseInfo.getPointerInfo().getLocation();
                    smoothMouseMove(robot, (int) cur.getX(), (int) cur.getY(), (int) bobberPoint.x, (int) bobberPoint.y, 20, 5, 10);
                    rightClick(robot);
                    Thread.sleep(2_494 + rnd.nextInt(1_400));
                } else {
                    System.out.println("🙃 Осознанно пропустил клёв.");
                }
            } else {
                System.out.println("♻️ Клёва нет — перезакид.");
            }

            // 🔹 Новый заброс
            System.out.println("🎣 Заброс (E)...");
            pressKey(robot, KeyEvent.VK_E);
            Thread.sleep(CAST_DELAY_MIN + rnd.nextInt(CAST_DELAY_MAX - CAST_DELAY_MIN));
        }
    }

    /* ========================  Приманка  ======================== */
    private static void applyBait(Robot robot) throws InterruptedException {
        System.out.println("🪝 Обновляю приманку...");
        if (BAIT_KEY != -1) {
            pressKey(robot, BAIT_KEY);
            Thread.sleep(400);
        }
        robot.mouseMove(BAIT_X, BAIT_Y);
        leftClick(robot);
        Thread.sleep(BAIT_WAIT_MS);
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

//                        скриншот поплавкак
//            Mat debugImg = screen.clone();
//            Imgproc.circle(debugImg,
//                    new Point(bestPoint.x - offset.x, bestPoint.y - offset.y),
//                    15, new Scalar(0, 0, 255), 2);
//            String filename = "debug/bobber_candidates_" + System.currentTimeMillis() + ".png";
//            Imgcodecs.imwrite(filename, debugImg);
//            debugImg.release();
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

        while (System.currentTimeMillis() - start < FAIL_SAFE_TIMEOUT) {
            Thread.sleep(CHECK_INTERVAL);

            Mat cur = bufferedImageToMat(robot.createScreenCapture(zone));
            Mat grayCur = new Mat();
            Imgproc.cvtColor(cur, grayCur, Imgproc.COLOR_BGR2GRAY);

            // 1) По шаблонам всплесков
            if (detectSplash(cur, splashTemplates, SPLASH_THRESHOLD)) {
                System.out.println("💥 КЛЁВ (шаблон)!");

                // две строчки для скрина клева ниже
//                BufferedImage splashImg = robot.createScreenCapture(zone);
//                DebugUtils.saveImage(splashImg, "splash_template");

                Thread.sleep(REACTION_DELAY);
                return true;
            }

            // 2) По движению
            Mat diff = new Mat();
            Core.absdiff(grayPrev, grayCur, diff);
            Scalar mean = Core.mean(diff);
            double thr = Math.max(15, mean.val[0] * DIFF_MULTIPLIER);
            Imgproc.threshold(diff, diff, thr, 255, Imgproc.THRESH_BINARY);
            int nonZero = Core.countNonZero(diff);

            System.out.println("В: " + nonZero);

            hist.add(nonZero);
            if (hist.size() > HISTORY_LEN) hist.removeFirst();

            if (nonZero == 0) {
                if (++zeroStreak >= ZERO_STREAK_LIMIT) {
                    System.out.println("😴 Тишина (нулями) — выходим.");
                    return false;
                }
            } else zeroStreak = 0;

            if (hist.size() >= 3) {
                int avg = (hist.get(0) + hist.get(1) + hist.get(2)) / 3;
                int now = hist.getLast();
                if (now > avg * 2 && now > DIFF_MIN_PIXELS) {
                    System.out.println("💥 КЛЁВ (движение)!");
                    Thread.sleep(REACTION_DELAY);
                    return true;
                }
            }
            grayPrev = grayCur.clone();
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
            Thread.sleep(minDelay + rnd.nextInt(Math.max(1, maxDelay - minDelay + 1)));
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
}

