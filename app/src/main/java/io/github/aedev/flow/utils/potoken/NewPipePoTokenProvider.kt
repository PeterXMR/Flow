package io.github.aedev.flow.utils.potoken

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult as ExtractorPoTokenResult

/**
 * PoToken provider for the NewPipe extractor path.
 *
 * Delegates to the shared [WebPoTokenSession] singleton so the WEB BotGuard attestation is
 * minted **once per app session** and reused by every extractor branch.
 *
 * Previously this object kept its own [PoTokenGenerator] and fetched its own visitorData, so the
 * NewPipe branch spun up a second, cold BotGuard WebView on the first video open — duplicating the
 * native InnerTube branch's work and bypassing FlowApplication's startup prewarm. Because a video
 * open races the NewPipe and InnerTube resolvers, that meant two independent cold attestations on a
 * deep-link cold start. Sharing the session collapses them into one warm attestation, so the first
 * link-open no longer pays for a redundant BotGuard run.
 *
 * The [ExtractorPoTokenResult] carries the same [visitorData] the token was minted against, so the
 * NewPipe extractor uses a matching visitorData/poToken pair for its WEB client player request.
 */
object NewPipePoTokenProvider : PoTokenProvider {
    private const val TAG = "NewPipePoTokenProvider"

    override fun getWebClientPoToken(videoId: String): ExtractorPoTokenResult? = runBlocking {
        val visitorData = WebPoTokenSession.sessionVisitorData()
        if (visitorData.isNullOrBlank()) {
            Log.w(TAG, "No session visitorData available for $videoId")
            return@runBlocking null
        }

        val result = WebPoTokenSession.mintForVisitorData(videoId, visitorData)
        if (result == null) {
            Log.w(TAG, "Shared PoToken session returned no token for $videoId")
            return@runBlocking null
        }

        ExtractorPoTokenResult(
            visitorData,
            result.playerRequestPoToken,
            result.streamingDataPoToken,
        )
    }

    override fun getWebEmbedClientPoToken(videoId: String): ExtractorPoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): ExtractorPoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): ExtractorPoTokenResult? = null
}
