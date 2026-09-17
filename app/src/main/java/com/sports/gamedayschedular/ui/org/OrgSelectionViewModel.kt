package com.sports.gamedayschedular.ui.org

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sports.gamedayschedular.data.model.Organization
import com.sports.gamedayschedular.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class OrgSelectionViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    val organizations: StateFlow<List<Organization>> = repository.getOrganizations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
