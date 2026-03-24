package com.ryan.pollenwitan.domain.model

data class ProfileLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)

data class AllergenThreshold(
    val type: PollenType,
    val low: Double,
    val moderate: Double,
    val high: Double,
    val veryHigh: Double
)

data class UserProfile(
    val id: String,
    val displayName: String,
    val trackedAllergens: Map<PollenType, AllergenThreshold>,
    val hasAsthma: Boolean,
    val location: ProfileLocation? = null,
    val medicineAssignments: List<MedicineAssignment> = emptyList()
) {
    companion object {
        fun defaultThreshold(type: PollenType): AllergenThreshold = when (type) {
            PollenType.Birch -> AllergenThreshold(type, low = 1.0, moderate = 11.0, high = 51.0, veryHigh = 201.0)
            PollenType.Alder -> AllergenThreshold(type, low = 1.0, moderate = 11.0, high = 51.0, veryHigh = 101.0)
            PollenType.Grass -> AllergenThreshold(type, low = 1.0, moderate = 6.0, high = 31.0, veryHigh = 81.0)
            PollenType.Mugwort -> AllergenThreshold(type, low = 1.0, moderate = 11.0, high = 51.0, veryHigh = 101.0)
            PollenType.Ragweed -> AllergenThreshold(type, low = 1.0, moderate = 6.0, high = 31.0, veryHigh = 81.0)
            PollenType.Olive -> AllergenThreshold(type, low = 1.0, moderate = 11.0, high = 51.0, veryHigh = 201.0)
        }

        val Ryan = UserProfile(
            id = "ryan",
            displayName = "Ryan",
            trackedAllergens = mapOf(
                PollenType.Birch to defaultThreshold(PollenType.Birch)
            ),
            hasAsthma = true
        )

        val Olga = UserProfile(
            id = "olga",
            displayName = "Olga",
            trackedAllergens = mapOf(
                PollenType.Alder to defaultThreshold(PollenType.Alder)
            ),
            hasAsthma = true
        )
    }
}
