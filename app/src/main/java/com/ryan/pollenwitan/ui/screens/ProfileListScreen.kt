package com.ryan.pollenwitan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.navigation.Screen
import com.ryan.pollenwitan.ui.theme.ForestTheme

@Composable
fun ProfileListScreen(
    navController: NavController,
    viewModel: ProfileManagementViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }

    if (profiles.isEmpty()) {
        EmptyProfilesContent(
            onCreateProfile = { navController.navigate(Screen.ProfileCreate.route) }
        )
    } else {
        ProfileListContent(
            profiles = profiles,
            onEditProfile = { profile ->
                navController.navigate(Screen.ProfileEdit.createRoute(profile.id))
            },
            onDeleteProfile = { profileToDelete = it },
            onCreateProfile = { navController.navigate(Screen.ProfileCreate.route) }
        )
    }

    // Delete confirmation dialog
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("Delete \"${profile.displayName}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    profileToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyProfilesContent(onCreateProfile: () -> Unit) {
    val colors = ForestTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No profiles yet",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.Text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your first profile to get personalised pollen forecasts.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.TextDim
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreateProfile) {
            Text("Create Profile")
        }
    }
}

@Composable
private fun ProfileListContent(
    profiles: List<UserProfile>,
    onEditProfile: (UserProfile) -> Unit,
    onDeleteProfile: (UserProfile) -> Unit,
    onCreateProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        profiles.forEach { profile ->
            ProfileCard(
                profile = profile,
                onEdit = { onEditProfile(profile) },
                onDelete = { onDeleteProfile(profile) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCreateProfile,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Profile")
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val allergenText = profile.trackedAllergens.keys
                    .joinToString(", ") { it.displayName }
                    .ifEmpty { "No allergens" }
                val subtitle = if (profile.hasAsthma) "$allergenText · Asthma" else allergenText
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
