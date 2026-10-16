package backend;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced Smart Water Monitoring System - Backend Connector
 * 
 * Features:
 * 1. Hardware Abstraction Layer (HAL).
 * 2. Velocity-Based Predictive Pump Control.
 * 3. Anomaly Detection & Signal Filtering.
 * 4. Virtual Pressure Calculation (Simulated from Level).
 */
public class RealBackendConnector implements BackendConnector {

  // --- CONFIGURATION CONSTANTS ---
  private static final int TOTAL_FAMILIES = 50;
  private static final int LIMIT_PER_FAMILY = 100; 
  private static final int MAX_CAPACITY = 10000;   
  
  // Pump Control Thresholds
  private static final int BASE_PUMP_ON_LEVEL = 3000;      
  private static final int PUMP_OFF_LEVEL = 9500;          
  private static final double VELOCITY_TRIGGER_THRESHOLD = 8.0; 
  private static final int PREDICTIVE_START_LEVEL = 4500;  

  // NEW: Pressure Limits (Bar units for simulation)
  private static final double MIN_SAFE_PRESSURE = 0.5; 
  private static final double MAX_SAFE_PRESSURE = 6.0; 

  // Simulation Parameters
  private static final int SIM_BASE_CONSUMPTION = 15;
  private static final int SIM_RANDOM_SPIKE_MAX = 20;
  private static final int SIM_REFILL_RATE = 250;
  
  // Database Credentials
  private static final String DB_URL = "jdbc:mysql://localhost:3306/water_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true";
  private static final String DB_USER = "root";
  private static final String DB_PASS = "VSP@23122709"; 

  // --- STATE VARIABLES (Thread-Safe) ---
  private volatile int currentLevel = 7500;
  private volatile int previousLevel = 7500;
  private volatile long lastUpdateTime = System.currentTimeMillis();
  
  private volatile int currentDayUsage = 0;
  private volatile boolean isPumpRunning = false;
  
  // Metrics for UI/Analytics
  private volatile double currentDrainVelocity = 0.0; 
  private volatile double currentPressure = 0.0; // NEW VARIABLE
  private volatile String systemStatus = "NORMAL";

  // Connections & Timers
  private Connection dbConnection;
  private Timer monitoringTimer;
  private AlertListener listener;

  private static final boolean USE_HARDWARE_MODE = false;

  public RealBackendConnector() {
    try {
      initializeSystem();
    } catch (Exception e) {
      System.err.println("❌ Critical Initialization Error: " + e.getMessage());
      startOfflineSimulation();
    }
  }

  private void initializeSystem() throws Exception {
    if (!USE_HARDWARE_MODE) {
      System.out.println("🧪 Running in Simulation Mode...");
      ensureDatabaseConnection(); 
      loadInitialStateFromDB();
    } else {
      initHardwareInterface(); 
    }
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
          this.currentLevel = rs.getInt("level_liters");
          this.previousLevel = this.currentLevel;
          updatePressureMetric(); // Calculate initial pressure
          System.out.println("📂 Loaded Last Level from DB: " + currentLevel + "L | Press: " + String.format("%.2f", currentPressure) + " Bar");
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

    // 1. DATA INGESTION
    int rawConsumption = getRawSensorData(deltaTimeSec);
    
    // 2. ANOMALY DETECTION
    int maxPhysicalDrainPerSec = 50; 
    int maxAllowedDrop = (int)(maxPhysicalDrainPerSec * deltaTimeSec);
    int filteredNewLevel = currentLevel - rawConsumption;
    
    if ((currentLevel - filteredNewLevel) > maxAllowedDrop) {
      filteredNewLevel = currentLevel - maxAllowedDrop;
    }

    // 3. UPDATE STATE
    currentDrainVelocity = (previousLevel - filteredNewLevel) / deltaTimeSec;
    previousLevel = currentLevel;
    currentLevel = Math.max(0, filteredNewLevel);
    currentDayUsage += rawConsumption; 
    lastUpdateTime = now;

    // 4. CALCULATE PRESSURE (NEW LOGIC)
    updatePressureMetric();

    // 5. PUMP CONTROL
    updatePumpStatusPredictive(currentDrainVelocity);

    // 6. PERSISTENCE & ALERTS
    saveReadingToDB(currentDrainVelocity, currentPressure);
    checkHealthAlerts();
    updateSystemStatusString();
  }

  /**
   * Simulates Pressure based on Tank Height (Hydrostatic Principle)
   * Formula: P ≈ ρgh (Simplified for Demo: Higher Level = Higher Pressure at Bottom)
   */
  private void updatePressureMetric() {
    // Map 0-10000 Liters to 0.0 - 6.0 Bar roughly
    this.currentPressure = (currentLevel / (double) MAX_CAPACITY) * MAX_SAFE_PRESSURE;
  }

  private int getRawSensorData(double dt) {
    if (USE_HARDWARE_MODE) return 0; 
    
    int consumption = SIM_BASE_CONSUMPTION + ThreadLocalRandom.current().nextInt(0, SIM_RANDOM_SPIKE_MAX);
    if (ThreadLocalRandom.current().nextDouble() < 0.05) {
      consumption += 500; 
    }
    return consumption;
  }

  private void updatePumpStatusPredictive(double velocity) {
    if (!isPumpRunning) {
      if (currentLevel <= BASE_PUMP_ON_LEVEL) {
        activatePump("Low Level Trigger");
      }
      else if (velocity > VELOCITY_TRIGGER_THRESHOLD && currentLevel <= PREDICTIVE_START_LEVEL) {
        activatePump("High Velocity Warning");
      }
    } else {
      if (currentLevel >= PUMP_OFF_LEVEL) {
        deactivatePump("Tank Full");
      }
    }

    if (isPumpRunning) {
      currentLevel += SIM_REFILL_RATE; 
      if (currentLevel > MAX_CAPACITY) {
        currentLevel = MAX_CAPACITY;
        isPumpRunning = false; 
      }
    }
  }

