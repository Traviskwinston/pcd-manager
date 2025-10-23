-- Create return_addresses table
CREATE TABLE IF NOT EXISTS return_addresses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    address TEXT NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Insert default Vultee Street address
INSERT INTO return_addresses (name, address, is_default, created_at, updated_at)
VALUES ('Vultee Street', 'Vultee Street', TRUE, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;




