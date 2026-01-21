package NotDev;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LyricOverlay {
    private final JFrame frame;
    private final JLabel artLabel;
    private BufferedImage backgroundImage;

    private static final String FONT_NAME = "Verdana";

    private String prevLine = "";
    private String currentLine = "Ожидание...";
    private String nextLine = "";

    private float animationProgress = 1.0f;
    private final float ANIMATION_SPEED = 6f;

    public LyricOverlay() {
        frame = new JFrame("Lyrics Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 180); // Уменьшили высоту, так как прогресс-бара нет
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

        // Обложка
        artLabel = new JLabel();
        artLabel.setPreferredSize(new Dimension(135, 135));
        artLabel.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 0));

        // Панель текста
        JPanel lyricsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

                int w = getWidth();
                int centerY = getHeight() / 2 + 5; // Сцентрировали по вертикали
                int centerX = w / 2;

                // Плавная кривая Sine
                float smoothT = (float) (Math.sin((animationProgress - 0.5) * Math.PI) + 1.0) / 2.0f;

                int lineGap = 45;
                int moveDist = 40;
                int shiftY = (int) ((1.0f - smoothT) * moveDist);

                // Прошедшая строка
                drawLyricLine(g2, prevLine, centerX, centerY - lineGap - shiftY, 18, 0.35f * smoothT);

                // Текущая строка (Акцент)
                int curSize = (int) (22 + (8 * smoothT));
                drawLyricLine(g2, currentLine, centerX, centerY - shiftY, curSize, Math.max(0.1f, smoothT));

                // Будущая строка
                drawLyricLine(g2, nextLine, centerX, centerY + lineGap - shiftY, 18, 0.45f * smoothT);
            }
        };
        lyricsPanel.setOpaque(false);

        // Поток анимации
        new Thread(() -> {
            long lastTime = System.nanoTime();
            while (true) {
                long now = System.nanoTime();
                float deltaTime = (now - lastTime) / 1_000_000_000f;
                lastTime = now;

                if (animationProgress < 1.0f) {
                    animationProgress += deltaTime * ANIMATION_SPEED;
                    if (animationProgress > 1.0f) animationProgress = 1.0f;
                    lyricsPanel.repaint();
                }
                try { Thread.sleep(7); } catch (Exception ignored) {}
            }
        }).start();

        frame.add(artLabel, BorderLayout.WEST);
        frame.add(lyricsPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void drawLyricLine(Graphics2D g2, String text, int x, int y, int fontSize, float alpha) {
        if (text == null || text.isEmpty()) return;
        g2.setFont(new Font(FONT_NAME, Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x - (fm.stringWidth(text) / 2);

        // Мягкая тень
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 120)));
        g2.drawString(text, textX + 1, y + 1);

        g2.setColor(new Color(1f, 1f, 1f, alpha));
        g2.drawString(text, textX, y);
    }

    public void updateLyrics(String prev, String curr, String next) {
        if (this.currentLine.equals(curr)) return;
        this.prevLine = prev;
        this.currentLine = curr;
        this.nextLine = next;
        this.animationProgress = 0.0f;
    }

    public void updateArt(byte[] artBytes) {
        new Thread(() -> {
            BufferedImage newBg = AlbumArtHandler.createBlurredBackground(artBytes, frame.getWidth(), frame.getHeight());
            ImageIcon icon = AlbumArtHandler.createIcon(artBytes, 135);
            SwingUtilities.invokeLater(() -> {
                this.backgroundImage = newBg;
                this.artLabel.setIcon(icon);
                frame.repaint();
            });
        }).start();
    }
}