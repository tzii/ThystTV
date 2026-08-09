package com.github.andreyasadchy.xtra.repository.datasource

import com.github.andreyasadchy.xtra.model.ui.Stream

internal fun Stream.hasBroadcasterIdentity(): Boolean =
    channelId != null || channelLogin != null

internal fun Iterable<Stream>.filterValidBroadcasters(): List<Stream> =
    filter { it.hasBroadcasterIdentity() }
