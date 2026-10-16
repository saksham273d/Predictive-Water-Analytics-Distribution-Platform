import backend.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.swing.*; 

public class Main extends JFrame implements AlertListener {
  
  private JLabel lblPumpStatus; 
  private JLabel lblTotalVolumeValue;
  private JLabel lblActivePumpsValue;
  private JLabel lblAvgLevelValue;
  private JLabel lblAlertsCountValue;
  
  private static BackendConnector backend;
  private JPanel dashboardPanel;
  private Timer refreshTimer;

  public Main() {
    super("💧 Smart Water Monitoring System");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(1200, 800); 
    setLocationRelativeTo(null);
    
    backend = new RealBackendConnector(); 
    backend.setAlertListener(this);

    initUI();
    startRefreshLoop();
  }

  private void initUI() {
    setLayout(new BorderLayout());
    add(createHeaderBar(), BorderLayout.NORTH);

    dashboardPanel = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, new Color(15, 20, 35), 0, getHeight(), new Color(10, 15, 25));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    dashboardPanel.setBorder(BorderFactory.createEmptyBorder());

    JScrollPane scrollPane = new JScrollPane(dashboardPanel);
    scrollPane.setBorder(null);
    scrollPane.getViewport().setBackground(new Color(15, 20, 35)); 
    
