package com.fenyx.jtv.data

import android.content.Context

/**
 * LOCAL BUILD CHANGE (not upstream): upstream v2's ZeeRepository replaced Sony, Zee and Star channels with
 * streams from third-party re-broadcast servers (sliv.tgaadi.workers.dev, vk-playlist.pages.dev, a GitHub
 * playlist) and with other people's Jio stream cookies and ClearKey keys fetched from allinonereborn2.online
 * or embedded in the source. That is unauthorised redistribution, sends requests to unknown servers, and
 * broke when those servers went away (Sony SAB: 404).
 *
 * This build keeps only the same three entry points, all inert, so every channel is played through Jio's
 * own API under the user's own login, exactly like v1.5.x.
 */
object ZeeRepository {
    /** No extra channels: the list is Jio's own. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun loadZeeChannels(context: Context): List<Channel> = emptyList()

    /** No channel is routed away from Jio. */
    @Suppress("UNUSED_PARAMETER")
    fun isZeeChannel(channelId: String): Boolean = false

    /** Never supplies a stream; callers fall through to Jio's playback API. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getStreamDataForChannel(context: Context, channelId: String, forceRefresh: Boolean = false): JioApiClient.StreamData? = null
}
