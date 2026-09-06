package parkinglot.simulator.domain.model

@JvmInline
value class ParkingSpotId(val value: String) {
    init {
        require(value.matches(Regex("^[AB]([1-9]|[1-4][0-9]|50)$"))) {
            "ParkingSpotId must match A1–A50 or B1–B50, but was $value"
        }
    }
}
