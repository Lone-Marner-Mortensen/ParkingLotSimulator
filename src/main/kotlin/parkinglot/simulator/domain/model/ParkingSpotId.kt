package parkinglot.simulator.domain.model

@JvmInline
value class ParkingSpotId(val value: String) {
    init {
        require(value.matches(Regex("^[AB]([1-9]|1[0-9]|2[0-5])$"))) {
            "ParkingSpotId must be in the form Ai or Bi where i is between 1 and 25, but was $value"
        }
    }
}
