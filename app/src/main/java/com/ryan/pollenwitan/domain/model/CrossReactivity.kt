package com.ryan.pollenwitan.domain.model

import androidx.annotation.StringRes
import com.ryan.pollenwitan.R

data class RelatedPollen(
    val pollenType: PollenType,
    @StringRes val familyRes: Int,
    val strength: CrossReactivityStrength
)

enum class CrossReactivityStrength { Strong, Moderate }

data class PollenCrossReactivity(
    val pollenType: PollenType,
    val relatedPollen: List<RelatedPollen>,
    @StringRes val foodsRes: Int
)

object CrossReactivityData {

    val entries: List<PollenCrossReactivity> = listOf(
        PollenCrossReactivity(
            pollenType = PollenType.Birch,
            relatedPollen = listOf(
                RelatedPollen(PollenType.Alder, R.string.cross_family_betulaceae, CrossReactivityStrength.Strong)
            ),
            foodsRes = R.string.cross_foods_birch
        ),
        PollenCrossReactivity(
            pollenType = PollenType.Alder,
            relatedPollen = listOf(
                RelatedPollen(PollenType.Birch, R.string.cross_family_betulaceae, CrossReactivityStrength.Strong)
            ),
            foodsRes = R.string.cross_foods_alder
        ),
        PollenCrossReactivity(
            pollenType = PollenType.Grass,
            relatedPollen = emptyList(),
            foodsRes = R.string.cross_foods_grass
        ),
        PollenCrossReactivity(
            pollenType = PollenType.Mugwort,
            relatedPollen = listOf(
                RelatedPollen(PollenType.Ragweed, R.string.cross_family_asteraceae, CrossReactivityStrength.Moderate)
            ),
            foodsRes = R.string.cross_foods_mugwort
        ),
        PollenCrossReactivity(
            pollenType = PollenType.Ragweed,
            relatedPollen = listOf(
                RelatedPollen(PollenType.Mugwort, R.string.cross_family_asteraceae, CrossReactivityStrength.Moderate)
            ),
            foodsRes = R.string.cross_foods_ragweed
        ),
        PollenCrossReactivity(
            pollenType = PollenType.Olive,
            relatedPollen = emptyList(),
            foodsRes = R.string.cross_foods_olive
        )
    )

    /**
     * Given a set of selected allergens, returns suggestions for cross-reactive
     * partners that are NOT yet selected.
     * Returns Map<suggested allergen -> the selected allergen that triggered the suggestion>.
     */
    fun suggestionsFor(selected: Set<PollenType>): Map<PollenType, RelatedPollen> {
        val suggestions = mutableMapOf<PollenType, RelatedPollen>()
        for (entry in entries) {
            if (entry.pollenType !in selected) continue
            for (related in entry.relatedPollen) {
                if (related.pollenType !in selected && related.pollenType !in suggestions) {
                    suggestions[related.pollenType] = RelatedPollen(
                        pollenType = entry.pollenType,
                        familyRes = related.familyRes,
                        strength = related.strength
                    )
                }
            }
        }
        return suggestions
    }
}
