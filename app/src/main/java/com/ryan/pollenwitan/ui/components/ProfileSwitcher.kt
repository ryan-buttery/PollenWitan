package com.ryan.pollenwitan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ryan.pollenwitan.domain.model.UserProfile

@Composable
fun ProfileSwitcher(
    profiles: List<UserProfile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit
) {
    if (profiles.size > 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { profile ->
                FilterChip(
                    selected = profile.id == selectedProfileId,
                    onClick = { onSelectProfile(profile.id) },
                    label = { Text(profile.displayName) }
                )
            }
        }
    }
}
