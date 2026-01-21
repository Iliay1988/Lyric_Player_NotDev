package NotDev;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LyricOverlay {
    private final JFrame frame;
    private final JLabel artLabel;
    private final JProgressBar progressBar;
    private final JLabel timeLabel;
    private BufferedImage backgroundImage;

    private String prevLine = "";
    private String currentLine = "Ожидание...";
    private String nextLine = "";

    private float animationProgress = 1.0f;
    private final Timer animTimer;

    public LyricOverlay() {
        frame = new JFrame("Lyrics Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 200); // Сделали окно чуть ниже и компактнее
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setUndecorated(false); // Можно поставить true для окна без рамок

        JPanel mainPanel = new JPanel(new BorderLayout(15, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                super.paintComponent(g);
            }
        };
        mainPanel.setOpaque(false);
        frame.setContentPane(mainPanel);

        artLabel = new JLabel();
        artLabel.setPreferredSize(new Dimension(130, 130));
        artLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 0));

        JPanel lyricsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int centerY = getHeight() / 2 - 10;
                int centerX = w / 2;

                // Функция плавности (Smoothstep)
                float t = animationProgress;
                float smoothT = t * t * (3 - 2 * t);

                // Настройки компактности
                int lineGap = 35; // Уменьшенное расстояние между строк
                int moveDist = 35; // На сколько пикселей сдвигается текст

                int shiftY = (int) ((1.0f - smoothT) * moveDist);

                // 1. Прошедшая строка (уплывает и исчезает)
                drawLyricLine(g2, prevLine, centerX, centerY - lineGap - shiftY, 17, 0.3f * smoothT);

                // 2. Текущая строка (всплывает, увеличивается, становится яркой)
                int curSize = (int) (19 + (7 * smoothT)); // Плавный рост шрифта
                drawLyricLine(g2, currentLine, centerX, centerY - shiftY, curSize, smoothT);

                // 3. Следующая строка (готовится снизу)
                drawLyricLine(g2, nextLine, centerX, centerY + lineGap - shiftY, 17, 0.4f * smoothT);
            }
        };
        lyricsPanel.setOpaque(false);

        // Увеличили частоту таймера для идеальной плавности (100 FPS)
        animTimer = new Timer(10, e -> {
            if (animationProgress < 1.0f) {
                animationProgress += 0.04f; // Чуть медленнее для "тягучести"
                if (animationProgress > 1.0f) animationProgress = 1.0f;
                lyricsPanel.repaint();
            }
        });
        animTimer.start();

        // Компактный низ
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 2));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 10, 30));

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(0, 4));
        progressBar.setForeground(new Color(30, 215, 96));
        progressBar.setBackground(new Color(255, 255, 255, 30));
        progressBar.setBorderPainted(false);

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        timeLabel.setForeground(new Color(200, 200, 200));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(timeLabel, BorderLayout.SOUTH);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(lyricsPanel, BorderLayout.CENTER);
        centerContainer.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(artLabel, BorderLayout.WEST);
        frame.add(centerContainer, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void drawLyricLine(Graphics2D g2, String text, int x, int y, int fontSize, float alpha) {
        if (text == null || text.isEmpty()) return;

        // Авто-уменьшение шрифта для длинных строк
        if (text.length() > 45) fontSize -= 4;

        g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x - (fm.stringWidth(text) / 2);

        // Рисуем мягкую тень
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 180)));
        g2.drawString(text, textX + 1, y + 1);

        // Рисуем основной текст
        g2.setColor(new Color(1f, 1f, 1f, alpha));
        g2.drawString(text, textX, y);
    }

    public void updateLyrics(String prev, String curr, String next) {
        SwingUtilities.invokeLater(() -> {
            this.prevLine = prev;
            this.currentLine = curr;
            this.nextLine = next;
            this.animationProgress = 0.0f;
        });
    }

    public void updateArt(byte[] artBytes) {
        SwingUtilities.invokeLater(() -> {
            artLabel.setIcon(AlbumArtHandler.createIcon(artBytes, 130));
            this.backgroundImage = AlbumArtHandler.createBlurredBackground(artBytes, frame.getWidth(), frame.getHeight());
            frame.repaint();
        });
    }

    public void updateProgress(long currentSec, long totalSec) {
        SwingUtilities.invokeLater(() -> {
            if (totalSec > 0) {
                progressBar.setValue((int) ((currentSec * 100) / totalSec));
                timeLabel.setText(String.format("%02d:%02d / %02d:%02d", currentSec/60, currentSec%60, totalSec/60, totalSec%60));
            }
        });
    }
}