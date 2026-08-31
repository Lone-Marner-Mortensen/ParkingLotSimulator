package parkinglot.simulator.domain.model

@JvmInline
value class LicensePlate(val value: String) {
    init {
        require(value.length == 10) {
            "LicensePlate must be exactly 10 characters long, but was ${value.length}"
        }
    }
}
