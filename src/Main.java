import backend.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Smart Water Monitoring System - Native Java Swing Interface
 * Features: Custom Dark Theme, Card Layout, Live Canvas Charts, Responsive Design
 */
public class Main extends JFrame implements AlertListener {

  private BackendConnector backend;
  private JPanel mainContainer;
  private Timer refreshTimer;

  // Dynamic Labels for Stats
  private JLabel lblLevelValue;
  private JLabel lblPumpStatus;
  private JLabel lblHealthValue;
  private JLabel lblAlertsValue;

  // Chart Panel Reference
  private LiveChartPanel chartPanel;

  public Main() {
    super("💧 Smart Water IoT Dashboard");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(1280, 800);
    setLocationRelativeTo(null);

    // Initialize Backend
    backend = new RealBackendConnector();
    backend.setAlertListener(this);

    initUI();
    startRefreshLoop();
  }

  private void initUI() {
    setLayout(new BorderLayout());

    // Header Bar
    add(createHeader(), BorderLayout.NORTH);

    // Main Content Container
    mainContainer = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, new Color(15, 20, 35), 0, getHeight(), new Color(10, 15, 25));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    mainContainer.setBorder(new EmptyBorder(20, 20, 20, 20));

    JScrollPane scrollPane = new JScrollPane(mainContainer);
    scrollPane.setBorder(null);
    scrollPane.getViewport().setOpaque(false);
    scrollPane.setOpaque(false);

    add(scrollPane, BorderLayout.CENTER);

