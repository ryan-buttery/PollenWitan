package com.ryan.pollenwitan.domain.model

enum class DefaultSymptom {
    Sneezing,
    ItchyWateryEyes,
    NasalCongestion,
    RunnyNose,
    ItchyThroat
}

data class TrackedSymptom(
    val id: String,
    val displayName: String,
    val isDefault: Boolean
)
