-- Region Management Service Database Initialization

CREATE DATABASE region_management;

\c region_management;

-- Provinces table
CREATE TABLE provinces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Schools table
CREATE TABLE schools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    province_id UUID NOT NULL REFERENCES provinces(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, province_id)
);

-- Region change requests table
CREATE TABLE region_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('student', 'coach')),
    current_province_id UUID REFERENCES provinces(id),
    current_school_id UUID REFERENCES schools(id),
    requested_province_id UUID NOT NULL REFERENCES provinces(id),
    requested_school_id UUID NOT NULL REFERENCES schools(id),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by UUID,
    review_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX idx_schools_province_id ON schools(province_id);
CREATE INDEX idx_region_change_requests_user_id ON region_change_requests(user_id);
CREATE INDEX idx_region_change_requests_status ON region_change_requests(status);
CREATE INDEX idx_region_change_requests_created_at ON region_change_requests(created_at);

-- Update triggers for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_provinces_updated_at BEFORE UPDATE ON provinces FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_schools_updated_at BEFORE UPDATE ON schools FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_region_change_requests_updated_at BEFORE UPDATE ON region_change_requests FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some initial data
INSERT INTO provinces (name) VALUES 
    ('北京市'),
    ('上海市'),
    ('广东省'),
    ('浙江省'),
    ('江苏省'),
    ('山东省'),
    ('四川省'),
    ('湖北省'),
    ('河南省'),
    ('湖南省');

-- Insert some schools for testing
INSERT INTO schools (name, province_id) VALUES 
    ('北京大学附属中学', (SELECT id FROM provinces WHERE name = '北京市')),
    ('清华大学附属中学', (SELECT id FROM provinces WHERE name = '北京市')),
    ('上海中学', (SELECT id FROM provinces WHERE name = '上海市')),
    ('华东师范大学第二附属中学', (SELECT id FROM provinces WHERE name = '上海市')),
    ('华南师范大学附属中学', (SELECT id FROM provinces WHERE name = '广东省')),
    ('深圳中学', (SELECT id FROM provinces WHERE name = '广东省'));
