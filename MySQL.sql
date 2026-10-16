-- Create Database
CREATE DATABASE IF NOT EXISTS water_mgmt_db;
USE water_mgmt_db;

-- Create Table for Readings
CREATE TABLE tank_readings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  level_liters INT NOT NULL,
  capacity_liters INT DEFAULT 10000,
  pump_status VARCHAR(10),
  drain_velocity DECIMAL(5,2) DEFAULT 0.00, -- NEW COLUMN FOR PREDICTION
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert Dummy Data for Initial Chart View
INSERT INTO tank_readings (level_liters, pump_status, drain_velocity) VALUES 
(9800, 'OFF', 0.00), (9500, 'OFF', 0.00), (9000, 'OFF', 0.00), 
(8500, 'OFF', 0.00), (8000, 'OFF', 0.00), (7500, 'OFF', 0.00);