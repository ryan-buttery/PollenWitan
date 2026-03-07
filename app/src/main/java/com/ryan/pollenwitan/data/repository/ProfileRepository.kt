package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfiles(): Flow<List<UserProfile>>
    fun getSelectedProfileId(): Flow<String>
    suspend fun selectProfile(profileId: String)
}
