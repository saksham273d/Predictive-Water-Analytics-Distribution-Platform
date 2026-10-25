import backend.RealBackendConnector;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * Master Analytics Window - Displays all Zone Trends in one unified view.
 */
public class ZoneAnalyticsWindow extends JFrame {
  
  private final RealBackendConnector realBackend; 
  private ChartPanel chartPanel;
  private JLabel lblSummary;
  private Timer refreshTimer;

  private static final Color[] ZONE_COLORS = {
    new Color(255, 87, 87),   // Red
    new Color(0, 255, 128),   // Green
    new Color(0, 169, 255),   // Blue
    new Color(255, 215, 0)    // Gold
  };

  public ZoneAnalyticsWindow(RealBackendConnector connector) {
    super("📊 MASTER ZONE ANALYTICS");
    this.realBackend = connector;
    
    setSize(1000, 700); 
    setLocationRelativeTo(null);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout());

    initUI();
    startRefreshLoop();
  }

  private void initUI() {
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
    topPanel.setBackground(new Color(20, 25, 40));
    
    JLabel title = new JLabel("ALL FLOORS LIVE TREND");
    title.setForeground(Color.CYAN);
    title.setFont(new Font("Segoe UI", Font.BOLD, 18));
    
    lblSummary = new JLabel("Loading...");
    lblSummary.setFont(new Font("Consolas", Font.PLAIN, 14));
    lblSummary.setForeground(Color.WHITE);

    topPanel.add(title);
    topPanel.add(Box.createHorizontalGlue());
    topPanel.add(lblSummary);
    
    add(topPanel, BorderLayout.NORTH);

    chartPanel = new ChartPanel();
    JScrollPane scrollPane = new JScrollPane(chartPanel);
    scrollPane.setBorder(null);
    add(scrollPane, BorderLayout.CENTER);
    
    updateDisplay();
  }

  private void updateDisplay() {
    Map<String, Object> status = realBackend.getCurrentStatus();
    int mainLvl = (Integer) status.getOrDefault("mainLevel", 0);
    
    lblSummary.setText(String.format("Main Tank: %d L | Monitoring Floors 1-4", mainLvl));
    
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

  class ChartPanel extends JPanel {
    private static final double MAX_Y_VALUE = 2000.0; 

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      
      int w = getWidth();
      int h = getHeight();
      
      int leftPad = 60; 
      int bottomPad = 50; 
      int rightPad = 150; 
      int topPad = 40;
      
      int chartW = w - leftPad - rightPad;
      int chartH = h - topPad - bottomPad;

      g2.setColor(new Color(15, 20, 35));
      g2.fillRect(0, 0, w, h);
      
      g2.setColor(Color.GRAY);
      g2.setFont(new Font("Arial", Font.PLAIN, 12));
      g2.drawString("Water Level Trend (Liters)", leftPad, 25);

      // Axes
      g2.setColor(new Color(80, 80, 100));
      g2.setStroke(new BasicStroke(2));
      g2.drawLine(leftPad, topPad, leftPad, h - bottomPad);
      g2.drawLine(leftPad, h - bottomPad, w - rightPad, h - bottomPad);
      
      // Y-Axis Labels & Grids
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
           g2.setColor(new Color(150, 150, 170)); 
        }
      }
      
      // X-Axis Labels
      g2.setColor(new Color(150, 150, 170));
      g2.drawString("Start", leftPad, h - bottomPad + 20);
      g2.drawString("Now", w - rightPad - 30, h - bottomPad + 20);
      
      // Data Fetching
      final List<List<Integer>> histories = new ArrayList<>(4);
      for(int i=0; i<4; i++) {
        histories.add(realBackend.getZoneHistory(i));
      }
      
      int maxPoints = 0;
      for(List<Integer> hist : histories) {
        if(hist != null && hist.size() > maxPoints) maxPoints = hist.size();
      }

      if (maxPoints < 2) {
        g2.setColor(Color.GRAY);
        g2.drawString("Waiting for data...", w/2 - 50, h/2);
        return;
      }

      double scaleX = (double) chartW / (maxPoints - 1);
      double scaleY = chartH / MAX_Y_VALUE;
      
      // Plotting Lines
      for (int z = 0; z < 4; z++) {
        List<Integer> history = histories.get(z);
        if (history == null || history.isEmpty()) continue;
        
        g2.setColor(ZONE_COLORS[z]); 
        g2.setStroke(new BasicStroke(3)); 
        
        int prevX = -1;
        int prevY = -1;
        
        for (int i = 0; i < history.size(); i++) {
          int val = history.get(i);
          
          if (val > MAX_Y_VALUE) val = (int) MAX_Y_VALUE;
          if (val < 0) val = 0;
          
          int x = leftPad + (int)(i * scaleX);
          int y = (h - bottomPad) - (int)(val * scaleY);
          
          if (i > 0) {
            g2.drawLine(prevX, prevY, x, y);
          }
          
          g2.fillOval(x-3, y-3, 6, 6);
          
          prevX = x;
          prevY = y;
        }
      }
      
      drawLegend(g2, w - rightPad + 20, topPad + 20);
    }  
    
    private void drawLegend(Graphics2D g2, int startX, int startY) {
      g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
      g2.setColor(Color.WHITE);
      g2.drawString("LEGEND", startX, startY);
      
      int itemHeight = 30;
      for(int i=0; i<4; i++) {
        int y = startY + 20 + (i * itemHeight);
        
        g2.setColor(ZONE_COLORS[i]);
        g2.fillRect(startX, y - 10, 15, 15);
        
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawString("Floor " + (i+1), startX + 25, y + 2);
      }
    }
  }
}