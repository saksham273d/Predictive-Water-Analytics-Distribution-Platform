import backend.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public class Main implements AlertListener {
  
  // UI Components
  private static JProgressBar tankBar;
  private static JLabel statusLabel;
  private static JLabel timeLabel;
  private static JTextArea alertLog;
  private static BackendConnector backend;
  private static JFrame mainFrame;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      // 1. Initialize Mock Backend
      backend = new MockBackendConnector();
      
      // 2. Create Dashboard
      createAndShowDashboard();
      
      // 3. Start Auto Refresh (Every 10 sec)
      startAutoRefresh();
      
      // 4. Connect Alerts
      backend.setAlertListener(new Main());
    });
  }

  private static void createAndShowDashboard() {
    // Global Font Settings for Modern Look
    try {
      UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 16));
      UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 14));
      UIManager.put("ProgressBar.font", new Font("Segoe UI", Font.BOLD, 18));
    } catch (Exception e) {
      // Ignore if font not found
    }

    mainFrame = new JFrame("💧 Society Water Dashboard - LIVE");
    mainFrame.setSize(750, 600);
    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    mainFrame.setLayout(new BorderLayout(10, 10));

    // --- TOP PANEL (Tank & Status) ---
    tankBar = new JProgressBar(0, 100);
    tankBar.setStringPainted(true);
    
    statusLabel = new JLabel("", SwingConstants.CENTER);
    timeLabel = new JLabel("", SwingConstants.CENTER);
    timeLabel.setFont(new Font("Arial", Font.BOLD, 18));

    // History Button
    JButton historyBtn = new JButton("[CHART] Show Live History");
    historyBtn.setBackground(new Color(0, 120, 60));
    historyBtn.setForeground(Color.WHITE);
    historyBtn.setFocusPainted(false);
    historyBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    historyBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    
    // ✅ IMPORTANT: Connect Button to Method
    historyBtn.addActionListener(e -> showHistoryChart());

    JPanel topPanel = new JPanel(new GridLayout(4, 1, 5, 5));
    topPanel.add(tankBar);
    topPanel.add(statusLabel);
    topPanel.add(timeLabel);
    topPanel.add(historyBtn);

    // --- CENTER PANEL (Alert Logs) ---
    alertLog = new JTextArea(10, 40);
    alertLog.setEditable(false);
    alertLog.setFont(new Font("Consolas", Font.PLAIN, 13));
    alertLog.setBackground(new Color(245, 245, 245));
    alertLog.setBorder(BorderFactory.createTitledBorder("⚠️ Live System Alerts"));
    
    JScrollPane scrollPane = new JScrollPane(alertLog);

    // Add panels to frame
    mainFrame.add(topPanel, BorderLayout.NORTH);
    mainFrame.add(scrollPane, BorderLayout.CENTER);

    // Initial Data Load
    updateFromBackend();
    mainFrame.setVisible(true);
  }

  /** Har 10 second mein data refresh karo */
  private static void startAutoRefresh() {
    Timer timer = new Timer(10000, e -> {
      SwingUtilities.invokeLater(() -> updateFromBackend());
    });
    timer.start();
  }

  /** Backend se naya data lao aur UI update karo */
  private static void updateFromBackend() {
    try {
      Map<String, Object> status = backend.getCurrentStatus();
      int level = (Integer) status.get("level");
      int capacity = (Integer) status.get("capacity");
      String statusText = (String) status.get("status");

      int percentage = (level * 100) / capacity;
      tankBar.setValue(percentage);
      tankBar.setString(level + " / " + capacity + " L (" + percentage + "%)");

      // Dynamic Colors based on status
      if ("CRITICAL".equals(statusText)) {
        tankBar.setForeground(Color.RED);
        statusLabel.setText("[!] CRITICAL - Supply Cut Imminent!");
        statusLabel.setForeground(Color.RED);
        timeLabel.setText("Est. Time Left: < 30 min");
      } else if ("WARNING".equals(statusText)) {
        tankBar.setForeground(Color.ORANGE);
        statusLabel.setText("[~] WARNING - Low Water Level");
        statusLabel.setForeground(Color.ORANGE);
        timeLabel.setText("Est. Time Left: ~1-2 hours");
      } else {
        tankBar.setForeground(new Color(0, 153, 76));
        statusLabel.setText("[OK] NORMAL - Adequate Supply");
        statusLabel.setForeground(Color.BLACK);
        timeLabel.setText("Est. Time Left: 3+ hours");
      }
    } catch (Exception ex) {
      statusLabel.setText("❌ Error fetching data");
      ex.printStackTrace();
    }
  }

  /** ✅ CHART WINDOW KHOLNE KA METHOD (Pure Java Graphics) */
  private static void showHistoryChart() {
    System.out.println(">>> Opening Chart Window..."); // Debug Print

    JFrame chartFrame = new JFrame("📈 Live Usage History");
    chartFrame.setSize(800, 500);
    chartFrame.setLocationRelativeTo(mainFrame);
    chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    // Custom Panel for Drawing Graph
    JPanel chartPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int padding = 50;
        int chartW = width - 2 * padding;
        int chartH = height - 2 * padding;

        // Background
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);

        // Title
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.drawString("Water Level Trend (Last 24 Hours)", padding, 30);

        // Fetch Data
        List<Map<String, Object>> history = backend.getHistory(24);
        if (history == null || history.isEmpty()) {
          g2.drawString("No Data Available", padding, height/2);
          return;
        }

        // Find Max Value for Scaling
        int maxVal = 10000; 
        for(Map<String,Object> r : history) {
            int lvl = (Integer)r.get("level");
            if(lvl > maxVal) maxVal = lvl;
        }

        // Draw Axes
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(new BasicStroke(1));
        // Horizontal Grid Lines
        for(int i=0; i<=4; i++) {
            int y = height - padding - (i * chartH / 4);
            g2.drawLine(padding, y, width - padding, y);
            
            // Y Labels
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            int val = (maxVal * i) / 4;
            g2.drawString(val + "L", 10, y + 4);
        }

        // Draw Line Graph
        g2.setColor(new Color(0, 153, 76)); // Green
        g2.setStroke(new BasicStroke(3));
        
        int prevX = padding;
        int prevY = height - padding;

        for (int i = 0; i < history.size(); i++) {
          Map<String, Object> reading = history.get(i);
          int level = (Integer) reading.get("level");
          
          // Calculate Coordinates
          int x = padding + (i * chartW / (history.size() - 1));
          int y = height - padding - (level * chartH / maxVal);

          if (i > 0) {
            g2.drawLine(prevX, prevY, x, y);
          }
          
          // Draw Point
          g2.fillOval(x - 3, y - 3, 6, 6);
          
          prevX = x;
          prevY = y;
        }
        
        // Current Value Indicator
        g2.setColor(Color.BLUE);
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        int currentLevel = (Integer) history.get(history.size()-1).get("level");
        g2.drawString("Current: " + currentLevel + " L", prevX - 80, prevY - 15);
      }
    };

    chartFrame.add(chartPanel);
    chartFrame.setVisible(true);

    // ✅ AUTO REFRESH FOR CHART
    Timer chartTimer = new Timer(10000, e -> {
        chartPanel.repaint(); // Redraw with fresh data
    });
    chartTimer.start();

    // Stop timer when window closes
    chartFrame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent e) {
        chartTimer.stop();
      }
    });
  }

  /** Alert Listener Implementation */
  @Override
  public void onAlert(String severity, String message) {
    SwingUtilities.invokeLater(() -> {
      String time = java.time.LocalTime.now().toString();
      String logEntry = "[" + time + "] [" + severity + "] " + message + "\n";
      alertLog.append(logEntry);
      alertLog.setCaretPosition(alertLog.getDocument().getLength());
      
      if ("CRITICAL".equals(severity)) {
        Toolkit.getDefaultToolkit().beep();
      }
    });
  }
}