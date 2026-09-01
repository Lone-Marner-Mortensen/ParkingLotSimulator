package parkinglot.simulator.domain.model

@JvmInline
value class ParkingSpotId(val value: String) {
    init {
        require(value.matches(Regex("^[AB]([1-9]|1[0-9]|2[0-5])$"))) {
            "ParkingSpotId must match A1–A25 or B1–B25, but was $value"
        }
    }
}
