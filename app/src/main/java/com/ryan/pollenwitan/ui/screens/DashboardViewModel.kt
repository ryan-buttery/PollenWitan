package com.ryan.pollenwitan.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.util.DefaultLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val conditions: CurrentConditions) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val repository: AirQualityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            repository.getCurrentConditions(
                DefaultLocation.LATITUDE,
                DefaultLocation.LONGITUDE
            ).fold(
                onSuccess = { _uiState.value = DashboardUiState.Success(it) },
                onFailure = { _uiState.value = DashboardUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }
}
