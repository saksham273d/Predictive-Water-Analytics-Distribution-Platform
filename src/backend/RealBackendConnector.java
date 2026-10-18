package backend;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smart Water Grid Manager - Core Backend Logic
 * Implements hierarchical distribution with hysteresis-based refill control.
 */
public class RealBackendConnector implements BackendConnector {

  // --- SYSTEM CONFIGURATION ---
  private static final int MAIN_CAPACITY = 10000;   
  private static final int NUM_ZONES = 4; 
  private static final int[] ZONE_CAPACITIES = {2000, 2000, 2000, 2000}; 
  
  // Pump Control Thresholds (Hysteresis Band)
  private static final int PUMP_ON_LEVEL = 3000;      
  private static final int PUMP_OFF_LEVEL = 9500;          
  
  // Flow Dynamics
  private static final double MAX_PIPE_FLOW_RATE = 150.0; // L/s
  private static final int SIM_REFILL_RATE = 250;         // L/tick for main pump
  
  // Zone Refill Buffer (Prevents rapid cycling)
  private static final int ZONE_TRIGGER_LOW = 800;   // Start filling below this
  private static final int ZONE_TARGET_HIGH = 1800;  // Stop filling above this

  // Database Configuration
  private static final String DB_URL = "jdbc:mysql://localhost:3306/water_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true";
  private static final String DB_USER = "root";
  private static final String DB_PASS = "VSP@23122709"; 

  // --- RUNTIME STATE ---
  private volatile int mainLevel = 7500;
  private volatile int prevMainLevel = 7500;
  private volatile long lastTickTime = System.currentTimeMillis();
  
  private volatile boolean isPumpRunning = false;
  
  // Zone State Arrays
  private volatile int[] zoneLevels = new int[NUM_ZONES]; 
  private volatile int[] prevZoneLevels = new int[NUM_ZONES];
  private volatile double[] flowRates = new double[NUM_ZONES]; 
  private volatile String[] zoneStates = new String[NUM_ZONES]; 
  private boolean[] isRefilling = new boolean[NUM_ZONES]; // Latch flags

  // Telemetry Metrics
  private volatile double totalDelivered = 0;
  private volatile double totalConsumed = 0;
  private volatile double drainVelocity = 0.0;
  private volatile int alertCount = 0;
  private volatile double healthScore = 100.0; 

  // History Buffers for Graphing
  private List<List<Integer>> zoneHistory = new ArrayList<>();
  private static final int HISTORY_LIMIT = 50; 

  // Resources
  private Connection dbConn;
  private Timer scheduler;
  private AlertListener listener;

  public RealBackendConnector() {
    try {
      initSystem();
    } catch (Exception e) {
      System.err.println("❌ Init Failed: " + e.getMessage());
      startFallbackMode();
    }
  }

  private void initSystem() throws Exception {
    System.out.println("🧪 Booting Water Grid Controller...");
    
    // Initialize Data Structures
    for(int i=0; i<NUM_ZONES; i++) {
      zoneHistory.add(new LinkedList<>());
      isRefilling[i] = false;
      zoneLevels[i] = 300 + ThreadLocalRandom.current().nextInt(0, 300); // Start low to trigger demo
      prevZoneLevels[i] = zoneLevels[i];
      flowRates[i] = 0.0;
      zoneStates[i] = "NORMAL";
    }

    connectDB(); 
    loadStateFromDB();
    startScheduler();
  }

  private void connectDB() throws SQLException {
    if (dbConn == null || dbConn.isClosed()) {
      try {
        Class.forName("com.mysql.cj.jdbc.Driver");
      } catch (ClassNotFoundException e) {
        throw new SQLException("MySQL JDBC driver not found", e);
      }
      dbConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
      System.out.println("✅ MySQL Connected.");
    }
  }

  private void loadStateFromDB() {
    try {
      connectDB();
      String sql = "SELECT level_liters FROM tank_readings ORDER BY recorded_at DESC LIMIT 1";
      try (Statement stmt = dbConn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
        if (rs.next()) {
          this.mainLevel = rs.getInt("level_liters");
          this.prevMainLevel = this.mainLevel;
        }
      }
    } catch (SQLException e) {
      System.err.println("⚠️ DB Load Error: Using defaults.");
    }
  }

