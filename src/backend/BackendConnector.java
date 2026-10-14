package backend;

import java.util.List;
import java.util.Map;

/**
 * Frontend ka single point of contact with backend
 * Baad mein real implementation isse replace karegi
 */
public interface BackendConnector {
  // Current tank status lao
  Map<String, Object> getCurrentStatus();
 
  List<Map<String, Object>> getHistory(int hours); 
  
  // Alert sunne ke liye listener set karo
  void setAlertListener(AlertListener listener);
  
  // Command bhejo (future use)
  boolean sendCommand(String command);
}