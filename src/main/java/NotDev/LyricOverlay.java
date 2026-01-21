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
        frame.setSize(950, 200);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                // Рисуем фон только если он есть
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
                int centerY = getHeight() / 2 - 5;
                int centerX = w / 2;

                // Используем Sine интерполяцию для максимальной плавности
                float smoothT = (float) (1.0 - Math.cos(animationProgress * Math.PI)) / 2f;

                int lineGap = 38;
                int moveDist = 38;
                int shiftY = (int) ((1.0f - smoothT) * moveDist);

                // Прошедшая строка
                drawLyricLine(g2, prevLine, centerX, centerY - lineGap - shiftY, 17, 0.4f * smoothT);
                // Текущая строка
                int curSize = (int) (20 + (6 * smoothT));
                drawLyricLine(g2, currentLine, centerX, centerY - shiftY, curSize, Math.max(0.1f, smoothT));
                // Будущая строка
                drawLyricLine(g2, nextLine, centerX, centerY + lineGap - shiftY, 17, 0.5f * smoothT);
            }
        };
        lyricsPanel.setOpaque(false);

        // Таймер анимации
        animTimer = new Timer(15, e -> {
            if (animationProgress < 1.0f) {
                animationProgress += 0.05f;
                if (animationProgress > 1.0f) animationProgress = 1.0f;
                lyricsPanel.repaint();
            }
        });
        animTimer.start();

        // Прогресс бар
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
        timeLabel.setForeground(Color.WHITE);
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
        g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x - (fm.stringWidth(text) / 2);
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 160)));
        g2.drawString(text, textX + 1, y + 1);
        g2.setColor(new Color(1f, 1f, 1f, alpha));
        g2.drawString(text, textX, y);
    }

    // ВАЖНО: проверка, изменилась ли строка
    public void updateLyrics(String prev, String curr, String next) {
        if (this.currentLine.equals(curr)) return; // Если строка та же, не сбрасываем анимацию!

        SwingUtilities.invokeLater(() -> {
            this.prevLine = prev;
            this.currentLine = curr;
            this.nextLine = next;
            this.animationProgress = 0.0f; // Начинаем движение
        });
    }

    public void updateArt(byte[] artBytes) {
        SwingUtilities.invokeLater(() -> {
            ImageIcon icon = AlbumArtHandler.createIcon(artBytes, 130);
            artLabel.setIcon(icon);
            BufferedImage newBg = AlbumArtHandler.createBlurredBackground(artBytes, frame.getWidth(), frame.getHeight());
            if (newBg != null) {
                this.backgroundImage = newBg;
            }
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