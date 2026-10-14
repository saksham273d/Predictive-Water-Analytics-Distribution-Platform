package backend;

import java.io.*;
import java.sql.*;
import java.util.*;

public class RealBackendConnector implements BackendConnector {
  
  private Process cProcess; // C program ka handle
  private BufferedReader reader; // C se data padhne ke liye
  private Connection dbConnection; // SQL se connect karne ke liye
  private AlertListener listener;
  private int currentLevel = 7500; // Default initial value
  private final int MAX_CAPACITY = 10000;
  private Timer dbWriterTimer; // Periodically DB mein write karne ke liye

  public RealBackendConnector() {
    try {
      // 1. Connect to SQL Database
      // NOTE: Member A se exact URL/User/Pass lena hai
      String url = "jdbc:mysql://localhost:3306/water_mgmt";
      String user = "root"; 
      String pass = "password"; // Change this
      
      Class.forName("com.mysql.cj.jdbc.Driver");
      dbConnection = DriverManager.getConnection(url, user, pass);
      System.out.println("✅ Connected to MySQL Database");

      // 2. Start C Sensor Agent
      // Path adjust karo based on where Member B put the .exe
      ProcessBuilder pb = new ProcessBuilder("./simulator/water_sensor.exe");
      pb.redirectErrorStream(true);
      cProcess = pb.start();
      reader = new BufferedReader(new InputStreamReader(cProcess.getInputStream()));
      System.out.println("✅ Started C Sensor Agent");

      // 3. Start Background Thread to Read C Output Continuously
      startSensorReaderThread();

      // 4. Start Timer to Batch Insert Data into SQL every 30 seconds
      startDbWriterTimer();

    } catch (Exception e) {
      System.err.println("❌ Initialization Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Thread jo C program se line-by-line data padhega */
  private void startSensorReaderThread() {
    new Thread(() -> {
      String line;
      try {
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("LEVEL|")) {
            parseAndUpdateState(line);
          }
        }
      } catch (IOException e) {
        System.err.println("⚠️ Sensor Stream Ended or Crashed");
      }
    }).start();
  }

  /** C output parse karo: LEVEL|current|max|timestamp */
  private void parseAndUpdateState(String line) {
    try {
      String[] parts = line.split("\\|");
      if (parts.length >= 2) {
        currentLevel = Integer.parseInt(parts[1]);
        
        // Check for critical alerts from C directly if needed
        if (currentLevel < 1000 && listener != null) {
           listener.onAlert("CRITICAL", "Direct Sensor Alert: Level Critical!");
        }
      }
    } catch (NumberFormatException e) {
      System.err.println("Invalid data format from C: " + line);
    }
  }

  /** Har 30 sec mein buffer kiya hua data SQL mein daalo */
  private void startDbWriterTimer() {
    dbWriterTimer = new Timer(true);
    dbWriterTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        insertIntoDatabase();
      }
    }, 30000, 30000); // Initial delay 30s, then repeat every 30s
  }

  private void insertIntoDatabase() {
    try {
      // Simple Insert Query (Optimize later with Prepared Statement batch)
      String sql = "INSERT INTO tank_readings (level_liters, capacity_liters, timestamp) VALUES (?, ?, NOW())";
      PreparedStatement stmt = dbConnection.prepareStatement(sql);
      stmt.setInt(1, currentLevel);
      stmt.setInt(2, MAX_CAPACITY);
      stmt.executeUpdate();
      
      System.out.println("💾 Saved reading to DB: " + currentLevel + "L");
      
    } catch (SQLException e) {
      System.err.println("DB Write Error: " + e.getMessage());
    }
  }

  // --- Interface Methods Implementation ---

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("level", currentLevel);
    status.put("capacity", MAX_CAPACITY);
    
    if (currentLevel < 1000) status.put("status", "CRITICAL");
    else if (currentLevel < 3000) status.put("status", "WARNING");
    else status.put("status", "NORMAL");
    
    return status;
  }

  @Override
  public List<Map<String, Object>> getHistory(int hours) {
    List<Map<String, Object>> history = new ArrayList<>();
    try {
      // Fetch last 'hours' worth of data from SQL
      // Note: Adjust query based on Member A's table structure
      String sql = "SELECT level_liters, timestamp FROM tank_readings ORDER BY timestamp DESC LIMIT ?";
      PreparedStatement stmt = dbConnection.prepareStatement(sql);
      stmt.setInt(1, hours * 6); // Approximate rows (assuming 1 reading per 10 mins in DB)
      
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        Map<String, Object> row = new HashMap<>();
        row.put("level", rs.getInt("level_liters"));
        row.put("timestamp", rs.getTimestamp("timestamp").getTime());
        history.add(row);
      }
      // Reverse list so oldest is first for chart plotting
      Collections.reverse(history);
      
    } catch (SQLException e) {
      System.err.println("History Fetch Error: " + e.getMessage());
    }
    return history;
  }

  @Override
  public void setAlertListener(AlertListener l) {
    this.listener = l;
  }

  @Override
  public boolean sendCommand(String command) {
    try {
      // Send command to C via Stdin
      PrintWriter writer = new PrintWriter(cProcess.getOutputStream(), true);
      writer.println(command);
      System.out.println("📤 Sent Command to C: " + command);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}