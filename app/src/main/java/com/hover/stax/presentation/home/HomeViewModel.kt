/*
 * Copyright 2022 Stax
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hover.stax.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hover.sdk.actions.HoverAction
import com.hover.stax.data.accounts.AccountRepository
import com.hover.stax.data.actions.ActionRepo
import com.hover.stax.domain.use_case.financial_tips.TipsUseCase
import com.hover.stax.model.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    private val accountsRepo: AccountRepository,
    private val actionRepo: ActionRepo,
    private val tipsUseCase: TipsUseCase
) : ViewModel() {

    private val _homeState = MutableLiveData(HomeState())
    val homeState: LiveData<HomeState> get() = _homeState

    init {
        fetchData()
    }

    private fun fetchData() {
        getAccounts()
        getFinancialTips()
        getDismissedFinancialTips()
    }

    private fun getAccounts() = viewModelScope.launch {
        accountsRepo.addedAccounts.collect { accounts ->
            _homeState.value = homeState.value?.copy(accounts = accounts)
            getBonusList(accounts.map { it.countryAlpha2 }.toTypedArray())
        }
    }

    private fun getBonusList(countries: Array<String>) = viewModelScope.launch {
        bonusListToFlow(countries).collect { bonusList ->
            if (bonusList is com.hover.stax.model.Resource.Success)
                _homeState.value = homeState.value?.copy(bonuses = bonusList.data ?: emptyList())
        }
    }

    private fun bonusListToFlow(countries: Array<String>): Flow<com.hover.stax.model.Resource<List<HoverAction>>> =
        flow {
            Timber.e("Looking for bounties from: ${countries.contentToString()}")
            try {
                emit(com.hover.stax.model.Resource.Loading())

                emit(
                    com.hover.stax.model.Resource.Success(
                        actionRepo.getBonusActionsByCountry(
                            countries
                        )
                    )
                )
            } catch (e: Exception) {
                emit(com.hover.stax.model.Resource.Error("Error fetching tips"))
            }
        }

    private fun getFinancialTips() = tipsUseCase().onEach { result ->
        if (result is com.hover.stax.model.Resource.Success)
            _homeState.value = homeState.value?.copy(financialTips = result.data ?: emptyList())
    }.launchIn(viewModelScope)

    private fun getDismissedFinancialTips() {
        _homeState.value =
            homeState.value?.copy(dismissedTipId = tipsUseCase.getDismissedTipId() ?: "")
    }

    fun dismissTip(id: String) {
        viewModelScope.launch {
            tipsUseCase.dismissTip(id)
            _homeState.value = homeState.value?.copy(dismissedTipId = id)
        }
    }
}