    add(scrollPane, BorderLayout.CENTER);
    buildCards();
  }

  private JPanel createHeaderBar() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
    panel.setBackground(new Color(20, 25, 40));
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 45, 60)));
    
    JLabel title = new JLabel("💧 WATER ANALYTICS DASHBOARD");
    title.setFont(new Font("Segoe UI", Font.BOLD, 24));
    title.setForeground(Color.CYAN);
    
    JLabel status = new JLabel("LIVE MONITORING ACTIVE");
    status.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    status.setForeground(new Color(100, 255, 100));
    
    panel.add(title);
    panel.add(Box.createHorizontalGlue());
    panel.add(status);
    return panel;
  }

  private void buildCards() {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;

    // Row 1 Stats
    gbc.gridx = 0; gbc.gridy = 0;
    JPanel card1 = createDynamicStatCard("TOTAL VOLUME", "-- L", Color.CYAN);
    Component[] comps1 = card1.getComponents();
    if(comps1.length > 1 && comps1[1] instanceof JLabel) lblTotalVolumeValue = (JLabel) comps1[1];
    dashboardPanel.add(card1, gbc);
    
    gbc.gridx++;
    JPanel card2 = createDynamicStatCard("ACTIVE PUMPS", "OFF", Color.GREEN);
    Component[] comps2 = card2.getComponents();
    if(comps2.length > 1 && comps2[1] instanceof JLabel) lblActivePumpsValue = (JLabel) comps2[1];
    dashboardPanel.add(card2, gbc);
    
    gbc.gridx++;
    JPanel card3 = createDynamicStatCard("AVG LEVEL", "--%", Color.YELLOW);
    Component[] comps3 = card3.getComponents();
    if(comps3.length > 1 && comps3[1] instanceof JLabel) lblAvgLevelValue = (JLabel) comps3[1];
    dashboardPanel.add(card3, gbc);
    
    gbc.gridx++;
    JPanel card4 = createDynamicStatCard("ALERTS", "0", Color.RED);
    Component[] comps4 = card4.getComponents();
    if(comps4.length > 1 && comps4[1] instanceof JLabel) lblAlertsCountValue = (JLabel) comps4[1];
    dashboardPanel.add(card4, gbc);

    // Row 2 Tank
    gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 2;
    dashboardPanel.add(createTankVisualizer(), gbc);

    // Row 3 Chart
    gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 2; gbc.gridheight = 1;
    dashboardPanel.add(createChartPlaceholder(), gbc);

    // Row 4 Controls
    gbc.gridx = 2; gbc.gridy = 2; gbc.gridheight = 1;
    dashboardPanel.add(createControlPanel(), gbc);
  }

  private JPanel createDynamicStatCard(String label, String value, Color accent) {
    JPanel card = new JPanel(new GridLayout(2, 1)) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(25, 30, 45)); 
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(4));
        g2.drawLine(10, 2, getWidth() - 10, 2);
      }
    };
    card.setPreferredSize(new Dimension(200, 100));
    
    JLabel lblTitle = new JLabel(label);
    lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
    lblTitle.setForeground(Color.GRAY);
    lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
    
    JLabel lblValue = new JLabel(value);
    lblValue.setFont(new Font("Segoe UI", Font.BOLD, 28));
    lblValue.setForeground(accent);
    lblValue.setHorizontalAlignment(SwingConstants.CENTER);
    
    card.add(lblTitle);
    card.add(lblValue);
    return card;
  }

  private JComponent createTankVisualizer() {
    JPanel tankPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int radius = Math.min(w, h) / 3;
        
        g2.setColor(new Color(40, 45, 60));
        g2.setStroke(new BasicStroke(10));
        g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        
        Map<String, Object> status = backend.getCurrentStatus();
        int level = (Integer) status.get("level");
        double percent = level / 10000.0;
        
        Color fillColor = Color.GREEN;
        if (percent < 0.3) fillColor = Color.RED;
        else if (percent < 0.6) fillColor = Color.ORANGE;
        
        g2.setColor(fillColor);
        g2.setStroke(new BasicStroke(15));
        g2.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, 90, -(int)(360 * percent));
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        String text = level + " L";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text)/2, cy + 10);
        
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.setColor(Color.GRAY);
        g2.drawString("MAIN TANK", cx - fm.stringWidth("MAIN TANK")/2, cy + 30);
      }
    };
    tankPanel.setPreferredSize(new Dimension(300, 300));
    return tankPanel;
  }

  private JComponent createChartPlaceholder() {
    JPanel chartArea = new JPanel(new BorderLayout()) {
       @Override
       protected void paintComponent(Graphics g) {
         Graphics2D g2 = (Graphics2D) g;
         g2.setColor(new Color(25, 30, 45));
         g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
         
         g2.setColor(Color.CYAN);
         g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
         g2.drawString("📈 LIVE USAGE TREND", 20, 30);
       }
    };
    
    JButton btnShowHistory = new JButton("View Detailed History");
    btnShowHistory.addActionListener(e -> showHistoryChart()); 
    
    chartArea.add(btnShowHistory, BorderLayout.SOUTH);
    return chartArea;
  }

  private JComponent createControlPanel() {
    JPanel controls = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(new Color(25, 30, 45));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
      }
    };

    JLabel header = new JLabel("⚙️ SYSTEM CONTROLS");
    header.setFont(new Font("Segoe UI", Font.BOLD, 14));
    header.setForeground(Color.CYAN);
    header.setHorizontalAlignment(SwingConstants.CENTER);
    controls.add(header, BorderLayout.NORTH);

    JPanel centerPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    
    lblPumpStatus = new JLabel("STATUS: IDLE", SwingConstants.CENTER);
    lblPumpStatus.setFont(new Font("Consolas", Font.BOLD, 16));
    lblPumpStatus.setForeground(Color.GRAY);
    centerPanel.add(lblPumpStatus);

    JButton btnTogglePump = new JButton("FORCE TOGGLE PUMP");
    btnTogglePump.setBackground(new Color(40, 45, 60));
    btnTogglePump.setForeground(Color.WHITE);
    btnTogglePump.setFocusPainted(false);
    btnTogglePump.addActionListener(e -> {
      backend.sendCommand("TOGGLE_PUMP"); 
      dashboardPanel.repaint(); 
    });
    centerPanel.add(btnTogglePump);

    controls.add(centerPanel, BorderLayout.CENTER);
    return controls;
  }
  
  private void startRefreshLoop() {
    refreshTimer = new Timer(2000, e -> {
      SwingUtilities.invokeLater(() -> {
        Map<String, Object> status = backend.getCurrentStatus();
        
        int level = (Integer) status.get("level");
        boolean pumpOn = (Boolean) status.getOrDefault("pumpActive", false);
        int capacity = (Integer) status.get("capacity");
        int percent = (level * 100) / capacity;

        if (lblTotalVolumeValue != null) lblTotalVolumeValue.setText(level + " L");
        if (lblActivePumpsValue != null) {
          lblActivePumpsValue.setText(pumpOn ? "1 / 1" : "0 / 1");
          lblActivePumpsValue.setForeground(pumpOn ? Color.GREEN : Color.GRAY);
        }
        if (lblAvgLevelValue != null) lblAvgLevelValue.setText(percent + "%");
        
        if (lblPumpStatus != null) {
          if (pumpOn) {
            lblPumpStatus.setText("STATUS: RUNNING 🟢");
            lblPumpStatus.setForeground(Color.GREEN);
          } else {
            lblPumpStatus.setText("STATUS: IDLE ⚪");
            lblPumpStatus.setForeground(Color.GRAY);
          }
        }
        
        dashboardPanel.repaint(); 
      });
    });
    refreshTimer.start();
  }

  private void showHistoryChart() {
    JFrame chartFrame = new JFrame("📈 Live Usage History");
    chartFrame.setSize(800, 500);
    chartFrame.setLocationRelativeTo(this);
    chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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

        g2.setColor(new Color(15, 20, 35)); 
        g2.fillRect(0, 0, width, height);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.drawString("Water Level Trend (Last 24 Hours)", padding, 30);

        List<Map<String, Object>> history = backend.getHistory(24);
        if (history == null || history.isEmpty()) {
          g2.drawString("No Data Available", padding, height/2);
          return;
        }

        int maxVal = 10000; 
        
        g2.setColor(new Color(60, 60, 80));
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(padding, padding, padding, height - padding);
        g2.drawLine(padding, height - padding, width - padding, height - padding);

        g2.setColor(Color.GREEN); 
        g2.setStroke(new BasicStroke(3));
        
        int prevX = padding;
        int prevY = height - padding;

        for (int i = 0; i < history.size(); i++) {
          Map<String, Object> reading = history.get(i);
          int level = (Integer) reading.get("level");
          
          int x = padding + (i * chartW / (history.size() - 1));
          int y = height - padding - (level * chartH / maxVal);

          if (i > 0) {
            g2.drawLine(prevX, prevY, x, y);
          }
          g2.fillOval(x - 3, y - 3, 6, 6);
          
          prevX = x;
          prevY = y;
        }
      }
    };

    chartFrame.add(chartPanel);
    chartFrame.setVisible(true);

    Timer chartTimer = new Timer(10000, e -> chartPanel.repaint());
    chartTimer.start();
    
    chartFrame.addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosed(java.awt.event.WindowEvent e) { chartTimer.stop(); }
    });
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
        Color bgColor = new Color(15, 20, 35);
        Color textColor = Color.WHITE;
        
        UIManager.put("Panel.background", bgColor);
        UIManager.put("Label.foreground", textColor);
        UIManager.put("Button.background", new Color(40, 45, 60));
        UIManager.put("Button.foreground", textColor);
        UIManager.put("ScrollPane.background", bgColor);
        UIManager.put("Viewport.background", bgColor);
        UIManager.put("TextArea.background", new Color(25, 30, 45));
        UIManager.put("TextArea.foreground", textColor);
        
      } catch (Exception e) {
        e.printStackTrace();
      }
      
      Main app = new Main();
      app.setVisible(true);
    });
  }
}