-- Create admin user manually
-- Password is: admin123 (BCrypt encrypted)
INSERT INTO users (id, email, password, name, role, active) 
VALUES (1, 'admin@pcd.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z2EXw8TfF5PG7IYCpKeYRR.O', 'Admin User', 'ADMIN', true);