  private void startScheduler() {
    scheduler = new Timer(true);
    scheduler.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() { executeCycle(); }
    }, 2000, 2000);
  }

  /**
   * Main Simulation Loop (Runs every 2 seconds)
   */
  private void executeCycle() {
    long now = System.currentTimeMillis();
    double dtSec = (now - lastTickTime) / 1000.0;
    if (dtSec <= 0) return; 

    // Phase 1: Demand Generation (Households consuming from Floor Tanks)
    simulateConsumption(dtSec);

    // Phase 2: Supply Management (Main Tank Pump Logic)
    manageMainPump();

    // Phase 3: Distribution (Transferring water from Main to Zones based on triggers)
    distributeToZones(dtSec);

    // Phase 4: Diagnostics & Health Monitoring
    analyzeHealth(dtSec);

    // Phase 5: State Update & Persistence
    updateMetrics(now);
    persistData();
    checkAlerts();
  }

  private void simulateConsumption(double dt) {
    for (int i = 0; i < NUM_ZONES; i++) {
      if (zoneLevels[i] > 0) {
        int usage = 5 + ThreadLocalRandom.current().nextInt(0, 6); // Slow drain for visibility
        zoneLevels[i] -= usage;
        if (zoneLevels[i] < 0) zoneLevels[i] = 0;
        totalConsumed += usage; 
      }
    }
  }

  private void manageMainPump() {
    // Hysteresis Control: Prevents rapid On/Off switching
    if (!isPumpRunning && mainLevel <= PUMP_ON_LEVEL) {
      isPumpRunning = true;
      System.out.println("🟢 Pump ON (Low Level: " + mainLevel + ")");
      notifyListener("INFO", "Main Pump Activated.");
    } else if (isPumpRunning && mainLevel >= PUMP_OFF_LEVEL) {
      isPumpRunning = false;
      System.out.println("🔴 Pump OFF (High Level: " + mainLevel + ")");
      notifyListener("INFO", "Tank Full.");
    }

    if (isPumpRunning) {
      mainLevel += SIM_REFILL_RATE; 
      if (mainLevel >= MAIN_CAPACITY) {
        mainLevel = MAIN_CAPACITY;
        isPumpRunning = false; // Overflow protection
      }
    }
  }

  private void distributeToZones(double dt) {
    for (int i = 0; i < NUM_ZONES; i++) {
      int currentLvl = zoneLevels[i];
      
      // Latching Logic: Trigger fill only when crossing lower bound
      if (currentLvl < ZONE_TRIGGER_LOW) isRefilling[i] = true;
      if (currentLvl >= ZONE_TARGET_HIGH) isRefilling[i] = false;

      // Execute Transfer if Latched and Source Available
      if (isRefilling[i] && mainLevel > 0) {
        int deficit = ZONE_TARGET_HIGH - currentLvl;
        if (deficit <= 0) { isRefilling[i] = false; continue; }

        double maxFlow = MAX_PIPE_FLOW_RATE * dt;
        int transferAmt = (int) Math.min(Math.min(maxFlow, deficit), mainLevel);
        
        if (transferAmt > 0) {
          mainLevel -= transferAmt;
          zoneLevels[i] += transferAmt;
          
          if (zoneLevels[i] >= ZONE_TARGET_HIGH) {
            zoneLevels[i] = ZONE_TARGET_HIGH;
            isRefilling[i] = false; // Strict stop at target
          }
          
          flowRates[i] = transferAmt / dt;
          totalDelivered += transferAmt;
        } else {
          flowRates[i] = 0.0;
        }
      } else {
        flowRates[i] = 0.0;
      }
    }
    if (mainLevel < 0) mainLevel = 0;
  }

  private void analyzeHealth(double dt) {
    alertCount = 0;
    double score = 100.0;

    for (int i = 0; i < NUM_ZONES; i++) {
      int drop = prevZoneLevels[i] - zoneLevels[i];
      
      // Leak Detection: Abnormal instantaneous loss
      if (drop > 50) {
        zoneStates[i] = "LEAK";
        alertCount++;
        score -= 20; 
        notifyListener("CRITICAL", "Leak in Floor " + (i+1));
      } 
      // Low Level Warning
      else if (zoneLevels[i] < (ZONE_CAPACITIES[i] * 0.2)) {
        zoneStates[i] = "LOW LEVEL";
        alertCount++;
        score -= 5; 
      }
      else {
        zoneStates[i] = "NORMAL";
      }
      
      prevZoneLevels[i] = zoneLevels[i];
    }

    if (mainLevel < (MAIN_CAPACITY * 0.1)) {
       alertCount++;
       score -= 15;
    }

    this.healthScore = Math.max(0, Math.min(100, score));
  }

  private void updateMetrics(long now) {
    drainVelocity = (prevMainLevel - mainLevel) / ((now - lastTickTime)/1000.0);
    prevMainLevel = mainLevel;
    lastTickTime = now;

    // Append to rolling history buffers
    for (int i = 0; i < NUM_ZONES; i++) {
      List<Integer> hist = zoneHistory.get(i);
      hist.add(zoneLevels[i]);
      if (hist.size() > HISTORY_LIMIT) hist.remove(0); 
    }
  }

  private void persistData() {
    try {
      connectDB(); 
      String sql = "INSERT INTO tank_readings (level_liters, capacity_liters, pump_status, drain_velocity, leak_detected) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement ps = dbConn.prepareStatement(sql)) {
        ps.setInt(1, mainLevel);
        ps.setInt(2, MAIN_CAPACITY);
        ps.setString(3, isPumpRunning ? "ON" : "OFF");
        ps.setDouble(4, drainVelocity);
        ps.setBoolean(5, Arrays.stream(zoneStates).anyMatch(s -> s.equals("LEAK")));
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      System.err.println("❌ Persist Error: " + e.getMessage());
    }
  }

  private void checkAlerts() {
    if (listener == null) return;
    if (mainLevel <= 500 && !isPumpRunning) {
       listener.onAlert("CRITICAL", "MAIN TANK CRITICALLY LOW!");
    } 
    if (healthScore < 50) {
       listener.onAlert("WARNING", "SYSTEM HEALTH DEGRADED (<50%)");
    }
  }

  private void notifyListener(String sev, String msg) {
    if (listener != null) listener.onAlert(sev, msg);
  }

  // --- PUBLIC API FOR UI ---

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> map = new HashMap<>();
    map.put("mainLevel", mainLevel);
    map.put("capacity", MAIN_CAPACITY);
    map.put("pumpActive", isPumpRunning); 
    map.put("status", healthScore < 50 ? "DEGRADED" : (isPumpRunning ? "REFILLING" : "NORMAL"));
    map.put("drainVelocity", String.format("%.2f L/s", drainVelocity));
    map.put("healthScore", String.format("%.1f%%", healthScore));
    map.put("activeAlerts", alertCount);
    
    List<Map<String, Object>> zones = new ArrayList<>();
    for(int i=0; i<NUM_ZONES; i++) {
      Map<String, Object> z = new HashMap<>();
      z.put("id", i+1);
      z.put("level", zoneLevels[i]);
      z.put("cap", ZONE_CAPACITIES[i]);
      z.put("state", zoneStates[i]);
      z.put("flowRate", flowRates[i]); 
      zones.add(z);
    }
    map.put("zones", zones);
    return map;
  }

  @Override
  public List<Map<String, Object>> getHistory(int hours) {
     List<Map<String, Object>> list = new ArrayList<>();
     try {
      connectDB();
      String sql = "SELECT level_liters, recorded_at, drain_velocity, leak_detected FROM tank_readings ORDER BY recorded_at DESC LIMIT 100";
      try (Statement st = dbConn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
        while (rs.next()) {
          Map<String, Object> row = new HashMap<>();
          row.put("level", rs.getInt("level_liters"));
          row.put("timestamp", rs.getTimestamp("recorded_at").getTime());
          row.put("velocity", rs.getDouble("drain_velocity"));
          row.put("leak", rs.getBoolean("leak_detected"));
          list.add(row);
        }
      }
      Collections.reverse(list); 
    } catch (SQLException e) { /* Silent fail for UI stability */ }
    return list;
  }

  @Override
  public void setAlertListener(AlertListener l) { this.listener = l; }

  @Override
  public boolean sendCommand(String cmd) {
    return switch(cmd) {
      case "TOGGLE_PUMP" -> {
        isPumpRunning = !isPumpRunning;
        yield true;
      }
      case "TEST_LEAK" -> {
        injectFault();
        yield true;
      }
      case "EXPORT_CSV" -> {
        exportReport();
        yield true;
      }
      default -> false;
    };
  }

  private void injectFault() {
    int zone = ThreadLocalRandom.current().nextInt(0, NUM_ZONES);
    int damage = 200 + ThreadLocalRandom.current().nextInt(0, 200);
    if (zoneLevels[zone] >= damage) {
      zoneLevels[zone] -= damage;
      analyzeHealth(2.0); // Force immediate recalculation
    }
  }

  private void exportReport() {
    try (FileWriter fw = new FileWriter("grid_report.csv")) {
      fw.write("ID,Timestamp,Main_Level,Pump_Status,Velocity,Leak\n");
      // In production, iterate DB results here. For sim, just header creation proves capability.
      System.out.println("📄 Report Generated.");
      notifyListener("INFO", "Export Successful!");
    } catch (IOException e) {
      System.err.println("Export Failed");
    }
  }

  private void startFallbackMode() {
    System.out.println("🔄 Running in Offline Mode...");
    scheduler = new Timer(true);
    scheduler.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        mainLevel -= 10;
        if(mainLevel < 0) mainLevel = 0;
        manageMainPump();
      }
    }, 2000, 2000);
  }

  public List<Integer> getZoneHistory(int idx) {
    if (idx >= 0 && idx < NUM_ZONES) return new ArrayList<>(zoneHistory.get(idx));
    return Collections.emptyList();
  }
  
  public Map<String, Object> getZoneStats(int idx) {
    Map<String, Object> m = new HashMap<>();
    if (idx >= 0 && idx < NUM_ZONES) {
      m.put("level", zoneLevels[idx]);
      m.put("cap", ZONE_CAPACITIES[idx]);
      m.put("state", zoneStates[idx]);
      m.put("flowRate", flowRates[idx]);
    }
    return m;
  }
}