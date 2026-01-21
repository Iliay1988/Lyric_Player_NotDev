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

    // --- НАСТРОЙКИ ШРИФТА ---
    // Можно заменить на "Montserrat", "Inter", "Roboto", если они установлены в системе.
    // "Trebuchet MS" или "Verdana" обычно выглядят мягче стандартного Segoe.
    private static final String FONT_NAME = "Trebuchet MS";

    private String prevLine = "";
    private String currentLine = "Ожидание...";
    private String nextLine = "";

    private float animationProgress = 1.0f;
    private final Timer animTimer;

    public LyricOverlay() {
        frame = new JFrame("Lyrics Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 210);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);

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
        artLabel.setPreferredSize(new Dimension(135, 135));
        artLabel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 0));

        JPanel lyricsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;

                // МАКСИМАЛЬНОЕ СГЛАЖИВАНИЕ
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

                int w = getWidth();
                int centerY = getHeight() / 2 - 5;
                int centerX = w / 2;

                // Плавная Sine-интерполяция
                float smoothT = (float) (1.0 - Math.cos(animationProgress * Math.PI)) / 2f;

                int lineGap = 42;
                int moveDist = 42;
                int shiftY = (int) ((1.0f - smoothT) * moveDist);

                // 1. Прошедшая строка (уходит вверх)
                drawLyricLine(g2, prevLine, centerX, centerY - lineGap - shiftY, 18, 0.35f * smoothT);

                // 2. ТЕКУЩАЯ строка (в центре)
                int curSize = (int) (22 + (8 * smoothT)); // Увеличение с 22 до 30
                drawLyricLine(g2, currentLine, centerX, centerY - shiftY, curSize, Math.max(0.1f, smoothT));

                // 3. Следующая строка (поднимается)
                drawLyricLine(g2, nextLine, centerX, centerY + lineGap - shiftY, 18, 0.45f * smoothT);
            }
        };
        lyricsPanel.setOpaque(false);

        animTimer = new Timer(15, e -> {
            if (animationProgress < 1.0f) {
                animationProgress += 0.045f;
                if (animationProgress > 1.0f) animationProgress = 1.0f;
                lyricsPanel.repaint();
            }
        });
        animTimer.start();

        // Прогресс бар
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 40, 15, 40));

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(0, 5));
        progressBar.setForeground(new Color(30, 215, 96));
        progressBar.setBackground(new Color(255, 255, 255, 35));
        progressBar.setBorderPainted(false);

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(new Font(FONT_NAME, Font.BOLD, 13));
        timeLabel.setForeground(new Color(220, 220, 220));
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

        // Используем выбранный шрифт
        g2.setFont(new Font(FONT_NAME, Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x - (fm.stringWidth(text) / 2);

        // РИСУЕМ МЯГКУЮ ТЕНЬ (Double Shadow)
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 130)));
        g2.drawString(text, textX + 2, y + 2);
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 80)));
        g2.drawString(text, textX - 1, y - 1);

        // ОСНОВНОЙ ТЕКСТ
        g2.setColor(new Color(1f, 1f, 1f, alpha));
        g2.drawString(text, textX, y);
    }

    public void updateLyrics(String prev, String curr, String next) {
        if (this.currentLine.equals(curr)) return;
        SwingUtilities.invokeLater(() -> {
            this.prevLine = prev;
            this.currentLine = curr;
            this.nextLine = next;
            this.animationProgress = 0.0f;
        });
    }

    public void updateArt(byte[] artBytes) {
        SwingUtilities.invokeLater(() -> {
            artLabel.setIcon(AlbumArtHandler.createIcon(artBytes, 135));
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