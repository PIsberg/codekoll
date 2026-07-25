package io.codekoll.loadtest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders a simple bar chart PNG with the plain AWT toolkit — no external charting library
 * (keeps the harness dependency-free). One bar per tier; used for the per-version CPU-time
 * and throughput charts written to docs/perf/.
 */
final class ChartRenderer {

  private static final int WIDTH = 720;
  private static final int HEIGHT = 360;
  private static final int MARGIN = 60;
  private static final Color BAR = new Color(0x3B, 0x82, 0xF6);
  private static final Color AXIS = new Color(0x33, 0x33, 0x33);

  private ChartRenderer() {}

  static void barChart(Path out, String title, String unit, List<String> labels,
      List<Double> values) throws IOException {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, WIDTH, HEIGHT);

    g.setColor(AXIS);
    g.setFont(new Font("SansSerif", Font.BOLD, 16));
    g.drawString(title + " (" + unit + ")", MARGIN, 30);
    g.setStroke(new BasicStroke(1.5f));
    g.drawLine(MARGIN, HEIGHT - MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN);
    g.drawLine(MARGIN, MARGIN, MARGIN, HEIGHT - MARGIN);

    double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
    max = max <= 0 ? 1.0 : max;
    int plotWidth = WIDTH - 2 * MARGIN;
    int plotHeight = HEIGHT - 2 * MARGIN;
    int slot = plotWidth / Math.max(1, values.size());
    int barWidth = Math.max(20, slot - 30);

    g.setFont(new Font("SansSerif", Font.PLAIN, 12));
    for (int i = 0; i < values.size(); i++) {
      int barHeight = (int) (plotHeight * (values.get(i) / max));
      int x = MARGIN + i * slot + (slot - barWidth) / 2;
      int y = HEIGHT - MARGIN - barHeight;
      g.setColor(BAR);
      g.fillRect(x, y, barWidth, barHeight);
      g.setColor(AXIS);
      g.drawString(labels.get(i), x, HEIGHT - MARGIN + 18);
      g.drawString(String.format("%.1f", values.get(i)), x, y - 6);
    }
    g.dispose();
    ImageIO.write(image, "png", out.toFile());
  }
}
