# 💧 Smart Water Monitoring & Automation System

## Project Overview
This is a real-time IoT-based water management system for residential societies. It monitors tank levels, calculates per-family quotas, and automatically controls pumps to prevent dry-run or overflow conditions.

## Tech Stack
- **Frontend:** Java Swing (Custom Dark Theme UI)
- **Backend Logic:** Java (Multi-threaded Simulation Engine with Velocity Prediction)
- **Hardware Interface (Simulated):** C Language Sensor Agent
- **Database:** MySQL (Persistent Logging with Audit Trail)

## Key Features
1. **Real-Time Dashboard:** Live visualization of tank levels using circular gauges.
2. **Smart Quota Management:** Tracks daily usage against family limits (50 families × 100L/day).
3. **Automated Pump Control:** 
   - Auto-ON when level < 3000L OR High Drain Velocity detected.
   - Auto-OFF when level > 9500L.
4. **Anomaly Detection:** Filters sensor noise/glitches using physical constraint clamping.
5. **Historical Trends:** 24-hour usage graph generated dynamically from SQL.

## How to Run

### Prerequisites
- JDK 11 or higher installed.
- MySQL Server 8.0+ running locally.
- VS Code with "Extension Pack for Java".

### Steps
1. Open `MySQL.sql` in MySQL Workbench and execute it to create the database.
2. Download `mysql-connector-j-x.x.x.jar` and place it in the `lib/` folder.
3. Update `DB_PASS` in `src/backend/RealBackendConnector.java` with your MySQL root password.
4. Compile and Run:
   ```powershell
   javac -d bin -cp "lib/*" src/*.java src/backend/*.java
   java -cp "bin;lib/*" Main