import backend.RealBackendConnector;
import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * Dedicated Window for viewing Individual Zone (Floor) Analytics
 * Features: Live Graph with Axes, Grid Lines, and Value Labels
 */
public class ZoneAnalyticsWindow extends JFrame {
  
  private final RealBackendConnector realBackend; 
  private JComboBox<String> zoneSelector;
  private ChartPanel chartPanel;
  private JLabel lblCurrentLevel;
  private JLabel lblStatus;
  private Timer refreshTimer;

  public ZoneAnalyticsWindow(RealBackendConnector connector) {
    super("📊 Zone-Specific Analytics");
    this.realBackend = connector;
    
    setSize(900, 650); 
    setLocationRelativeTo(null);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout());

    initUI();
    startRefreshLoop();
  }

  private void initUI() {
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
    topPanel.setBackground(new Color(20, 25, 40));
    
    JLabel title = new JLabel("SELECT FLOOR: ");
    title.setForeground(Color.CYAN);
    title.setFont(new Font("Segoe UI", Font.BOLD, 14));
    
    String[] options = {"Floor 1", "Floor 2", "Floor 3", "Floor 4"};
    zoneSelector = new JComboBox<>(options);
    zoneSelector.addActionListener(e -> updateDisplay());
    
    lblCurrentLevel = new JLabel("-- L");
    lblCurrentLevel.setFont(new Font("Segoe UI", Font.BOLD, 20));
    lblCurrentLevel.setForeground(Color.WHITE);
    
    lblStatus = new JLabel("NORMAL");
    lblStatus.setFont(new Font("Consolas", Font.BOLD, 16));
    lblStatus.setForeground(Color.GREEN);

    topPanel.add(title);
    topPanel.add(zoneSelector);
    topPanel.add(Box.createHorizontalGlue());
    topPanel.add(lblCurrentLevel);
    topPanel.add(lblStatus);
    
    add(topPanel, BorderLayout.NORTH);

    chartPanel = new ChartPanel();
    JScrollPane scrollPane = new JScrollPane(chartPanel);
    scrollPane.setBorder(null);
    add(scrollPane, BorderLayout.CENTER);
    
    updateDisplay();
  }

  private void updateDisplay() {
    int selectedIndex = zoneSelector.getSelectedIndex();
    
    Map<String, Object> stats = realBackend.getZoneStats(selectedIndex);
    int level = (Integer) stats.getOrDefault("level", 0);
    String state = (String) stats.getOrDefault("state", "UNKNOWN");
    
    lblCurrentLevel.setText(level + " / 2000 L");
    lblStatus.setText(state);
    
    switch (state) {
      case "LEAK" -> lblStatus.setForeground(Color.RED);
      case "LOW LEVEL" -> lblStatus.setForeground(Color.ORANGE);
      default -> lblStatus.setForeground(Color.GREEN);
    }
    
    chartPanel.setZoneIndex(selectedIndex);
    chartPanel.repaint();
  }

  private void startRefreshLoop() {
    refreshTimer = new Timer(1000, e -> {
      SwingUtilities.invokeLater(this::updateDisplay);
    });
    refreshTimer.start();
  }
  
  @Override
  public void dispose() {
    if(refreshTimer != null) refreshTimer.stop();
    super.dispose();
  }

  /**
   * INNER CLASS: Advanced Chart Renderer with Axes & Numbers
   */
  class ChartPanel extends JPanel {
    private int currentZoneIdx = 0;
    private static final double MAX_Y_VALUE = 2000.0; // Fixed scale for consistency

    public void setZoneIndex(int idx) {
      this.currentZoneIdx = idx;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      
      int w = getWidth();
      int h = getHeight();
      
      // Layout Padding
      int leftPad = 60; 
      int bottomPad = 40;
      int rightPad = 20;
      int topPad = 40;
      
      int chartW = w - leftPad - rightPad;
      int chartH = h - topPad - bottomPad;

      // Background
      g2.setColor(new Color(15, 20, 35));
      g2.fillRect(0, 0, w, h);
      
      // Title
      g2.setColor(Color.CYAN);
      g2.setFont(new Font("Arial", Font.BOLD, 16));
      g2.drawString("Water Level Trend - " + zoneSelector.getSelectedItem(), leftPad, 25);

      // Fetch Data
      List<Integer> history = realBackend.getZoneHistory(currentZoneIdx);
      
      if (history == null || history.isEmpty()) {
        g2.setColor(Color.GRAY);
        g2.drawString("Waiting for data...", w/2 - 50, h/2);
        return;
      }
      
      // --- DRAW GRID & AXES ---
      
      // Axis Lines
      g2.setColor(new Color(80, 80, 100));
      g2.setStroke(new BasicStroke(2));
      g2.drawLine(leftPad, topPad, leftPad, h - bottomPad);
      g2.drawLine(leftPad, h - bottomPad, w - rightPad, h - bottomPad);
      
      // Y-Axis Labels & Horizontal Grids
      g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
      g2.setColor(new Color(150, 150, 170));
      
      for (int i = 0; i <= 4; i++) {
        int val = i * 500; 
        double percent = val / MAX_Y_VALUE;
        int y = (h - bottomPad) - (int)(percent * chartH);
        
        String label = String.valueOf(val);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, leftPad - fm.stringWidth(label) - 10, y + 5);
        
        if (i > 0) { 
           g2.setColor(new Color(40, 45, 60));
           g2.setStroke(new BasicStroke(1));
           g2.drawLine(leftPad, y, w - rightPad, y);
           g2.setColor(new Color(150, 150, 170)); // Reset color for next label
        }
      }
      
      // X-Axis Labels
      g2.setColor(new Color(150, 150, 170));
      g2.drawString("Start", leftPad, h - bottomPad + 20);
      g2.drawString("Now", w - rightPad - 30, h - bottomPad + 20);
      
      // --- PLOT THE DATA LINE ---
      
      g2.setColor(new Color(0, 210, 255)); 
      g2.setStroke(new BasicStroke(3));
      
      int maxPoints = history.size();
      
      // Scaling Factors
      double scaleX = (maxPoints > 1) ? ((double) chartW / (maxPoints - 1)) : 0;
      double scaleY = chartH / MAX_Y_VALUE;
      
      int prevX = -1;
      int prevY = -1;
      
      for (int i = 0; i < maxPoints; i++) {
        int val = history.get(i);
        
        // Clamp value visually
        if (val > MAX_Y_VALUE) val = (int) MAX_Y_VALUE;
        if (val < 0) val = 0;
        
        int x = leftPad + (int)(i * scaleX);
        int y = (h - bottomPad) - (int)(val * scaleY);
        
        if (i > 0) {
          g2.drawLine(prevX, prevY, x, y);
        }
        
        // Draw Point Dot with White Border
        g2.fillOval(x-4, y-4, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawOval(x-4, y-4, 8, 8); 
        g2.setColor(new Color(0, 210, 255)); 
        
        prevX = x;
        prevY = y;
      }
      
      // Legend Box
      g2.setColor(new Color(25, 30, 45, 200)); 
      g2.fillRoundRect(w - 180, topPad + 10, 160, 40, 10, 10);
      g2.setColor(Color.WHITE);
      g2.setFont(new Font("Arial", Font.PLAIN, 12));
      g2.drawString("Scale: 0 - 2000 Liters", w - 170, topPad + 25);
      g2.drawString("Interval: Last 50 Ticks", w - 170, topPad + 40);
    }  
  }
}