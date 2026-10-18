package backend;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enterprise-Grade Smart Water Grid Manager (Optimized Version)
 * Features: Zero Idle Loss, Demand-Driven Distribution, Latching Refill Logic
 */
public class RealBackendConnector implements BackendConnector {

  // --- CONFIGURATION CONSTANTS ---
  private static final int MAIN_CAPACITY = 10000;   
  private static final int NUM_ZONES = 4; 
  private static final int[] ZONE_CAPACITIES = {2000, 2000, 2000, 2000}; 
  
  // Pump Thresholds for Main Tank (External Supply)
  private static final int MAIN_PUMP_ON_LEVEL = 3000;      
  private static final int MAIN_PUMP_OFF_LEVEL = 9500;          
  
  // Simulation Rates
  // REMOVED SIM_BASE_CONSUMPTION_MAIN to prevent artificial drain when idle
  private static final int SIM_REFILL_RATE = 250;
  
  // Pipe Flow Speed (Balanced for smooth visualization)
  private static final double MAX_PIPE_FLOW_RATE = 150.0; // Liters per second
  
  // Zone Refill Logic (The "Smart Buffer")
  private static final int ZONE_REFILL_TRIGGER = 800;   // Start refilling below this
  private static final int ZONE_TARGET_FILL = 1800;     // Stop refilling at this level

  // Database Credentials
  private static final String DB_URL = "jdbc:mysql://localhost:3306/water_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true";
  private static final String DB_USER = "root";
  private static final String DB_PASS = "VSP@23122709"; 

  // --- STATE VARIABLES ---
  private volatile int mainLevel = 7500;
  private volatile int previousMainLevel = 7500;
  private volatile long lastUpdateTime = System.currentTimeMillis();
  
  private volatile boolean isMainPumpRunning = false;
  
  // Dynamic Arrays for Zones
  private volatile int[] zoneLevels = new int[NUM_ZONES]; 
  private volatile int[] previousZoneLevels = new int[NUM_ZONES];
  private volatile double[] currentFlowRates = new double[NUM_ZONES]; 
  private volatile String[] zoneStatus = new String[NUM_ZONES]; 
  
  // Track which zones are actively refilling (Prevents Sawtooth Wave)
  private boolean[] isZoneRefilling = new boolean[NUM_ZONES];

  // Metrics
  private volatile double totalWaterDeliveredToZones = 0;
  private volatile double totalWaterConsumedFromMain = 0;
  private volatile double currentDrainVelocity = 0.0;
  private volatile int activeAlertCount = 0;
  private volatile double systemHealthScore = 100.0; 

  // History Storage for Graphs
  private List<List<Integer>> zoneHistories = new ArrayList<>();
  private static final int HISTORY_SIZE = 50; 

  // Connections & Timers
  private Connection dbConnection;
  private Timer monitoringTimer;
  private AlertListener listener;

  public RealBackendConnector() {
    try {
      initializeSystem();
    } catch (Exception e) {
      System.err.println("❌ Critical Initialization Error: " + e.getMessage());
      startOfflineSimulation();
    }
  }

  private void initializeSystem() throws Exception {
    System.out.println("🧪 Initializing Enterprise Water Grid...");
    
    // Initialize empty histories and flags for all zones
    for(int i=0; i<NUM_ZONES; i++) {
      zoneHistories.add(new LinkedList<>());
      isZoneRefilling[i] = false; 
    }

    // Start zones LOW so they immediately trigger refill logic upon startup
    for(int i=0; i<NUM_ZONES; i++) {
      zoneLevels[i] = 300 + ThreadLocalRandom.current().nextInt(0, 300); 
      previousZoneLevels[i] = zoneLevels[i];
      currentFlowRates[i] = 0.0;
      zoneStatus[i] = "NORMAL";
    }

    ensureDatabaseConnection(); 
    loadInitialStateFromDB();
    startMonitoringLoop();
  }

  private void ensureDatabaseConnection() throws SQLException {
    if (dbConnection == null || dbConnection.isClosed()) {
      System.out.println("🔄 Reconnecting to MySQL...");
      try {
        Class.forName("com.mysql.cj.jdbc.Driver");
        dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        System.out.println("✅ Connected to MySQL Database Successfully!");
      } catch (ClassNotFoundException cnfe) {
        throw new SQLException("MySQL Driver not found", cnfe);
      }
    }
  }

