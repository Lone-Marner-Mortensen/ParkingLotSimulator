CREATE TABLE IF NOT EXISTS parking_spots (
    spot VARCHAR(10) NOT NULL PRIMARY KEY,
    license_plate VARCHAR(20)
);

INSERT INTO parking_spots (spot, license_plate)
SELECT 'A' || gs.i, NULL
FROM generate_series(1, 25) AS gs(i)
ON CONFLICT (spot) DO NOTHING;

INSERT INTO parking_spots (spot, license_plate)
SELECT 'B' || gs.i, NULL
FROM generate_series(1, 25) AS gs(i)
ON CONFLICT (spot) DO NOTHING;

CREATE TABLE IF NOT EXISTS vehicle_transits (
    license_plate VARCHAR(20) NOT NULL PRIMARY KEY
);
