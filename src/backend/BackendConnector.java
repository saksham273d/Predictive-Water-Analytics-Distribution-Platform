package backend;

import java.util.List;
import java.util.Map;

public interface BackendConnector {
  Map<String, Object> getCurrentStatus();
  List<Map<String, Object>> getHistory(int hours);
  void setAlertListener(AlertListener listener);
  boolean sendCommand(String command);
}