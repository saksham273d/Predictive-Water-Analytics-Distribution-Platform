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
 */
public final class Main extends JFrame implements AlertListener {

  private static final long serialVersionUID = 1L;

  private final transient BackendConnector backend;
  private JPanel mainContainer;
  private Timer refreshTimer;

  private JLabel lblLevelValue;
  private JLabel lblPumpStatus;
  private JLabel lblHealthValue;
  private JLabel lblAlertsValue;

  private LiveChartPanel chartPanel;
  private DiscrepancyChartPanel discPanel;

  public Main() {
    super("💧 AQUASENSE DASHBOARD");
    setDefaultCloseOperation(EXIT_ON_CLOSE);

    setSize(1400, 850);
    setLocationRelativeTo(null);
    setResizable(true);
    setMinimumSize(new Dimension(1000, 600));

    System.setProperty("awt.useSystemAAFontSettings","on");
    System.setProperty("swing.aatext", "true");

    backend = new RealBackendConnector();

    initUI();
    startRefreshLoop();
  }

  private void initUI() {
    setLayout(new BorderLayout());
    add(createHeader(), BorderLayout.NORTH);

    mainContainer = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, new Color(10, 15, 30), 0, getHeight(), new Color(5, 8, 15));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    mainContainer.setBorder(new EmptyBorder(10, 15, 10, 15));