    buildDashboardCards();
  }

  private void buildDashboardCards() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;

    // --- ROW 1: KEY METRICS CARDS ---
    gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.gridheight = 1;
    mainContainer.add(createMetricCard("TANK LEVEL", "-- L", Color.CYAN, true), gbc);

    gbc.gridx++;
    mainContainer.add(createMetricCard("PUMP STATUS", "IDLE", Color.GRAY, false), gbc);

    gbc.gridx++;
    mainContainer.add(createMetricCard("SYS HEALTH", "--%", Color.YELLOW, false), gbc);

    gbc.gridx++;
    mainContainer.add(createMetricCard("WARNINGS", "0", Color.ORANGE, false), gbc);
   
    // --- ROW 2: VISUALIZERS & CONTROLS ---
    gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 2;
    mainContainer.add(createGridVisualizer(), gbc);

    gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 1;
    chartPanel = new LiveChartPanel();
    mainContainer.add(chartPanel, gbc);

    gbc.gridx = 2; gbc.gridy = 2; gbc.gridheight = 1;
    mainContainer.add(createControlPanel(), gbc);
  }

  // --- CUSTOM COMPONENTS ---

  private JPanel createHeader() {
    JPanel header = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(20, 25, 40));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(40, 45, 60));
        g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      }
    };
    header.setPreferredSize(new Dimension(0, 60));

    JLabel title = new JLabel("💧 WATER ANALYTICS DASHBOARD");
    title.setFont(new Font("Segoe UI", Font.BOLD, 22));
    title.setForeground(Color.CYAN);
    title.setBorder(new EmptyBorder(0, 20, 0, 0));

    JLabel status = new JLabel("● LIVE MONITORING ACTIVE");
    status.setFont(new Font("Consolas", Font.BOLD, 14));
    status.setForeground(new Color(100, 255, 100));
    status.setBorder(new EmptyBorder(0, 0, 0, 20));

    header.add(title, BorderLayout.WEST);
    header.add(status, BorderLayout.EAST);
    return header;
  }

  private JPanel createMetricCard(String label, String value, Color accent, boolean isPrimary) {
    JPanel card = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Card Background with Shadow Effect Simulation
        g2.setColor(new Color(25, 30, 45));
        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 15, 15);

        // Top Accent Bar
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(4));
        g2.drawArc(10, -10, getWidth() - 20, 20, 0, 180); // Curved top line

        // Border Glow if Primary
        if (isPrimary) {
          g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50));
          g2.setStroke(new BasicStroke(2));
          g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 15, 15);
        }
      }
    };
    card.setPreferredSize(new Dimension(0, 120));

    JLabel lblTitle = new JLabel(label);
    lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
    lblTitle.setForeground(Color.LIGHT_GRAY);
    lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
    lblTitle.setBorder(new EmptyBorder(10, 0, 0, 0));

    JLabel lblVal = new JLabel(value);
    lblVal.setFont(new Font("Segoe UI", Font.BOLD, 28));
    lblVal.setForeground(accent);
    lblVal.setHorizontalAlignment(SwingConstants.CENTER);

    // Store reference for dynamic updates
    if (label.equals("TANK LEVEL")) lblLevelValue = lblVal;
    else if (label.equals("PUMP STATUS")) lblPumpStatus = lblVal;
    else if (label.equals("SYS HEALTH")) lblHealthValue = lblVal;
    else if (label.equals("WARNINGS") || label.equals("ACTIVE ALERTS")) lblAlertsValue = lblVal;

    card.add(lblTitle, BorderLayout.NORTH);
    card.add(lblVal, BorderLayout.CENTER);

    return card;
  }

  private JComponent createGridVisualizer() {
    JPanel gridPanel = new JPanel() {
      // Particle list for animation
      private List<Point> particles = new ArrayList<>();
      private Random rand = new Random();

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        // Layout Constants
        int mainRadius = 60;
        int zoneRadius = 35;
        int centerX = w / 2;
        int mainY = 80; 
        int zoneStartX = 40;
        int zoneGap = (w - 80) / 4; 
        int zoneY = h - 100; 
        
        // Get Data
        Map<String, Object> status = backend.getCurrentStatus();
        int mainLevel = (Integer) status.get("mainLevel");
        boolean pumpOn = (Boolean) status.getOrDefault("pumpActive", false);
        List<Map<String, Object>> zones = (List<Map<String, Object>>) status.get("zones");
        
        // 1. DRAW CONNECTING PIPES WITH FLOW INDICATION
        g2.setStroke(new BasicStroke(4));
        
        for (int i = 0; i < 4; i++) {
          int zx = zoneStartX + (i * zoneGap) + (zoneGap/2);
          Point start = new Point(centerX, mainY + mainRadius);
          Point end = new Point(zx, zoneY - zoneRadius);
          
          // Draw Base Pipe
          g2.setColor(new Color(60, 60, 80));
          g2.drawLine(start.x, start.y, end.x, end.y);
          
          // Draw Flow Animation if water is moving
          Map<String, Object> z = zones.get(i);
          double flowRate = (Double) z.get("flowRate");
          
          if (flowRate > 1.0) { // If there is significant flow
            g2.setColor(new Color(0, 210, 255, 150)); // Cyan semi-transparent
            g2.setStroke(new BasicStroke(6));
            
            // Generate random particle positions along the line for effect
            // In a real high-perf app, use Timer. Here we simulate density.
            int numParticles = (int)(flowRate / 10); 
            for(int p=0; p<numParticles; p++) {
              double t = rand.nextDouble(); // Interpolation factor 0-1
              int px = (int)(start.x + t * (end.x - start.x));
              int py = (int)(start.y + t * (end.y - start.y));
              g2.fillOval(px-2, py-2, 4, 4);
            }
            g2.setStroke(new BasicStroke(4)); // Reset stroke
          }
        }
        
        // 2. DRAW MAIN TANK
        drawTankCircle(g2, centerX, mainY, mainRadius, mainLevel, 10000, "MAIN", pumpOn);
        
        // 3. DRAW ZONE TANKS
        if (zones != null) {
          for (int i = 0; i < zones.size(); i++) {
            Map<String, Object> z = zones.get(i);
            int zx = zoneStartX + (i * zoneGap) + (zoneGap/2);
            int zLevel = (Integer) z.get("level");
            int zCap = (Integer) z.get("cap");
            String zState = (String) z.get("state");
            
            Color accent = Color.GREEN;
            if ("LEAK".equals(zState)) accent = Color.RED;
            else if ("STAGNANT".equals(zState)) accent = Color.ORANGE;
            
            drawTankCircle(g2, zx, zoneY, zoneRadius, zLevel, zCap, "F-" + (i+1), false);
            
            // State Label
            g2.setColor(accent);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            String txt = zState;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(txt, zx - fm.stringWidth(txt)/2, zoneY + zoneRadius + 15);
          }
        }
      }
      
      private void drawTankCircle(Graphics2D g2, int cx, int cy, int r, int level, int cap, String label, boolean isActive) {
        g2.setColor(new Color(40, 45, 60));
        g2.setStroke(new BasicStroke(5));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        
        double percent = level / (double) cap;
        Color fillColor = Color.CYAN;
        if (percent < 0.3) fillColor = Color.RED;
        else if (isActive) fillColor = Color.GREEN; 
        
        g2.setColor(fillColor);
        g2.setStroke(new BasicStroke(r/3)); 
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(int)(360 * percent));
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, r/2));
        String text = String.valueOf(level);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text)/2, cy + 5);
        
        g2.setColor(Color.GRAY);
        g2.setFont(new Font("Arial", Font.PLAIN, 10));
        g2.drawString(label, cx - fm.stringWidth(label)/2, cy + r + 15);
      }
    };
    gridPanel.setPreferredSize(new Dimension(400, 400));
    return gridPanel;
  } 
      
  private JComponent createControlPanel() {
    // Changed to GridLayout(3, 1) to accommodate the new button comfortably
    JPanel controls = new JPanel(new GridLayout(3, 1, 10, 10)) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(25, 30, 45));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
      }
    };
    controls.setBorder(new EmptyBorder(10, 10, 10, 10));

    // 1. Status Label
    JLabel lblStatus = new JLabel("SYSTEM READY", SwingConstants.CENTER);
    lblStatus.setFont(new Font("Consolas", Font.BOLD, 16));
    lblStatus.setForeground(Color.CYAN);

    // 2. Force Toggle Pump Button (Teal/Cyan Gradient)
    JButton btnToggle = new JButton("⚡ FORCE TOGGLE PUMP") {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        GradientPaint gp = new GradientPaint(
          0, 0, new Color(0, 150, 136), 
          getWidth(), getHeight(), new Color(0, 210, 255)
        );
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        
        g2.setColor(new Color(0, 255, 255, 100)); 
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
        
        super.paintComponent(g);
      }
    };
    btnToggle.setForeground(new Color(10, 20, 30)); 
    btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
    btnToggle.setFocusPainted(false);
    btnToggle.setContentAreaFilled(false); 
    
    btnToggle.addActionListener(e -> {
      backend.sendCommand("TOGGLE_PUMP");
      dashboardRepaintAll();
    });

    // 3. NEW: View Zone Analytics Button (Purple Gradient)
    JButton btnViewZones = new JButton("🔍 VIEW ZONE ANALYTICS") {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Purple/Magenta Gradient for distinction
        GradientPaint gp = new GradientPaint(
          0, 0, new Color(100, 50, 150), 
          getWidth(), getHeight(), new Color(180, 80, 220)
        );
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        
        g2.setColor(new Color(255, 255, 255, 50)); 
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 10, 10);
        
        super.paintComponent(g);
      }
    };
    btnViewZones.setForeground(Color.WHITE);
    btnViewZones.setFont(new Font("Segoe UI", Font.BOLD, 14));
    btnViewZones.setFocusPainted(false);
    btnViewZones.setContentAreaFilled(false);
    
    btnViewZones.addActionListener(e -> {
      try {
        Class<?> windowClass = Class.forName("ZoneAnalyticsWindow");
        java.lang.reflect.Constructor<?> constructor = windowClass.getDeclaredConstructor(backend.getClass());
        JFrame window = (JFrame) constructor.newInstance(backend);
        window.setVisible(true);
      } catch (ReflectiveOperationException | ClassCastException ex) {
        JOptionPane.showMessageDialog(this, "Zone Analytics not available in Mock Mode.", "Info", JOptionPane.INFORMATION_MESSAGE);
      }
    });

    // Add components to panel
    controls.add(lblStatus);
    controls.add(btnToggle);
    controls.add(btnViewZones);
    
    return controls;
  }
  
  class LiveChartPanel extends JPanel {
    private List<Integer> historyData = new ArrayList<>();
    private static final int MAX_POINTS = 50;

    public LiveChartPanel() {
      setBackground(new Color(25, 30, 45));
      setPreferredSize(new Dimension(0, 200));
      
      // Add border painter manually in paintComponent
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int padding = 20;
      int chartW = w - 2 * padding;
      int chartH = h - 2 * padding;

      // Title
      g2.setColor(Color.CYAN);
      g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
      g2.drawString("📈 LIVE USAGE TREND", padding, 15);

      // Axes
      g2.setColor(new Color(60, 60, 80));
      g2.setStroke(new BasicStroke(1));
      g2.drawLine(padding, padding, padding, h - padding);
      g2.drawLine(padding, h - padding, w - padding, h - padding);

      // Plot Data
      if (historyData.size() > 1) {
        g2.setColor(new Color(0, 210, 255)); // Cyan Line
        g2.setStroke(new BasicStroke(3));

        int prevX = padding;
        int prevY = h - padding;

        for (int i = 0; i < historyData.size(); i++) {
          int val = historyData.get(i);
          double percent = val / 10000.0;

          int x = padding + (i * chartW / (MAX_POINTS - 1));
          int y = h - padding - (int) (percent * chartH);

          if (i > 0) {
            g2.drawLine(prevX, prevY, x, y);
          }

          // Draw Point
          g2.fillOval(x - 3, y - 3, 6, 6);

          prevX = x;
          prevY = y;
        }
      } else {
        g2.setColor(Color.GRAY);
        g2.drawString("Waiting for data...", padding + 50, h / 2);
      }
    }

    public void addDataPoint(int level) {
      historyData.add(level);
      if (historyData.size() > MAX_POINTS) {
        historyData.remove(0);
      }
      repaint();
    }
  }

  private void startRefreshLoop() {
    refreshTimer = new Timer(2000, e -> {
      SwingUtilities.invokeLater(() -> {
        try {
          Map<String, Object> status = backend.getCurrentStatus();

          // 1. Fetch Basic Metrics
          int level = (Integer) status.getOrDefault("mainLevel", 0);
          boolean pumpOn = (Boolean) status.getOrDefault("pumpActive", false);
          
          // 2. Fetch Advanced Metrics (With Safety Checks)
          String healthStr = (String) status.getOrDefault("healthScore", "N/A");
          int alerts = (Integer) status.getOrDefault("activeAlerts", 0);

          // --- UPDATE LABELS ---

          // Tank Level Card
          if (lblLevelValue != null) {
            lblLevelValue.setText(level + " L");
          }

          // Pump Status Card
          if (lblPumpStatus != null) {
            lblPumpStatus.setText(pumpOn ? "RUNNING 🟢" : "IDLE ⚪");
            lblPumpStatus.setForeground(pumpOn ? Color.GREEN : Color.GRAY);
          }

          if (lblHealthValue != null) {
            lblHealthValue.setText(healthStr);
            
            // Color Coding Logic
            try {
              if (healthStr.contains("%")) {
                double val = Double.parseDouble(healthStr.replace("%", "").trim());
                if (val >= 80) {
                  lblHealthValue.setForeground(Color.GREEN);
                } else if (val >= 50) {
                  lblHealthValue.setForeground(Color.ORANGE);
                } else {
                  lblHealthValue.setForeground(Color.RED);
                }
              } else {
                lblHealthValue.setForeground(Color.GRAY);
              }
            } catch (NumberFormatException ex) {
              lblHealthValue.setForeground(Color.GRAY);
            }
          } else {
            System.err.println("ERROR: lblHealthValue is NULL! Check buildDashboardCards().");
          }

          if (lblAlertsValue != null) {
            lblAlertsValue.setText(String.valueOf(alerts));
            
            if (alerts == 0) {
              lblAlertsValue.setForeground(Color.GREEN);
              lblAlertsValue.setFont(new Font("Segoe UI", Font.BOLD, 28));
            } else if (alerts <= 2) {
              lblAlertsValue.setForeground(Color.ORANGE); // Warning Zone
              lblAlertsValue.setFont(new Font("Segoe UI", Font.BOLD, 30));
            } else {
              lblAlertsValue.setForeground(Color.RED); // Critical Zone
              lblAlertsValue.setFont(new Font("Segoe UI", Font.BOLD, 32));
            }
          }

          // Update Live Chart
          if (chartPanel != null) {
            chartPanel.addDataPoint(level);
          }

          // Repaint Grid Visualizer (Tank Circles & Pipes)
          mainContainer.repaint();

        } catch (Exception ex) {
          System.err.println("UI Refresh Error: " + ex.getMessage());
          ex.printStackTrace();
        }
      });
    });
    refreshTimer.start();
  }

  private void dashboardRepaintAll() {
    if (mainContainer != null) mainContainer.repaint();
  }

  @Override
  public void onAlert(String severity, String message) {
    SwingUtilities.invokeLater(() -> {
      JOptionPane.showMessageDialog(this,
          "[" + severity + "] " + message,
          "System Alert",
          JOptionPane.WARNING_MESSAGE);
    });
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        // Set global colors to avoid white flashes
        UIManager.put("Panel.background", new Color(15, 20, 35));
        UIManager.put("Label.foreground", Color.WHITE);
      } catch (Exception e) {
        e.printStackTrace();
      }

      Main app = new Main();
      app.setVisible(true);
    });
  }
}