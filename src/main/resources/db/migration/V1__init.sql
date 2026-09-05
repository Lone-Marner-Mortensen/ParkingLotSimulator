CREATE TABLE IF NOT EXISTS parking_spots (
    spot VARCHAR(10) NOT NULL PRIMARY KEY,
    license_plate VARCHAR(20),
    CONSTRAINT parking_spot_not_blank CHECK (btrim(spot) <> ''),
    CONSTRAINT parking_spot_license_plate_not_blank
        CHECK (license_plate IS NULL OR btrim(license_plate) <> '')
);

INSERT INTO parking_spots (spot, license_plate)
SELECT 'A' || gs.i, NULL
FROM generate_series(1, 25) AS gs(i)
ON CONFLICT (spot) DO NOTHING;

INSERT INTO parking_spots (spot, license_plate)
SELECT 'B' || gs.i, NULL
FROM generate_series(1, 25) AS gs(i)
ON CONFLICT (spot) DO NOTHING;

CREATE UNIQUE INDEX parking_spots_unique_license_plate
    ON parking_spots (license_plate)
    WHERE license_plate IS NOT NULL;

CREATE TABLE IF NOT EXISTS vehicle_transits (
    license_plate VARCHAR(20) NOT NULL PRIMARY KEY,
    CONSTRAINT vehicle_transit_license_plate_not_blank CHECK (btrim(license_plate) <> '')
);

CREATE TABLE sensor_events_processing_status (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    sequence_number INT NULL,
    is_processing BOOLEAN NULL,
    processed_at TIMESTAMP NULL,
    CONSTRAINT sensor_events_processing_status_sequence_number_positive
        CHECK (sequence_number IS NULL OR sequence_number >= 1),
    CONSTRAINT sensor_events_processing_status_processed_implies_not_processing
        CHECK (processed_at IS NULL OR is_processing = false)
);
