package com.ryan.pollenwitan.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.domain.model.DayPeriod
import com.ryan.pollenwitan.domain.model.DefaultSymptom
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType

// ── PollenType ──────────────────────────────────────────────

@Composable
fun PollenType.localizedName(): String = when (this) {
    PollenType.Birch -> stringResource(R.string.pollen_birch)
    PollenType.Alder -> stringResource(R.string.pollen_alder)
    PollenType.Grass -> stringResource(R.string.pollen_grass)
    PollenType.Mugwort -> stringResource(R.string.pollen_mugwort)
    PollenType.Ragweed -> stringResource(R.string.pollen_ragweed)
    PollenType.Olive -> stringResource(R.string.pollen_olive)
}

fun PollenType.localizedName(context: Context): String = when (this) {
    PollenType.Birch -> context.getString(R.string.pollen_birch)
    PollenType.Alder -> context.getString(R.string.pollen_alder)
    PollenType.Grass -> context.getString(R.string.pollen_grass)
    PollenType.Mugwort -> context.getString(R.string.pollen_mugwort)
    PollenType.Ragweed -> context.getString(R.string.pollen_ragweed)
    PollenType.Olive -> context.getString(R.string.pollen_olive)
}

@Composable
fun PollenType.localizedAbbreviation(): String = when (this) {
    PollenType.Birch -> stringResource(R.string.pollen_birch_abbr)
    PollenType.Alder -> stringResource(R.string.pollen_alder_abbr)
    PollenType.Grass -> stringResource(R.string.pollen_grass_abbr)
    PollenType.Mugwort -> stringResource(R.string.pollen_mugwort_abbr)
    PollenType.Ragweed -> stringResource(R.string.pollen_ragweed_abbr)
    PollenType.Olive -> stringResource(R.string.pollen_olive_abbr)
}

fun PollenType.localizedAbbreviation(context: Context): String = when (this) {
    PollenType.Birch -> context.getString(R.string.pollen_birch_abbr)
    PollenType.Alder -> context.getString(R.string.pollen_alder_abbr)
    PollenType.Grass -> context.getString(R.string.pollen_grass_abbr)
    PollenType.Mugwort -> context.getString(R.string.pollen_mugwort_abbr)
    PollenType.Ragweed -> context.getString(R.string.pollen_ragweed_abbr)
    PollenType.Olive -> context.getString(R.string.pollen_olive_abbr)
}

// ── DefaultSymptom ──────────────────────────────────────────

@Composable
fun DefaultSymptom.localizedName(): String = when (this) {
    DefaultSymptom.Sneezing -> stringResource(R.string.symptom_sneezing)
    DefaultSymptom.ItchyWateryEyes -> stringResource(R.string.symptom_itchy_watery_eyes)
    DefaultSymptom.NasalCongestion -> stringResource(R.string.symptom_nasal_congestion)
    DefaultSymptom.RunnyNose -> stringResource(R.string.symptom_runny_nose)
    DefaultSymptom.ItchyThroat -> stringResource(R.string.symptom_itchy_throat)
}

fun DefaultSymptom.localizedName(context: Context): String = when (this) {
    DefaultSymptom.Sneezing -> context.getString(R.string.symptom_sneezing)
    DefaultSymptom.ItchyWateryEyes -> context.getString(R.string.symptom_itchy_watery_eyes)
    DefaultSymptom.NasalCongestion -> context.getString(R.string.symptom_nasal_congestion)
    DefaultSymptom.RunnyNose -> context.getString(R.string.symptom_runny_nose)
    DefaultSymptom.ItchyThroat -> context.getString(R.string.symptom_itchy_throat)
}

// ── DayPeriod ───────────────────────────────────────────────

@Composable
fun DayPeriod.localizedName(): String = when (this) {
    DayPeriod.Morning -> stringResource(R.string.period_morning)
    DayPeriod.Afternoon -> stringResource(R.string.period_afternoon)
    DayPeriod.Evening -> stringResource(R.string.period_evening)
}

// ── MedicineType ────────────────────────────────────────────

@Composable
fun MedicineType.localizedName(): String = when (this) {
    MedicineType.Tablet -> stringResource(R.string.medicine_type_tablet)
    MedicineType.Eyedrops -> stringResource(R.string.medicine_type_eyedrops)
    MedicineType.NasalSpray -> stringResource(R.string.medicine_type_nasal_spray)
    MedicineType.Other -> stringResource(R.string.medicine_type_other)
}

@Composable
fun MedicineType.localizedUnitLabel(): String = when (this) {
    MedicineType.Tablet -> stringResource(R.string.medicine_unit_tablets)
    MedicineType.Eyedrops -> stringResource(R.string.medicine_unit_drops)
    MedicineType.NasalSpray -> stringResource(R.string.medicine_unit_sprays)
    MedicineType.Other -> stringResource(R.string.medicine_unit_doses)
}

fun MedicineType.localizedUnitLabel(context: Context): String = when (this) {
    MedicineType.Tablet -> context.getString(R.string.medicine_unit_tablets)
    MedicineType.Eyedrops -> context.getString(R.string.medicine_unit_drops)
    MedicineType.NasalSpray -> context.getString(R.string.medicine_unit_sprays)
    MedicineType.Other -> context.getString(R.string.medicine_unit_doses)
}
