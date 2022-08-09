package com.hover.stax.domain.repository

import com.hover.stax.domain.model.Account
import com.hover.stax.channels.Channel
import kotlinx.coroutines.flow.Flow

interface AccountRepository {

    val fetchAccounts: Flow<List<Account>>

    suspend fun createAccounts(channels: List<Channel>)
    suspend fun createAccount(channel: Channel, subscriptionId: Int?, isDefault: Boolean)

    suspend fun setDefaultAccount(account: Account)
    suspend fun getTelecomAccounts(subscriberIds: IntArray) : List<Account>
}