package backend;

/**
 * Jab backend se alert aaye toh ye method call hoga
 */
public interface AlertListener {
  void onAlert(String severity, String message);
}