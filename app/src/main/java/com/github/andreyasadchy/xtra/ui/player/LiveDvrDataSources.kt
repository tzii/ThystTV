package com.github.andreyasadchy.xtra.ui.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import java.io.ByteArrayOutputStream

/**
 * Copies what the player is already downloading into a [LiveDvrBuffer], adding no requests
 * while live. Everything here sits behind the player's own loads, so no capture problem may
 * reach them: only upstream errors propagate.
 */
@OptIn(UnstableApi::class)
internal class LiveDvrHlsDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val buffer: LiveDvrBuffer,
) : HlsDataSourceFactory {
    override fun createDataSource(dataType: Int): DataSource = upstream.createDataSource().let { source ->
        when (dataType) {
            C.DATA_TYPE_MANIFEST -> PlaylistCapture(source, buffer)
            C.DATA_TYPE_MEDIA, C.DATA_TYPE_MEDIA_INITIALIZATION -> SegmentCapture(source, buffer)
            else -> source
        }
    }
}

@OptIn(UnstableApi::class)
private abstract class CapturingDataSource(private val upstream: DataSource) : DataSource {
    private var expected = C.LENGTH_UNSET.toLong()
    private var received = 0L

    /** Where the bytes really came from, which is what relative playlist entries resolve against. */
    protected var url: String = ""
        private set

    protected abstract fun onOpen(dataSpec: DataSpec)
    protected abstract fun onData(data: ByteArray, offset: Int, length: Int)
    protected abstract fun onEnd()
    protected abstract fun onClose()

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        url = (upstream.uri ?: dataSpec.uri).toString()
        expected = length
        received = 0L
        onOpen(dataSpec)
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            onEnd()
        } else if (read > 0) {
            received += read
            onData(buffer, offset, read)
            // A parser can stop at the declared length without ever asking past it.
            if (expected != C.LENGTH_UNSET.toLong() && received >= expected) onEnd()
        }
        return read
    }

    override fun close() {
        try {
            upstream.close()
        } finally {
            onClose()
        }
    }

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
}

@OptIn(UnstableApi::class)
private class PlaylistCapture(upstream: DataSource, private val buffer: LiveDvrBuffer) : CapturingDataSource(upstream) {
    private var body: ByteArrayOutputStream? = null

    override fun onOpen(dataSpec: DataSpec) {
        body = ByteArrayOutputStream()
    }

    override fun onData(data: ByteArray, offset: Int, length: Int) {
        val current = body ?: return
        if (current.size() + length > MAX_PLAYLIST_BYTES) body = null else current.write(data, offset, length)
    }

    override fun onEnd() {
        val complete = body ?: return
        body = null
        try {
            DvrPlaylistParser.parse(complete.toString(Charsets.UTF_8.name()), url)?.let(buffer::onPlaylist)
        } catch (_: Exception) {
        }
    }

    override fun onClose() {
        body = null
    }

    private companion object {
        const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
    }
}

@OptIn(UnstableApi::class)
private class SegmentCapture(upstream: DataSource, private val buffer: LiveDvrBuffer) : CapturingDataSource(upstream) {
    private var writer: DvrSegmentWriter? = null

    override fun onOpen(dataSpec: DataSpec) {
        // A resumed or partial load holds only part of the segment, so it can't be stored.
        // Named as the playlist lists it, wherever a redirect sent the request.
        writer = if (dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) buffer.openWriter(dataSpec.uri.toString()) else null
    }

    override fun onData(data: ByteArray, offset: Int, length: Int) {
        writer?.write(data, offset, length)
    }

    override fun onEnd() {
        writer?.finish()
        writer = null
    }

    override fun onClose() {
        writer?.abort()
        writer = null
    }
}
