package backend;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smart Water Grid Manager - Core Backend Logic
 */
public class RealBackendConnector implements BackendConnector {

  private static final int MAIN_CAPACITY = 10000;
  private static final int NUM_ZONES = 4;
  private static final int[] ZONE_CAPACITIES = {2000, 2000, 2000, 2000};

  // Pump Thresholds (Hysteresis Band)
  private static final int PUMP_ON_LEVEL = 3000;
  private static final int PUMP_OFF_LEVEL = 9500;

  // Flow Dynamics
  private static final double MAX_PIPE_FLOW_RATE = 150.0;
  private static final int SIM_REFILL_RATE = 250;
  private static final int ZONE_TRIGGER_LOW = 800;
  private static final int ZONE_TARGET_HIGH = 1800;

  // DB Config
  private static final String DB_URL = "jdbc:mysql://localhost:3306/water_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true";
  private static final String DB_USER = "root";
  private static final String DB_PASS = "VSP@23122709";

  // Runtime State
  private volatile int mainLevel = 7500;
  private volatile int prevMainLevel = 7500;
  private volatile long lastTickTime = System.currentTimeMillis();
  private volatile boolean isPumpRunning = false;

  // Zone Arrays
  private final int[] zoneLevels = new int[NUM_ZONES];
  private final int[] prevZoneLevels = new int[NUM_ZONES];
  private final double[] flowRates = new double[NUM_ZONES];
  private final String[] zoneStates = new String[NUM_ZONES];
  private final boolean[] isRefilling = new boolean[NUM_ZONES];

  // Metrics
  private volatile double drainVelocity = 0.0;
  private volatile int alertCount = 0;
  private volatile double healthScore = 100.0;

  // Alert Flags
  private volatile boolean hasUnacknowledgedAlert = false;
  private volatile boolean isCriticalHealthAlertShown = false;

  // History Buffers
  private final List<List<Integer>> zoneHistory = new ArrayList<>();
  private final List<Double> inletHistory = new ArrayList<>();
  private final List<Double> outletHistory = new ArrayList<>();
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

