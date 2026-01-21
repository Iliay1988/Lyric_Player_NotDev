package NotDev;

import javax.swing.*;
import java.awt.*;

public class LyricOverlay {
    private final JFrame frame;
    private final JLabel label;

    public LyricOverlay() {
        frame = new JFrame("Lyrics Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Color.BLACK); // Черный фон

        label = new JLabel("Ожидание музыки...", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 24));
        label.setForeground(Color.WHITE); // Белый текст

        frame.add(label, BorderLayout.CENTER);
        frame.setSize(800, 200);
        frame.setLocationRelativeTo(null); // Центрировать при запуске
        frame.setAlwaysOnTop(true); // Поверх всех окон
        frame.setVisible(true);
    }

    public void updateText(String text) {
        SwingUtilities.invokeLater(() -> {
            // HTML позволяет тексту переноситься на новую строку, если он длинный
            label.setText("<html><div style='text-align: center; width: 700px;'>" + text + "</div></html>");
        });
    }
}