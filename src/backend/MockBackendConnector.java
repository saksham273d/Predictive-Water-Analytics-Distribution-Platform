package backend;

import java.util.*;

public class MockBackendConnector implements BackendConnector {
  private AlertListener listener;
  private int mockLevel = 7500;
  private final Random random = new Random();
  private final Timer timer = new Timer(true);

  public MockBackendConnector() {
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        mockLevel = Math.max(0, mockLevel - (50 + random.nextInt(150)));
        
        if (mockLevel < 1000 && listener != null) {
          listener.onAlert("CRITICAL", "Water level critically low!");
        } else if (mockLevel < 3000 && listener != null && random.nextBoolean()) {
          listener.onAlert("WARNING", "Water level dropping fast");
        }
      }
    }, 10000, 10000);
  }

  @Override
  public Map<String, Object> getCurrentStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("level", mockLevel);
    status.put("capacity", 10000);
    status.put("pumpActive", false); 
    
    if (mockLevel < 1000) status.put("status", "CRITICAL");
    else if (mockLevel < 3000) status.put("status", "WARNING");
    else status.put("status", "NORMAL");
    
    return status;
  }

  @Override
  public List<Map<String, Object>> getHistory(int hours) {
    List<Map<String, Object>> history = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (int i = hours; i >= 0; i--) {
      Map<String, Object> reading = new HashMap<>();
      reading.put("timestamp", now - (i * 3600000L));
      reading.put("level", Math.max(0, 7500 - (i * 150) + random.nextInt(200)));
      history.add(reading);
    }
    return history;
  }

  @Override
  public void setAlertListener(AlertListener l) { this.listener = l; }

  @Override
  public boolean sendCommand(String command) { return true; }
}