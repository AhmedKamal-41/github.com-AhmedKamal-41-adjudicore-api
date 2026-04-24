-- Synthetic members: 4 active across varied plans, 1 with expired eligibility (M005).
INSERT INTO members (member_id, first_name, last_name, date_of_birth, plan_code, eligibility_start, eligibility_end) VALUES
    ('M001', 'John',   'Smith',   DATE '1980-05-15', 'PPO_GOLD',   DATE '2024-01-01', NULL),
    ('M002', 'Jane',   'Doe',     DATE '1992-08-22', 'HMO_SILVER', DATE '2024-03-15', NULL),
    ('M003', 'Robert', 'Johnson', DATE '1965-11-30', 'MEDICARE_A', DATE '2023-06-01', NULL),
    ('M004', 'Maria',  'Garcia',  DATE '1988-02-14', 'EPO_BRONZE', DATE '2024-09-01', DATE '2026-12-31'),
    ('M005', 'David',  'Lee',     DATE '1975-07-04', 'PPO_GOLD',   DATE '2022-01-01', DATE '2023-12-31');

-- Synthetic providers: 3 in-network (general/family/cardiology), 2 out-of-network (dermatology/radiology).
INSERT INTO providers (npi, name, specialty, is_in_network) VALUES
    ('1234567890', 'General Hospital',           'INTERNAL_MEDICINE', TRUE),
    ('2345678901', 'Sunrise Family Clinic',      'FAMILY_MEDICINE',   TRUE),
    ('3456789012', 'Westside Cardiology',        'CARDIOLOGY',        TRUE),
    ('4567890123', 'Mountain View Dermatology',  'DERMATOLOGY',       FALSE),
    ('5678901234', 'City Imaging Center',        'RADIOLOGY',         FALSE);
