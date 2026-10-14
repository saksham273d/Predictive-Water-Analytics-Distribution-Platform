package backend;

import java.util.*;

public class MockBackendConnector implements BackendConnector {
  private AlertListener listener;
  private int mockLevel = 7500;
  private final Random random = new Random();
  private final Timer timer = new Timer(true);

  public MockBackendConnector() {
    // Har 10 sec mein level kam karo aur alert check karo
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        mockLevel = Math.max(0, mockLevel - (50 + random.nextInt(150)));
        
        // Critical alert bhejo agar level bahut kam ho
        if (mockLevel < 1000 && listener != null) {
          listener.onAlert("CRITICAL", "Water level critically low!");
        } else if (mockLevel < 3000 && listener != null && random.nextBoolean()) {
          listener.onAlert("WARNING", "Water level dropping fast");
        }
      }
    }, 10000, 10000);  // 10 sec delay, phir har 10 sec repeat
  }

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("level", mockLevel);
    status.put("capacity", 10000);
    
    if (mockLevel < 1000) status.put("status", "CRITICAL");
    else if (mockLevel < 3000) status.put("status", "WARNING");
    else status.put("status", "NORMAL");
    
    return status;
  }

  // ✅ YE NAYA METHOD ADD HUA HAI - History chart ke liye
  @Override
  public List<Map<String, Object>> getHistory(int hours) {
    List<Map<String, Object>> history = new ArrayList<>();
    long now = System.currentTimeMillis();
    
    for (int i = hours; i >= 0; i--) {
      Map<String, Object> reading = new HashMap<>();
      reading.put("timestamp", now - (i * 3600000L)); // Har ghante ka data
      reading.put("level", Math.max(0, 7500 - (i * 150) + random.nextInt(200)));
      reading.put("consumption", 120 + random.nextInt(80));
      history.add(reading);
    }
    return history;
  }

  @Override
  public void setAlertListener(AlertListener l) {
    this.listener = l;
  }

  @Override
  public boolean sendCommand(String command) {
    System.out.println("[MOCK] Command received: " + command);
    return true;
  }
}