  private void activatePump(String reason) {
    isPumpRunning = true;
    System.out.println("🟢 PUMP AUTO-STARTED [" + reason + "] at Level: " + currentLevel + "L | Press: " + String.format("%.2f", currentPressure) + " Bar");
    if (listener != null) listener.onAlert("INFO", "Pump Activated: " + reason);
  }

  private void deactivatePump(String reason) {
    isPumpRunning = false;
    System.out.println("🔴 PUMP AUTO-STOPPED [" + reason + "] at Level: " + currentLevel + "L");
    if (listener != null) listener.onAlert("INFO", "Pump Deactivated: " + reason);
  }

  private void saveReadingToDB(double velocity, double pressure) {
    try {
      ensureDatabaseConnection(); 
      // Note: You need to add 'pressure_bar' column to your SQL table too!
      String sql = "INSERT INTO tank_readings (level_liters, capacity_liters, pump_status, drain_velocity, pressure_bar) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setInt(1, currentLevel);
        pstmt.setInt(2, MAX_CAPACITY);
        pstmt.setString(3, isPumpRunning ? "ON" : "OFF");
        pstmt.setDouble(4, velocity);
        pstmt.setDouble(5, pressure);
        pstmt.executeUpdate();
      }
    } catch (SQLException e) {
      System.err.println("❌ DB Write Error: " + e.getMessage());
    }
  }

  private void checkHealthAlerts() {
    if (listener == null) return;
    int requiredForToday = TOTAL_FAMILIES * LIMIT_PER_FAMILY;
    
    // CRITICAL: Low Pressure Check (Pipe Leak Simulation)
    if (currentPressure < MIN_SAFE_PRESSURE && !isPumpRunning && currentLevel > 1000) {
       listener.onAlert("CRITICAL", "LOW PRESSURE DETECTED! Possible Pipe Leak.");
    }
    
    // WARNING: High Pressure Check (Blockage Simulation)
    if (currentPressure > MAX_SAFE_PRESSURE - 0.5 && isPumpRunning) {
       listener.onAlert("WARNING", "HIGH PRESSURE WARNING! Check Valves.");
    }

    if (currentLevel <= 500 && !isPumpRunning) {
       listener.onAlert("CRITICAL", "TANK CRITICALLY LOW!");
    } 
    else if (currentDayUsage > requiredForToday && currentDayUsage % 1000 < 100) {
       listener.onAlert("WARNING", "Daily Quota Exceeded!");
    }
  }

  private void updateSystemStatusString() {
    if (currentLevel < 1000) systemStatus = "CRITICAL";
    else if (isPumpRunning) systemStatus = "REFILLING";
    else if (currentDrainVelocity > 10) systemStatus = "RAPID DRAIN";
    else if (currentPressure < MIN_SAFE_PRESSURE) systemStatus = "LOW PRESSURE"; // NEW STATUS
    else if (currentDayUsage > TOTAL_FAMILIES * LIMIT_PER_FAMILY) systemStatus = "OVER_USAGE";
    else systemStatus = "NORMAL";
  }

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("level", currentLevel);
    status.put("capacity", MAX_CAPACITY);
    status.put("pumpActive", isPumpRunning); 
    status.put("totalFamilies", TOTAL_FAMILIES);
    status.put("limitPerFamily", LIMIT_PER_FAMILY);
    status.put("currentDayUsage", currentDayUsage);
    status.put("requiredForToday", TOTAL_FAMILIES * LIMIT_PER_FAMILY);
    status.put("drainVelocity", String.format("%.2f L/s", currentDrainVelocity));
    status.put("pressure", String.format("%.2f Bar", currentPressure)); // NEW METRIC
    status.put("predictionRisk", currentDrainVelocity > 8 ? "HIGH" : "LOW");
    status.put("status", systemStatus);
    return status;
  }

  @Override
  public List<Map<String, Object>> getHistory(int hours) {
    List<Map<String, Object>> history = new ArrayList<>();
    try {
      ensureDatabaseConnection();
      String sql = "SELECT level_liters, recorded_at, drain_velocity, pressure_bar FROM tank_readings ORDER BY recorded_at DESC LIMIT 100";
      try (Statement stmt = dbConnection.createStatement();
           ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          Map<String, Object> row = new HashMap<>();
          row.put("level", rs.getInt("level_liters"));
          row.put("timestamp", rs.getTimestamp("recorded_at").getTime());
          row.put("velocity", rs.getDouble("drain_velocity"));
          row.put("pressure", rs.getDouble("pressure_bar"));
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
      isPumpRunning = !isPumpRunning;
      System.out.println("👤 MANUAL OVERRIDE: Pump switched to " + (isPumpRunning ? "ON" : "OFF"));
      return true;
    }
    return false;
  }

  private void startOfflineSimulation() {
    System.out.println("🔄 Switching to Offline Simulation Mode (No DB)...");
    monitoringTimer = new Timer(true);
    monitoringTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        int consumption = SIM_BASE_CONSUMPTION + ThreadLocalRandom.current().nextInt(0, SIM_RANDOM_SPIKE_MAX);
        currentLevel -= consumption;
        currentDayUsage += consumption;
        if (currentLevel < 0) currentLevel = 0;
        updatePressureMetric();
        updatePumpStatusPredictive(consumption / 2.0); 
        checkHealthAlerts();
        updateSystemStatusString();
      }
    }, 2000, 2000);
  }
  
  private void initHardwareInterface() {
    throw new UnsupportedOperationException("Hardware mode not yet enabled");
  }
}