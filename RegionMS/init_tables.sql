-- Create region management tables in existing galphos database

-- Provinces table
CREATE TABLE IF NOT EXISTS provinces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Schools table
CREATE TABLE IF NOT EXISTS schools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    province_id UUID NOT NULL REFERENCES provinces(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(name, province_id)
);

-- Region change requests table
CREATE TABLE IF NOT EXISTS region_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('student', 'coach')),
    current_province_id UUID REFERENCES provinces(id),
    current_school_id UUID REFERENCES schools(id),
    requested_province_id UUID NOT NULL REFERENCES provinces(id),
    requested_school_id UUID NOT NULL REFERENCES schools(id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reason TEXT,
    processed_by UUID,
    processed_at TIMESTAMP WITH TIME ZONE,
    admin_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_schools_province_id ON schools(province_id);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_user_id ON region_change_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_status ON region_change_requests(status);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_created_at ON region_change_requests(created_at);

-- Insert some sample provinces
INSERT INTO provinces (name) VALUES 
    ('Bangkok'),
    ('Chiang Mai'),
    ('Phuket'),
    ('Khon Kaen'),
    ('Songkhla')
ON CONFLICT (name) DO NOTHING;

-- Insert some sample schools for each province
INSERT INTO schools (name, province_id) VALUES 
    ('Bangkok University', (SELECT id FROM provinces WHERE name = 'Bangkok')),
    ('Chulalongkorn University', (SELECT id FROM provinces WHERE name = 'Bangkok')),
    ('Kasetsart University', (SELECT id FROM provinces WHERE name = 'Bangkok')),
    ('Chiang Mai University', (SELECT id FROM provinces WHERE name = 'Chiang Mai')),
    ('Mae Fah Luang University', (SELECT id FROM provinces WHERE name = 'Chiang Mai')),
    ('Prince of Songkla University (Phuket)', (SELECT id FROM provinces WHERE name = 'Phuket')),
    ('Phuket Rajabhat University', (SELECT id FROM provinces WHERE name = 'Phuket')),
    ('Khon Kaen University', (SELECT id FROM provinces WHERE name = 'Khon Kaen')),
    ('Mahasarakham University', (SELECT id FROM provinces WHERE name = 'Khon Kaen')),
    ('Prince of Songkla University (Hat Yai)', (SELECT id FROM provinces WHERE name = 'Songkhla')),
    ('Songkhla Rajabhat University', (SELECT id FROM provinces WHERE name = 'Songkhla'))
ON CONFLICT (name, province_id) DO NOTHING;

-- Grant necessary permissions to 'db' user
GRANT SELECT, INSERT, UPDATE, DELETE ON provinces TO db;
GRANT SELECT, INSERT, UPDATE, DELETE ON schools TO db;
GRANT SELECT, INSERT, UPDATE, DELETE ON region_change_requests TO db;
