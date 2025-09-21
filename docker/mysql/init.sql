-- MySQL Initialization Script for E-commerce Backend
-- This script sets up the database with optimized settings

-- Create the database if it doesn't exist
CREATE DATABASE IF NOT EXISTS ecommerce 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- Use the database
USE ecommerce;

-- Create the application user
CREATE USER IF NOT EXISTS 'ecommerce_user'@'%' IDENTIFIED BY 'ecommerce_pass';

-- Grant necessary privileges
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER ON ecommerce.* TO 'ecommerce_user'@'%';

-- Flush privileges
FLUSH PRIVILEGES;

-- Optimize MySQL settings for the application
SET GLOBAL innodb_buffer_pool_size = 268435456; -- 256MB
SET GLOBAL innodb_log_file_size = 67108864; -- 64MB
SET GLOBAL innodb_flush_log_at_trx_commit = 2;
SET GLOBAL query_cache_size = 33554432; -- 32MB
SET GLOBAL query_cache_type = 1;

-- Create sample data (optional - for demo purposes)
-- This will be populated when the application starts

-- Sample categories for demo
INSERT IGNORE INTO products (id, name, description, price, sku, created_at, updated_at, click_count, purchase_count, popularity_score) VALUES
(1, 'Sample Product 1', 'This is a sample product for demonstration', 99.99, 'SAMPLE-001', NOW(), NOW(), 0, 0, 0.0),
(2, 'Sample Product 2', 'Another sample product for testing', 149.99, 'SAMPLE-002', NOW(), NOW(), 0, 0, 0.0);

-- Log initialization completion
SELECT 'MySQL initialization completed successfully' AS message;