    for(int i=0; i<NUM_ZONES; i++) {
      zoneHistory.add(new LinkedList<>());
      isRefilling[i] = false;

      // Randomize starting levels for visual diversity
      zoneLevels[i] = 400 + ThreadLocalRandom.current().nextInt(0, 1200);
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

  private synchronized void executeCycle() {
    long now = System.currentTimeMillis();
    double dtSec = (now - lastTickTime) / 1000.0;
    if (dtSec <= 0) return;

    int prevMainSnapshot = mainLevel;
    int[] prevZoneSnapshot = zoneLevels.clone();

    simulateConsumption();
    manageMainPump();
    distributeToZones(dtSec);
    analyzeHealth();
    updateMetrics(now);

    // Calculate Flow Discrepancy for Graph
    double outflow = prevMainSnapshot - mainLevel;
    double inflow = 0;
    for (int i = 0; i < NUM_ZONES; i++) {
      inflow += zoneLevels[i] - prevZoneSnapshot[i];
    }

    // Add slight noise to simulate sensor imperfection
    double noise = ThreadLocalRandom.current().nextDouble(-2, 2);

    synchronized (inletHistory) {
      inletHistory.add(outflow + noise);
      if (inletHistory.size() > HISTORY_LIMIT) inletHistory.remove(0);
    }
    synchronized (outletHistory) {
      outletHistory.add(inflow);
      if (outletHistory.size() > HISTORY_LIMIT) outletHistory.remove(0);
    }

    persistData();
    checkGlobalAlerts();
  }

  private void simulateConsumption() {
    for (int i = 0; i < NUM_ZONES; i++) {
      if (zoneLevels[i] > 0) {

        // Varied consumption rates per floor for realistic graph separation
        int usage = switch (i) {
          case 0 -> 15 + ThreadLocalRandom.current().nextInt(0, 10); // Fast
          case 1 -> 3 + ThreadLocalRandom.current().nextInt(0, 4); // Slow
          case 2 -> 8 + ThreadLocalRandom.current().nextInt(0, 6); // Medium
          default -> 1 + ThreadLocalRandom.current().nextInt(0, 3); // Very Slow
        };

        zoneLevels[i] -= usage;
        if (zoneLevels[i] < 0) zoneLevels[i] = 0;
      }
    }
  }

  private void manageMainPump() {
    if (!isPumpRunning && mainLevel <= PUMP_ON_LEVEL) {
      isPumpRunning = true;
      notifyListener("INFO", "Main Pump Activated.");
    } else if (isPumpRunning && mainLevel >= PUMP_OFF_LEVEL) {
      isPumpRunning = false;
      notifyListener("INFO", "Tank Full.");
    }

    if (isPumpRunning) {
      mainLevel += SIM_REFILL_RATE;
      if (mainLevel >= MAIN_CAPACITY) {
        mainLevel = MAIN_CAPACITY;
        isPumpRunning = false;
      }
    }
  }

  private void distributeToZones(double dt) {
    for (int i = 0; i < NUM_ZONES; i++) {

      // Safety Interlock: Block flow if leak detected
      if ("LEAK".equals(zoneStates[i])) {
        flowRates[i] = 0.0;
        isRefilling[i] = false;
        continue;
      }

      int currentLvl = zoneLevels[i];
      if (currentLvl < ZONE_TRIGGER_LOW) isRefilling[i] = true;
      if (currentLvl >= ZONE_TARGET_HIGH) isRefilling[i] = false;

      if (isRefilling[i] && mainLevel > 0) {
        int deficit = ZONE_TARGET_HIGH - currentLvl;
        if (deficit <= 0) { isRefilling[i] = false; continue; }

        double maxFlow = MAX_PIPE_FLOW_RATE * dt;
        int transferAmt = Math.min((int)Math.min(maxFlow, deficit), mainLevel);

        if (transferAmt > 0) {
          mainLevel -= transferAmt;
          zoneLevels[i] += transferAmt;

          if (zoneLevels[i] >= ZONE_TARGET_HIGH) {
            zoneLevels[i] = ZONE_TARGET_HIGH;
            isRefilling[i] = false;
          }

          flowRates[i] = transferAmt / dt;
        } else {
          flowRates[i] = 0.0;
        }
      } else {
        flowRates[i] = 0.0;
      }
    }
    if (mainLevel < 0) mainLevel = 0;
  }

  private void analyzeHealth() {
    int issues = 0;
    double score = 100.0;
    boolean newIssue = false;

    for (int i = 0; i < NUM_ZONES; i++) {
      if ("LEAK".equals(zoneStates[i])) {
        issues++;
        score -= 20;
        newIssue = true;
        continue;
      }

      int drop = prevZoneLevels[i] - zoneLevels[i];

      if (drop > 50) {
        zoneStates[i] = "LEAK";
        issues++;
        score -= 20;
        newIssue = true;
        notifyListener("CRITICAL", "Leak Detected! Floor " + (i+1) + " BLOCKED.");
      }
      else if (zoneLevels[i] < (ZONE_CAPACITIES[i] * 0.2)) {
        zoneStates[i] = "LOW LEVEL";
        issues++;
        score -= 5;
        newIssue = true;
      }
      else {
        zoneStates[i] = "NORMAL";
      }

      prevZoneLevels[i] = zoneLevels[i];
    }

    if (mainLevel < (MAIN_CAPACITY * 0.1)) {
       issues++;
       score -= 15;
       newIssue = true;
    }

    if (newIssue) {
      this.hasUnacknowledgedAlert = true;
      this.alertCount = issues;
    } else {
      if (!this.hasUnacknowledgedAlert) {
         this.alertCount = 0;
      }
    }

    this.healthScore = Math.max(0, Math.min(100, score));
  }

  private void updateMetrics(long now) {
    drainVelocity = (prevMainLevel - mainLevel) / ((now - lastTickTime)/1000.0);
    prevMainLevel = mainLevel;
    lastTickTime = now;

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

  private void checkGlobalAlerts() {
    if (listener == null) return;

    if (mainLevel <= 500 && !isPumpRunning) {
      listener.onAlert("CRITICAL", "MAIN TANK CRITICALLY LOW!");
    }

    if (healthScore < 45.0) {
      if (!isCriticalHealthAlertShown) {
        listener.onAlert("WARNING", "SYSTEM HEALTH DEGRADED (<45%). Immediate Attention Required.");
        isCriticalHealthAlertShown = true;
      }
    } else {
      if (isCriticalHealthAlertShown) {
        isCriticalHealthAlertShown = false;
      }
    }
  }

  private void notifyListener(String sev, String msg) {
    if (listener != null) listener.onAlert(sev, msg);
  }

  @Override
  public synchronized Map<String, Object> getCurrentStatus() {
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
    } catch (SQLException e) {
      System.err.println("❌ History Load Error: " + e.getMessage());
    }
    return list;
  }

  @Override
  public void setAlertListener(AlertListener l) { this.listener = l; }

  @Override
  public synchronized boolean sendCommand(String cmd) {
    switch (cmd) {
      case "TOGGLE_PUMP" -> {
        isPumpRunning = !isPumpRunning;
        return true;
      }
      case "FORCE_LEAK" -> {
        forceInjectLeak();
        return true;
      }
      default -> {
        if (cmd.startsWith("RESOLVE_ZONE_")) {
          try {
            int zoneIdx = Integer.parseInt(cmd.substring(13));
            if (zoneIdx >= 0 && zoneIdx < NUM_ZONES) {
              resolveSpecificZone(zoneIdx);
              return true;
            }
          } catch (NumberFormatException e) {
            System.err.println("Invalid Zone Index format: " + cmd);
          }
        }

        if ("EXPORT_CSV".equals(cmd)) {
          exportReport();
          return true;
        }

        return false;
      }
    }
  }

  private void resolveSpecificZone(int idx) {
    if (idx >= 0 && idx < NUM_ZONES) {
      if ("LEAK".equals(zoneStates[idx])) {
        zoneStates[idx] = "NORMAL";
        prevZoneLevels[idx] = zoneLevels[idx]; // Sync to prevent false leak detection next tick

        recalculateAlertsOnly();
      }
    }
  }

  private void recalculateAlertsOnly() {
    int currentIssues = 0;
    for (String state : zoneStates) {
      if ("LEAK".equals(state) || "LOW LEVEL".equals(state)) {
        currentIssues++;
      }
    }

    if (currentIssues == 0) {
      this.hasUnacknowledgedAlert = false;
      this.alertCount = 0;
    } else {
      this.alertCount = currentIssues;
    }
  }

  private void forceInjectLeak() {
    int targetZone = -1;
    for(int i=0; i<NUM_ZONES; i++) {
      if(zoneLevels[i] > 500) {
        targetZone = i;
        break;
      }
    }

    if(targetZone != -1) {
      int leakAmount = 250 + ThreadLocalRandom.current().nextInt(0, 100);

      zoneLevels[targetZone] -= leakAmount;
      if(zoneLevels[targetZone] < 0) zoneLevels[targetZone] = 0;

      zoneStates[targetZone] = "LEAK";
      this.hasUnacknowledgedAlert = true;
      this.alertCount++;

      notifyListener("CRITICAL", "Forced Leak Simulated in Floor " + (targetZone+1));
    }
  }

  private void exportReport() {
    try (FileWriter fw = new FileWriter("grid_report.csv")) {
      fw.write("ID,Timestamp,Main_Level,Pump_Status,Velocity,Leak\n");
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

  public synchronized List<Integer> getZoneHistory(int idx) {
    if (idx >= 0 && idx < NUM_ZONES) return new ArrayList<>(zoneHistory.get(idx));
    return Collections.emptyList();
  }

  public List<Double> getInletFlowHistory() {
    synchronized (inletHistory) {
      return new ArrayList<>(inletHistory);
    }
  }

  public List<Double> getOutletFlowHistory() {
    synchronized (outletHistory) {
      return new ArrayList<>(outletHistory);
    }
  }

  public synchronized Map<String, Object> getZoneStats(int idx) {
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