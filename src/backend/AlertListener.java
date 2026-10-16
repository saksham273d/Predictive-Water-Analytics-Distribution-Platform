package backend;

public interface AlertListener {
  void onAlert(String severity, String message);
}