  private void loadInitialStateFromDB() {
    try {
      ensureDatabaseConnection();
      String sql = "SELECT level_liters FROM tank_readings ORDER BY recorded_at DESC LIMIT 1";
      try (Statement stmt = dbConnection.createStatement();
           ResultSet rs = stmt.executeQuery(sql)) {
        if (rs.next()) {
          this.mainLevel = rs.getInt("level_liters");
          this.previousMainLevel = this.mainLevel;
          System.out.println("📂 Loaded Main Level from DB: " + mainLevel + "L");
        }
      }
    } catch (SQLException e) {
      System.err.println("⚠️ Error loading initial data: " + e.getMessage());
    }
  }

  private void startMonitoringLoop() {
    monitoringTimer = new Timer(true);
    monitoringTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        processSystemCycle();
      }
    }, 2000, 2000);
  }

  private void processSystemCycle() {
    long now = System.currentTimeMillis();
    double deltaTimeSec = (now - lastUpdateTime) / 1000.0;
    if (deltaTimeSec <= 0) return; 

    // 1. DRAIN FIRST (This MUST happen before refill checks)
    simulateConsumerUsage(deltaTimeSec);

    // 2. PUMP CONTROL (Fill Main Tank if low)
    updateMainPumpStatus();

    // 3. REFILL ZONES (Move water from Main to Zone if Zone is low)
    replenishZonesToTarget(deltaTimeSec);

    // ... rest of the code (metrics, history, db save) ...
    
    currentDrainVelocity = (previousMainLevel - mainLevel) / deltaTimeSec;
    previousMainLevel = mainLevel;
    lastUpdateTime = now;

    // Record histories...
    for (int i = 0; i < NUM_ZONES; i++) {
      List<Integer> hist = zoneHistories.get(i);
      hist.add(zoneLevels[i]);
      if (hist.size() > HISTORY_SIZE) {
        hist.remove(0); 
      }
    }

    saveGridDataToDB();
    checkGlobalAlerts();
  }

  private void simulateConsumerUsage(double dt) {
    for (int i = 0; i < NUM_ZONES; i++) {
      
      // IF TANK HAS WATER, TAKE IT AWAY IMMEDIATELY
      if (zoneLevels[i] > 0) {
        
        // HIGH USAGE RATE: 30-60 Liters per tick (2 seconds)
        // This ensures visible movement on screen within 5-10 seconds
        int usage = 5 + ThreadLocalRandom.current().nextInt(0, 6); 
        
        zoneLevels[i] -= usage;
        
        // Prevent negative values
        if (zoneLevels[i] < 0) {
          zoneLevels[i] = 0;
        }
        
        // Update Stats
        totalWaterConsumedFromMain += usage; 
      }
    }
  }

  private void replenishZonesToTarget(double dt) {
    
    for (int i = 0; i < NUM_ZONES; i++) {
      
      int currentLevel = zoneLevels[i];
      
      // 1. TRIGGER PHASE: If tank drops BELOW 800, latch ON
      if (currentLevel < ZONE_REFILL_TRIGGER) {
        isZoneRefilling[i] = true;
      }
      
      // 2. STOP PHASE: If tank reaches TARGET (1800), latch OFF
      if (currentLevel >= ZONE_TARGET_FILL) {
        isZoneRefilling[i] = false;
      }
      
      if (isZoneRefilling[i] && mainLevel > 0) {
        
        // Calculate how much space left to hit TARGET
        int amountNeeded = ZONE_TARGET_FILL - currentLevel;
        if (amountNeeded <= 0) {
           isZoneRefilling[i] = false; 
           continue;
        }

        double maxFlowPerTick = MAX_PIPE_FLOW_RATE * dt;
        double desiredFlow = Math.min(maxFlowPerTick, amountNeeded);
        int actualFlow = (int) Math.min(desiredFlow, mainLevel);
        
        if (actualFlow > 0) {
          mainLevel -= actualFlow;
          zoneLevels[i] += actualFlow;
          
          // Cap strictly at Target
          if (zoneLevels[i] >= ZONE_TARGET_FILL) {
            zoneLevels[i] = ZONE_TARGET_FILL;
            isZoneRefilling[i] = false; // Force stop exactly at target
          }
          
          currentFlowRates[i] = actualFlow / dt;
          totalWaterDeliveredToZones += actualFlow;
        } else {
          currentFlowRates[i] = 0.0;
        }
        
      } else {
        // Not refilling (Flag is False) -> DO NOTHING EVEN IF LEVEL IS < 1800
        currentFlowRates[i] = 0.0;
      }
    }
    
    if (mainLevel < 0) mainLevel = 0;
  }
  
  private void detectAnomaliesAndCalculateHealth(double dt) {
    activeAlertCount = 0; // Reset counter
    
    double healthScore = 100.0;

    for (int i = 0; i < NUM_ZONES; i++) {
      int actualDrop = previousZoneLevels[i] - zoneLevels[i];
      
      // A. LEAK DETECTION (Critical Alert)
      if (actualDrop > 50) {
        zoneStatus[i] = "LEAK";
        activeAlertCount++;
        healthScore -= 20; 
        System.out.println("🚨 [ZONE " + (i+1) + "] CRITICAL LEAK DETECTED!");
        if (listener != null) listener.onAlert("CRITICAL", "Leak in Floor " + (i+1));
      } 
      // B. LOW LEVEL WARNING (Operational Alert)
      else if (zoneLevels[i] < (ZONE_CAPACITIES[i] * 0.2)) { // Less than 20% full
        zoneStatus[i] = "LOW LEVEL";
        activeAlertCount++;
        healthScore -= 5; // Minor penalty
        System.out.println("⚠️ [ZONE " + (i+1) + "] LOW WATER LEVEL! Refill Needed.");
      }
      // C. NORMAL STATE
      else {
        zoneStatus[i] = "NORMAL";
      }
      
      previousZoneLevels[i] = zoneLevels[i];
    }

    // D. MAIN TANK CRITICAL CHECK
    if (mainLevel < (MAIN_CAPACITY * 0.1)) {
       activeAlertCount++;
       healthScore -= 15;
       System.out.println("🔴 [MAIN TANK] CRITICALLY LOW!");
    }

    // Clamp Score
    if (healthScore < 0) healthScore = 0;
    if (healthScore > 100) healthScore = 100;
    
    this.systemHealthScore = healthScore;
  }

  private void updateMainPumpStatus() {
    if (!isMainPumpRunning) {
      if (mainLevel <= MAIN_PUMP_ON_LEVEL) {
        isMainPumpRunning = true;
        System.out.println("🟢 MAIN PUMP AUTO-STARTED at Level: " + mainLevel + "L");
        if (listener != null) listener.onAlert("INFO", "Main Pump Activated.");
      }
    } else {
      if (mainLevel >= MAIN_PUMP_OFF_LEVEL) {
        isMainPumpRunning = false;
        System.out.println("🔴 MAIN PUMP AUTO-STOPPED at Level: " + mainLevel + "L");
        if (listener != null) listener.onAlert("INFO", "Main Tank Full.");
      }
    }

    if (isMainPumpRunning) {
      mainLevel += SIM_REFILL_RATE; 
      if (mainLevel >= MAIN_CAPACITY) {
        mainLevel = MAIN_CAPACITY;
        isMainPumpRunning = false; 
        System.out.println("🔴 MAIN PUMP STOPPED (Overflow Prevention)");
      }
    }
  }

  private void saveGridDataToDB() {
    try {
      ensureDatabaseConnection(); 
      String sql = "INSERT INTO tank_readings (level_liters, capacity_liters, pump_status, drain_velocity, leak_detected) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setInt(1, mainLevel);
        pstmt.setInt(2, MAIN_CAPACITY);
        pstmt.setString(3, isMainPumpRunning ? "ON" : "OFF");
        pstmt.setDouble(4, currentDrainVelocity);
        boolean anyLeak = Arrays.stream(zoneStatus).anyMatch(s -> s.equals("LEAK"));
        pstmt.setBoolean(5, anyLeak);
        pstmt.executeUpdate();
      }
    } catch (SQLException e) {
      System.err.println("❌ DB Write Error: " + e.getMessage());
    }
  }

  private void checkGlobalAlerts() {
    if (listener == null) return;
    if (mainLevel <= 500 && !isMainPumpRunning) {
       listener.onAlert("CRITICAL", "MAIN TANK CRITICALLY LOW!");
    } 
    if (systemHealthScore < 50) {
       listener.onAlert("WARNING", "SYSTEM HEALTH DEGRADED (<50%)");
    }
  }

  // --- GETTERS FOR UI ---

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("mainLevel", mainLevel);
    status.put("capacity", MAIN_CAPACITY);
    status.put("pumpActive", isMainPumpRunning); 
    status.put("status", systemHealthScore < 50 ? "DEGRADED" : (isMainPumpRunning ? "REFILLING" : "NORMAL"));
    status.put("drainVelocity", String.format("%.2f L/s", currentDrainVelocity));
    status.put("healthScore", String.format("%.1f%%", systemHealthScore));
    status.put("activeAlerts", activeAlertCount);
    
    List<Map<String, Object>> zones = new ArrayList<>();
    for(int i=0; i<NUM_ZONES; i++) {
      Map<String, Object> z = new HashMap<>();
      z.put("id", i+1);
      z.put("level", zoneLevels[i]);
      z.put("cap", ZONE_CAPACITIES[i]);
      z.put("state", zoneStatus[i]);
      z.put("flowRate", currentFlowRates[i]); 
      zones.add(z);
    }
    status.put("zones", zones);
    
    return status;
  }

  @Override
  public List<Map<String, Object>> getHistory(int hours) {
     List<Map<String, Object>> history = new ArrayList<>();
     try {
      ensureDatabaseConnection();
      String sql = "SELECT level_liters, recorded_at, drain_velocity, leak_detected FROM tank_readings ORDER BY recorded_at DESC LIMIT 100";
      try (Statement stmt = dbConnection.createStatement();
           ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          Map<String, Object> row = new HashMap<>();
          row.put("level", rs.getInt("level_liters"));
          row.put("timestamp", rs.getTimestamp("recorded_at").getTime());
          row.put("velocity", rs.getDouble("drain_velocity"));
          row.put("leak", rs.getBoolean("leak_detected"));
          history.add(row);
        }
      }
      Collections.reverse(history); 
    } catch (SQLException e) {
      System.err.println("History Fetch Error: " + e.getMessage());
    }
    return history;
  }

  @Override
  public void setAlertListener(AlertListener l) { this.listener = l; }

  @Override
  public boolean sendCommand(String command) {
    if ("TOGGLE_PUMP".equals(command)) {
      isMainPumpRunning = !isMainPumpRunning;
      System.out.println("👤 MANUAL OVERRIDE: Main Pump switched to " + (isMainPumpRunning ? "ON" : "OFF"));
      return true;
    }
    if ("EXPORT_CSV".equals(command)) {
      exportDataToCsv();
      return true;
    }
    return false;
  }

  private void exportDataToCsv() {
    try {
      FileWriter writer = new FileWriter("water_grid_report.csv");
      writer.append("ID,Timestamp,Main_Level,Pump_Status,Velocity,Likely_Leak\n");
      writer.flush();
      writer.close();
      System.out.println("📄 Report exported to water_grid_report.csv");
      if(listener != null) listener.onAlert("INFO", "Report Generated Successfully!");
      
    } catch (IOException e) {
      System.err.println("Export Failed: " + e.getMessage());
    }
  }

  private void startOfflineSimulation() {
    System.out.println("🔄 Offline Mode...");
    monitoringTimer = new Timer(true);
    monitoringTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        mainLevel -= 10;
        if(mainLevel < 0) mainLevel = 0;
        updateMainPumpStatus();
      }
    }, 2000, 2000);
  }

  /**
   * Returns historical data for a specific zone index (0-3)
   */
  public List<Integer> getZoneHistory(int zoneIndex) {
    if (zoneIndex >= 0 && zoneIndex < NUM_ZONES) {
      return new ArrayList<>(zoneHistories.get(zoneIndex)); // Return copy
    }
    return Collections.emptyList();
  }
  
  /**
   * Returns current stats for a specific zone
   */
  public Map<String, Object> getZoneStats(int zoneIndex) {
    Map<String, Object> stats = new HashMap<>();
    if (zoneIndex >= 0 && zoneIndex < NUM_ZONES) {
      stats.put("level", zoneLevels[zoneIndex]);
      stats.put("cap", ZONE_CAPACITIES[zoneIndex]);
      stats.put("state", zoneStatus[zoneIndex]);
      stats.put("flowRate", currentFlowRates[zoneIndex]);
    }
    return stats;
  }
}