    add(mainContainer, BorderLayout.CENTER);
    buildDashboardCards();
  }

  private void buildDashboardCards() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(8, 8, 8, 8);

    // Row 0: Metrics
    gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4; gbc.gridheight = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 0.15;

    JPanel topRowPanel = new JPanel(new GridLayout(1, 4, 15, 0));
    topRowPanel.setOpaque(false);

    JPanel cardReservoir = createFlexibleMetricLabel("MAIN RESERVOIR", "-- L", Color.CYAN, true);
    JPanel cardPump = createFlexibleMetricLabel("PUMP STATUS", "IDLE", Color.GRAY, false);
    JPanel cardHealth = createFlexibleMetricLabel("SYSTEM HEALTH", "--%", Color.YELLOW, false);
    JPanel cardAlerts = createFlexibleMetricLabel("ACTIVE ALERTS", "0", Color.ORANGE, false);

    lblLevelValue = (JLabel) cardReservoir.getComponent(1);
    lblPumpStatus = (JLabel) cardPump.getComponent(1);
    lblHealthValue = (JLabel) cardHealth.getComponent(1);
    lblAlertsValue = (JLabel) cardAlerts.getComponent(1);

    topRowPanel.add(cardReservoir);
    topRowPanel.add(cardPump);
    topRowPanel.add(cardHealth);
    topRowPanel.add(cardAlerts);

    mainContainer.add(topRowPanel, gbc);

    // Left: Visualizer
    gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 2;
    gbc.weightx = 1.0; gbc.weighty = 1.0;
    JComponent gridVis = createGridVisualizer();
    gridVis.setMinimumSize(new Dimension(300, 300));
    mainContainer.add(gridVis, gbc);

    // Right Top: Trend Chart
    gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 1;    
    gbc.weightx = 1.0; gbc.weighty = 0.6;
    chartPanel = new LiveChartPanel();
    chartPanel.setMinimumSize(new Dimension(250, 150));
    mainContainer.add(chartPanel, gbc);

    // Right Mid: Mass Balance Chart
    gbc.gridx = 2; gbc.gridy = 2; gbc.gridheight = 1; gbc.weighty = 0.4;
    if (backend instanceof RealBackendConnector realBackend) {
      discPanel = new DiscrepancyChartPanel(realBackend);
      discPanel.setMinimumSize(new Dimension(250, 120));
      mainContainer.add(discPanel, gbc);
    }

    // Right Bottom: Controls
    gbc.gridx = 2; gbc.gridy = 3; gbc.gridwidth = 2; gbc.gridheight = 1; gbc.weighty = 0.2;
    JComponent ctrlDeck = createControlPanel();
    ctrlDeck.setMinimumSize(new Dimension(250, 90));
    mainContainer.add(ctrlDeck, gbc);

    // Bottom Strip: Maintenance
    gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.gridheight = 1; gbc.weighty = 0.1;
    JComponent maintStrip = createZoneMaintenanceStrip();
    maintStrip.setMinimumSize(new Dimension(600, 40));
    mainContainer.add(maintStrip, gbc);
  }

  private JPanel createFlexibleMetricLabel(String labelText, String valueText, Color accent, boolean isPrimary) {
    JPanel card = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(25, 30, 45, 220));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(10, 0, getWidth()-10, 0);
        if (isPrimary) {
          g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        }
      }
    };

    JLabel lblTitle = new JLabel(labelText);
    lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
    lblTitle.setForeground(new Color(180, 190, 210));
    lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
    lblTitle.setBorder(new EmptyBorder(5, 0, 0, 0));

    JLabel lblVal = new JLabel(valueText);
    lblVal.setFont(new Font("Segoe UI Light", Font.PLAIN, 26));
    lblVal.setForeground(accent);
    lblVal.setHorizontalAlignment(SwingConstants.CENTER);

    card.add(lblTitle, BorderLayout.NORTH);
    card.add(lblVal, BorderLayout.CENTER);

    return card;
  }

  private JPanel createHeader() {
    JPanel header = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(15, 20, 35));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(0, 210, 255, 80));
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
      }
    };
    header.setPreferredSize(new Dimension(0, 50));

    JLabel title = new JLabel("💧 AQUASENSE DASHBOARD");
    title.setFont(new Font("Segoe UI Semibold", Font.BOLD, 22));
    title.setForeground(Color.WHITE);
    title.setBorder(new EmptyBorder(0, 20, 0, 0));

    JLabel status = new JLabel("● LIVE TELEMETRY ACTIVE");
    status.setFont(new Font("Consolas", Font.BOLD, 12));
    status.setForeground(new Color(0, 255, 128));
    status.setBorder(new EmptyBorder(0, 0, 0, 20));

    header.add(title, BorderLayout.WEST);
    header.add(status, BorderLayout.EAST);
    return header;
  }

  private JComponent createGridVisualizer() {
    JPanel gridPanel = new JPanel() {
      private final Random rand = new Random();

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        if (w < 200 || h < 200) return;

        int mainRadius = Math.min(w / 9, h / 5); 
        int centerX = w / 2;
        int mainY = mainRadius + 40; 

        int zoneCount = 4;
        int horizontalMargin = 20; 
        int availableWidth = w - (horizontalMargin * 2);
        int slotWidth = availableWidth / zoneCount;
        
        int maxZoneRadiusByWidth = (slotWidth / 2) - 6; 
        int maxZoneRadiusByHeight = (h / 7); 
        
        int zoneRadius = Math.min(maxZoneRadiusByWidth, maxZoneRadiusByHeight);
        
        if (zoneRadius < 15) zoneRadius = 15; 

        int zoneY = h - zoneRadius - 40; 

        Map<String, Object> status = backend.getCurrentStatus();
        int mainLevel = (Integer) status.get("mainLevel");
        boolean pumpOn = (Boolean) status.getOrDefault("pumpActive", false);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zones = (List<Map<String, Object>>) status.get("zones");
        
        g2.setStroke(new BasicStroke(2));
        
        // Draw Pipes FIRST (Background Layer)
        for (int i = 0; i < zoneCount; i++) {
          int zx = horizontalMargin + (i * slotWidth) + (slotWidth / 2);
          
          Point start = new Point(centerX, mainY + mainRadius);
          Point end = new Point(zx, zoneY - zoneRadius);
          
          g2.setColor(new Color(40, 45, 60));
          g2.drawLine(start.x, start.y, end.x, end.y);
          
          Map<String, Object> z = zones.get(i);
          double flowRate = (Double) z.get("flowRate");
          
          if (flowRate > 1.0) {
            g2.setColor(new Color(0, 210, 255, 180)); 
            g2.setStroke(new BasicStroke(3));
            
            int numParticles = Math.min(4, (int)(flowRate / 30)); 
            for(int p=0; p<numParticles; p++) {
              double t = rand.nextDouble(); 
              int px = (int)(start.x + t * (end.x - start.x));
              int py = (int)(start.y + t * (end.y - start.y));
              
              g2.fillOval(px-2, py-2, 4, 4);
              g2.setColor(Color.WHITE);
              g2.fillOval(px-1, py-1, 2, 2);
              g2.setColor(new Color(0, 210, 255, 180));
            }
            g2.setStroke(new BasicStroke(2)); 
          }
        }
        
        drawTankCircle(g2, centerX, mainY, mainRadius, mainLevel, 10000, "MAIN", pumpOn);
        
        if (zones != null) {
          for (int i = 0; i < zones.size(); i++) {
            Map<String, Object> z = zones.get(i);
            int zx = horizontalMargin + (i * slotWidth) + (slotWidth / 2);
            int zLevel = (Integer) z.get("level");
            int zCap = (Integer) z.get("cap");
            String zState = (String) z.get("state");
            
            Color accent = Color.GREEN; 
            String displayTxt = zState; 

            if ("LEAK".equals(zState)) {
              accent = new Color(255, 60, 60); 
              displayTxt = "BLOCKED"; 
            } else if ("LOW LEVEL".equals(zState)) {
              accent = Color.ORANGE;
            }
            
            drawTankCircle(g2, zx, zoneY, zoneRadius, zLevel, zCap, "F-" + (i+1), false);
            
            g2.setColor(accent);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 9)); 
            
            FontMetrics fm = g2.getFontMetrics();
            int textX = zx - fm.stringWidth(displayTxt) / 2;
            int textY = zoneY + zoneRadius + 12; 
            
            g2.drawString(displayTxt, textX, textY);
          }
        }
      }
      
      private void drawTankCircle(Graphics2D g2, int cx, int cy, int r, int level, int cap, String label, boolean isActive) {
        g2.setColor(new Color(30, 35, 50));
        g2.setStroke(new BasicStroke(3));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        
        double percent = level / (double) cap;
        Color fillColor = Color.CYAN;
        if (percent < 0.3) fillColor = Color.RED;
        else if (isActive) fillColor = Color.GREEN; 
        
        g2.setColor(fillColor);
        float strokeW = Math.max(2, r / 5); 
        g2.setStroke(new BasicStroke(strokeW)); 
        
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(int)(360 * percent));
        
        g2.setColor(Color.WHITE);
        int fontSize = Math.max(8, Math.min(r - 2, 14)); 
        g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        
        String text = String.valueOf(level);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text)/2, cy + 4);
        
        if (r > 15) {
           g2.setColor(new Color(150, 160, 180));
           g2.setFont(new Font("Arial", Font.PLAIN, 8));
           g2.drawString(label, cx - fm.stringWidth(label)/2, cy + r + 10);
        }
      }
    }; return gridPanel;
  }

  private JComponent createControlPanel() {
    JPanel controls = new JPanel(new GridLayout(4, 1, 2, 2)) { 
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(20, 25, 40, 200));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
      }
    };
    
    controls.setBorder(new EmptyBorder(3, 3, 3, 3));

    JLabel lblStatus = new JLabel("CONTROL DECK", SwingConstants.CENTER);
    lblStatus.setFont(new Font("Consolas", Font.BOLD, 10)); 
    lblStatus.setForeground(Color.CYAN);
    lblStatus.setBorder(new EmptyBorder(0, 0, 1, 0)); 

    JButton btnToggle = createStyledButton("⚡ TOGGLE PUMP", new Color(0, 150, 136), new Color(0, 210, 255));
    btnToggle.addActionListener(e -> {
      backend.sendCommand("TOGGLE_PUMP");
      dashboardRepaintAll();
    });

    JButton btnViewZones = createStyledButton(" ZONE ANALYTICS", new Color(100, 50, 150), new Color(180, 80, 220));
    btnViewZones.addActionListener(e -> {
      try {
        ZoneAnalyticsWindow window = new ZoneAnalyticsWindow((RealBackendConnector) backend);
        window.setVisible(true);
      } catch (Exception ex) {}
    });

    JButton btnForceLeak = createStyledButton("💥 SIMULATE BURST", new Color(200, 30, 30), new Color(255, 80, 80));
    btnForceLeak.addActionListener(e -> {
      backend.sendCommand("FORCE_LEAK");
      dashboardRepaintAll(); 
    });

    controls.add(lblStatus);
    controls.add(btnToggle);
    controls.add(btnViewZones);
    controls.add(btnForceLeak);
    
    return controls;
  }

  private JButton createStyledButton(String text, Color c1, Color c2) {
    JButton btn = new JButton(text) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2);
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5); 
        super.paintComponent(g);
      }
    };
    btn.setForeground(Color.WHITE);
    btn.setFont(new Font("Segoe UI", Font.BOLD, 10)); 
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setPreferredSize(new Dimension(0, 22)); 
    
    return btn;
  }
  
  private JComponent createZoneMaintenanceStrip() {
    JPanel strip = new JPanel(new GridLayout(1, 4, 5, 0)) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(15, 20, 30)); 
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    
    strip.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    for (int i = 0; i < 4; i++) {
      final int zoneIndex = i;
      JButton btnFix = new JButton("🔧 FIX FLOOR " + (i+1)) {
        @Override
        protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g;
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          
          Map<String, Object> status = backend.getCurrentStatus();
          Object zonesValue = status.get("zones");
          String currentState = "";
          if (zonesValue instanceof List<?> zones) {
            if (zoneIndex < zones.size() && zones.get(zoneIndex) instanceof Map<?, ?>) {
              Object state = ((Map<?, ?>) zones.get(zoneIndex)).get("state");
              if (state instanceof String stateString) {
                currentState = stateString;
              }
            }
          }
          
          // Visual feedback based on leak status
          Color baseColor;
          if ("LEAK".equals(currentState)) {
             baseColor = new Color(200, 50, 50); 
          } else {
             baseColor = new Color(40, 50, 70); 
          }
          
          GradientPaint gp = new GradientPaint(0, 0, baseColor.darker(), getWidth(), getHeight(), baseColor.brighter());
          g2.setPaint(gp);
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
          
          super.paintComponent(g);
        }
      };
      
      btnFix.setForeground(Color.WHITE);
      btnFix.setFont(new Font("Segoe UI", Font.BOLD, 9)); 
      btnFix.setFocusPainted(false);
      btnFix.setContentAreaFilled(false);
      btnFix.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      btnFix.setPreferredSize(new Dimension(0, 20)); 
      
      btnFix.addActionListener(e -> {
        backend.sendCommand("RESOLVE_ZONE_" + zoneIndex);
        dashboardRepaintAll(); 
      });
      
      strip.add(btnFix);
    }
    
    return strip;
  }

  class LiveChartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final ArrayList<Integer> historyData = new ArrayList<>();
    private static final int MAX_POINTS = 50;

    public LiveChartPanel() { setBackground(new Color(20, 25, 40)); }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int padding = 15;
      int chartW = w - 2 * padding;
      int chartH = h - 2 * padding;

      g2.setColor(Color.CYAN);
      g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
      g2.drawString("📈 MAIN TANK TREND", padding, 15);

      g2.setColor(new Color(50, 60, 80));
      g2.setStroke(new BasicStroke(1));
      g2.drawLine(padding, padding, padding, h - padding);
      g2.drawLine(padding, h - padding, w - padding, h - padding);

      if (historyData.size() > 1) {
        g2.setColor(new Color(0, 210, 255));
        g2.setStroke(new BasicStroke(2));

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
          g2.fillOval(x - 2, y - 2, 4, 4);
          prevX = x;
          prevY = y;
        }
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

  class DiscrepancyChartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient RealBackendConnector rbConn;

    public DiscrepancyChartPanel(RealBackendConnector backend) {
      this.rbConn = backend;
      setBackground(new Color(20, 25, 40));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();

      if (w < 100 || h < 50) return;

      int padding = 15;
      int chartW = w - 2 * padding;
      int chartH = h - 2 * padding;

      g2.setColor(Color.ORANGE);
      g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
      g2.drawString("⚖️ MASS BALANCE ANALYSIS", padding, 15);

      g2.setColor(new Color(50, 60, 80));
      g2.setStroke(new BasicStroke(1));
      g2.drawLine(padding, padding, padding, h - padding);
      g2.drawLine(padding, h - padding, w - padding, h - padding);

      List<Double> inlets = rbConn.getInletFlowHistory();
      List<Double> outlets = rbConn.getOutletFlowHistory();

      if (inlets == null || inlets.isEmpty()) return;

      double maxY = 0;
      for(double val : inlets) if(val > maxY) maxY = val;
      for(double val : outlets) if(val > maxY) maxY = val;
      if(maxY < 10) maxY = 10;
      maxY *= 1.2;

      plotLine(g2, inlets, new Color(0, 210, 255), chartW, chartH, padding, h, maxY);
      plotLine(g2, outlets, new Color(255, 165, 0), chartW, chartH, padding, h, maxY);

      g2.setColor(new Color(30, 35, 50, 200));
      g2.fillRoundRect(w - 100, padding + 5, 85, 30, 4, 4);

      g2.setColor(Color.CYAN);
      g2.fillRect(w - 90, padding + 12, 8, 8);
      g2.setColor(Color.WHITE);
      g2.setFont(new Font("Arial", Font.PLAIN, 9));
      g2.drawString("Outflow", w - 78, padding + 19);

      g2.setColor(Color.ORANGE);
      g2.fillRect(w - 90, padding + 24, 8, 8);
      g2.setColor(Color.WHITE);
      g2.drawString("Inflow", w - 78, padding + 31);
    }

    private void plotLine(Graphics2D g2, List<Double> data, Color color, int cw, int ch, int padL, int totalH, double maxVal) {
      if(data.size() < 2) return;

      g2.setColor(color);
      g2.setStroke(new BasicStroke(2));

      int prevX = -1;
      int prevY = -1;

      for(int i=0; i<data.size(); i++) {
        double val = data.get(i);
        int x = padL + (i * cw / (data.size()-1));
        int y = (totalH - padL) - (int)((val / maxVal) * ch);

        if(i > 0) {
          g2.drawLine(prevX, prevY, x, y);
        }
        prevX = x;
        prevY = y;
      }
    }
  }

  private void startRefreshLoop() {
    refreshTimer = new Timer(2000, e -> {
      SwingUtilities.invokeLater(() -> {
        try {
          Map<String, Object> status = backend.getCurrentStatus();

          int level = (Integer) status.getOrDefault("mainLevel", 0);
          boolean pumpOn = (Boolean) status.getOrDefault("pumpActive", false);
          String healthStr = (String) status.getOrDefault("healthScore", "N/A");
          int alerts = (Integer) status.getOrDefault("activeAlerts", 0);

          if (lblLevelValue != null) lblLevelValue.setText(level + " L");
          if (lblPumpStatus != null) {
            lblPumpStatus.setText(pumpOn ? "RUNNING 🟢" : "IDLE ⚪");
            lblPumpStatus.setForeground(pumpOn ? Color.GREEN : Color.GRAY);
          }
          if (lblHealthValue != null) {
            lblHealthValue.setText(healthStr);
            try {
              if (healthStr.contains("%")) {
                double val = Double.parseDouble(healthStr.replace("%", "").trim());
                if (val >= 80) lblHealthValue.setForeground(Color.GREEN);
                else if (val >= 50) lblHealthValue.setForeground(Color.ORANGE);
                else lblHealthValue.setForeground(Color.RED);
              }
            } catch (NumberFormatException ex) {}
          }
          if (lblAlertsValue != null) {
            lblAlertsValue.setText(String.valueOf(alerts));
            if (alerts == 0) lblAlertsValue.setForeground(Color.GREEN);
            else if (alerts <= 2) lblAlertsValue.setForeground(Color.ORANGE);
            else lblAlertsValue.setForeground(Color.RED);
          }

          if (chartPanel != null) chartPanel.addDataPoint(level);

          mainContainer.repaint();

        } catch (Exception ex) {
          System.err.println("UI Refresh Error: " + ex.getMessage());
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
        UIManager.put("Panel.background", new Color(10, 15, 30));
        UIManager.put("Label.foreground", Color.WHITE);
      } catch (ClassNotFoundException | InstantiationException
          | IllegalAccessException | UnsupportedLookAndFeelException e) {
        System.err.println("Unable to set the look and feel: " + e.getMessage());
      }

      Main app = new Main();
      app.backend.setAlertListener(app);
      app.setVisible(true);
    });
  }
}