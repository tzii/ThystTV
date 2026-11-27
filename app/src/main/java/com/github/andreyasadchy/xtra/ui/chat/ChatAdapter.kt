package com.github.andreyasadchy.xtra.ui.chat

import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.getSpans
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.StvBadge
import com.github.andreyasadchy.xtra.model.chat.StvUser
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.ui.view.ChatMessageTextView
import com.github.andreyasadchy.xtra.ui.view.NamePaintImageSpan
import com.github.andreyasadchy.xtra.util.chat.ChatAdapterUtils
import java.util.Random

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val localTwitchEmotes: List<TwitchEmote>,
    private val thirdPartyEmotes: EmoteNameIndex,
    private val globalBadges: List<TwitchBadge>,
    private val channelBadges: List<TwitchBadge>,
    private val cheerEmotes: List<CheerEmote>,
    private val namePaints: List<NamePaint>,
    private val stvBadges: List<StvBadge>,
    private val personalEmoteSets: Map<String, List<Emote>>,
    private val stvUsers: Map<String, StvUser>,
    private val enableTimestamps: Boolean,
    private val timestampFormat: String?,
    private val firstMsgVisibility: Int,
    private val firstChatMsg: String,
    private val redeemedChatMsg: String,
    private val redeemedNoMsg: String,
    private val rewardChatMsg: String,
    private val replyMessage: String,
    private val useRandomColors: Boolean,
    private val useReadableColors: Boolean,
    private val isLightTheme: Boolean,
    private val nameDisplay: String?,
    private val useBoldNames: Boolean,
    private val showNamePaints: Boolean,
    private val showStvBadges: Boolean,
    private val showPersonalEmotes: Boolean,
    private val showSystemMessageEmotes: Boolean,
    private val chatUrl: String?,
    private val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    private val messageTextSize: Float,
    private val emoteSize: Int,
    private val badgeSize: Int,
    private val emoteQuality: String,
    private val animateGifs: Boolean,
    private val enableOverlayEmotes: Boolean,
    private val translateMessage: (ChatMessage, String?) -> Unit,
    private val showLanguageDownloadDialog: (ChatMessage, String) -> Unit,
    private val channelId: String?,
    private val loggedInUser: String?,
    private val messageClickListener: ((String?) -> Unit)?,
    private val replyClickListener: (() -> Unit)?,
    private val imageClickListener: ((String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit)?,
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    var translateAllMessages = false
    /** Set while the list floats over video; null on an app surface. */
    var overlayStyle: ChatOverlayStyle? = null
        private set
    /** Animated emotes stay still while the floating chat is hidden. */
    var animationsPaused = false
        private set
    private val useDarkOverlay get() = overlayStyle != null
    private var selectedMessage: ChatMessage? = null
    private val random = Random()
    private val userColors = HashMap<String, Int>()
    private val savedColors = HashMap<String, Int>()
    /**
     * Readable name colors are adapted to the background they sit on, so a
     * light theme's chat keeps separate ones for the dark floating card.
     */
    private val overlayUserColors = HashMap<String, Int>()
    private val overlaySavedColors = HashMap<String, Int>()
    private val rowUserColors get() = if (effectiveIsLightTheme() == isLightTheme) userColors else overlayUserColors
    private val rowSavedColors get() = if (effectiveIsLightTheme() == isLightTheme) savedColors else overlaySavedColors
    private val savedLocalTwitchEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalBadges = mutableMapOf<String, ByteArray>()
    private val savedLocalCheerEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalEmotes = mutableMapOf<String, ByteArray>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatMessage = synchronized(messages) {
            messages.getOrNull(position)
        } ?: return
        val result = ChatAdapterUtils.prepareChatMessage(
            chatMessage, holder.textView, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg, redeemedChatMsg, redeemedNoMsg,
            rewardChatMsg, replyMessage, null, useRandomColors, random, useReadableColors, effectiveIsLightTheme(), nameDisplay, useBoldNames || useDarkOverlay, showNamePaints,
            namePaints, showStvBadges, stvBadges, showPersonalEmotes, personalEmoteSets, stvUsers, enableOverlayEmotes, showSystemMessageEmotes,
            loggedInUser, chatUrl, getEmoteBytes, rowUserColors, rowSavedColors, translateAllMessages, translateMessage, showLanguageDownloadDialog,
            true, localTwitchEmotes, thirdPartyEmotes, globalBadges, channelBadges, cheerEmotes, savedLocalTwitchEmotes, savedLocalBadges,
            savedLocalCheerEmotes, savedLocalEmotes
        )
        holder.imageBinder.bind({ holder.bind(chatMessage, result.builder) }) { onImageLoaded ->
            ChatAdapterUtils.loadImages(
                holder.textView, { onImageLoaded() }, result.images, result.imagePaint, result.userName, result.userNameStartIndex,
                result.builder, result.translated, emoteSize, badgeSize, emoteQuality, animateGifs, enableOverlayEmotes,
                chatMessage, rowSavedColors, useReadableColors, effectiveIsLightTheme(), showLanguageDownloadDialog, true,
                startAnimations = { !animationsPaused },
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_STYLE }) {
            holder.applyStyle()
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /**
     * Applies the floating chat's look, or null for an app surface. Rows on
     * screen restyle in place; moving between an app surface and the video
     * also rebuilds their text, since name colors follow the background.
     */
    fun setOverlayStyle(style: ChatOverlayStyle?, notifyRows: Boolean) {
        val previous = overlayStyle
        if (previous == style) return
        overlayStyle = style
        if (!notifyRows || itemCount == 0) return
        if ((previous == null) != (style == null)) {
            notifyItemRangeChanged(0, itemCount)
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STYLE)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.imageBinder.cancel()
    }

    fun updateTranslation(chatMessage: ChatMessage, item: TextView, previousTranslation: String?) {
        (item.text as? SpannableString)?.let { text ->
            val builder = SpannableStringBuilder()
            builder.append(
                if (previousTranslation != null) {
                    text.dropLast(previousTranslation.length + 1)
                } else {
                    text
                }
            )
            if (!chatMessage.translationFailed) {
                ChatAdapterUtils.addTranslation(chatMessage, builder, builder.length, rowSavedColors, useReadableColors, effectiveIsLightTheme(), showLanguageDownloadDialog, true)
            }
            item.text = builder
        }
    }

    fun createMessageClickedChatAdapter(): MessageClickedChatAdapter {
        return MessageClickedChatAdapter(
            messages, localTwitchEmotes, thirdPartyEmotes, globalBadges, channelBadges, cheerEmotes, namePaints, stvBadges, personalEmoteSets,
            stvUsers, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg, redeemedChatMsg, redeemedNoMsg, rewardChatMsg, replyMessage,
            { chatMessage -> selectedMessage = chatMessage; replyClickListener?.invoke() },
            { url, name, format, isAnimated, source, thirdParty, emoteId -> imageClickListener?.invoke(url, name, format, isAnimated, source, thirdParty, emoteId) },
            useRandomColors, useReadableColors, isLightTheme, nameDisplay, useBoldNames, showNamePaints, showStvBadges, showPersonalEmotes,
            showSystemMessageEmotes, chatUrl, getEmoteBytes, messageTextSize, emoteSize, badgeSize,
            emoteQuality, animateGifs, enableOverlayEmotes, translateAllMessages, translateMessage, showLanguageDownloadDialog, random, userColors,
            savedColors, savedLocalTwitchEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes, loggedInUser, selectedMessage
        )
    }

    fun createReplyClickedChatAdapter(): ReplyClickedChatAdapter {
        return ReplyClickedChatAdapter(
            messages, localTwitchEmotes, thirdPartyEmotes, globalBadges, channelBadges, cheerEmotes, namePaints, stvBadges, personalEmoteSets,
            stvUsers, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg, redeemedChatMsg, redeemedNoMsg, rewardChatMsg, replyMessage,
            { url, name, format, isAnimated, source, thirdParty, emoteId -> imageClickListener?.invoke(url, name, format, isAnimated, source, thirdParty, emoteId) },
            useRandomColors, useReadableColors, isLightTheme, nameDisplay, useBoldNames, showNamePaints, showStvBadges, showPersonalEmotes,
            showSystemMessageEmotes, chatUrl, getEmoteBytes, messageTextSize, emoteSize, badgeSize,
            emoteQuality, animateGifs, enableOverlayEmotes, translateAllMessages, translateMessage, showLanguageDownloadDialog, random, userColors,
            savedColors, savedLocalTwitchEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes, loggedInUser, selectedMessage
        )
    }

    override fun getItemCount(): Int = synchronized(messages) {
        messages.size
    }

    private fun effectiveIsLightTheme() = isLightTheme && !useDarkOverlay

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (animateGifs && !animationsPaused) setRowAnimating(holder.textView, true)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (animateGifs) setRowAnimating(holder.textView, false)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (animateGifs) {
            for (i in 0 until recyclerView.childCount) {
                (recyclerView.getChildAt(i) as? TextView)?.let { setRowAnimating(it, false) }
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /** Stops or restarts animated emotes and name paints on the rows currently shown. */
    fun setAnimationsPaused(recyclerView: RecyclerView, paused: Boolean) {
        if (animationsPaused == paused) return
        animationsPaused = paused
        if (!animateGifs) return
        for (i in 0 until recyclerView.childCount) {
            (recyclerView.getChildAt(i) as? TextView)?.let { setRowAnimating(it, !paused) }
        }
    }

    private fun setRowAnimating(textView: TextView, running: Boolean) {
        val text = textView.text as? Spannable ?: return
        fun Animatable.set() = if (running) start() else stop()
        text.getSpans<ImageSpan>().forEach { span ->
            val drawable = span.drawable
            if (drawable is Animatable) {
                drawable.set()
            } else if (drawable is LayerDrawable) {
                for (i in 0 until drawable.numberOfLayers) {
                    (drawable.getDrawable(i) as? Animatable)?.set()
                }
            }
        }
        text.getSpans<NamePaintImageSpan>().forEach {
            (it.drawable as? Animatable)?.set()
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView
        val imageBinder = ChatRowImageBinder(textView::postOnAnimation, textView::removeCallbacks)
        private val defaultTextColors = textView.textColors
        private val defaultLinkTextColors = textView.linkTextColors
        private val defaultHighlightColor = textView.highlightColor
        private val defaultPadding = intArrayOf(textView.paddingLeft, textView.paddingTop, textView.paddingRight, textView.paddingBottom)
        private val density = textView.resources.displayMetrics.density

        fun bind(chatMessage: ChatMessage, formattedMessage: SpannableStringBuilder) {
            textView.text = formattedMessage
            applyStyle()
            textView.apply {
                if (chatMessage.isReply) {
                    movementMethod = null
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    TooltipCompat.setTooltipText(this, chatMessage.replyParent?.message ?: chatMessage.replyParent?.systemMsg)
                    setOnClickListener {
                        if (selectionStart == -1 && selectionEnd == -1) {
                            selectedMessage = chatMessage.replyParent
                            messageClickListener?.invoke(channelId)
                        }
                    }
                } else {
                    movementMethod = LinkMovementMethod.getInstance()
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    TooltipCompat.setTooltipText(this, chatMessage.message ?: chatMessage.systemMsg)
                    setOnClickListener {
                        if (selectionStart == -1 && selectionEnd == -1) {
                            selectedMessage = chatMessage
                            messageClickListener?.invoke(channelId)
                        }
                    }
                }
            }
        }

        /** Size, colors, outline and weight from [overlayStyle]; the text itself stays. */
        fun applyStyle() {
            textView.apply {
                val overlay = overlayStyle
                textSize = messageTextSize * (overlay?.textScale ?: 1f)
                if (overlay != null) {
                    setTextColor(FLOATING_CHAT_TEXT_COLOR)
                    setLinkTextColor(FLOATING_CHAT_LINK_COLOR)
                    highlightColor = FLOATING_CHAT_LINK_HIGHLIGHT_COLOR
                    // Room for the halo and an even inset from the card's rounded edge.
                    val horizontal = (OVERLAY_PADDING_H_DP * density).toInt()
                    val vertical = (OVERLAY_PADDING_V_DP * density).toInt()
                    if (paddingLeft != horizontal || paddingTop != vertical) setPadding(horizontal, vertical, horizontal, vertical)
                } else {
                    setTextColor(defaultTextColors)
                    setLinkTextColor(defaultLinkTextColors)
                    highlightColor = defaultHighlightColor
                    if (paddingLeft != defaultPadding[0] || paddingTop != defaultPadding[1]) {
                        setPadding(defaultPadding[0], defaultPadding[1], defaultPadding[2], defaultPadding[3])
                    }
                }
                if (this is ChatMessageTextView) {
                    setOverlayTreatment(overlay?.treatment)
                } else if (overlay?.treatment == ChatOverlayStyle.Treatment.NONE || overlay == null) {
                    setShadowLayer(0f, 0f, 0f, 0)
                } else {
                    setShadowLayer(2f * density, 0f, 0.5f * density, Color.BLACK)
                }
                val typeface = if (overlay?.boldText == true) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                if (this.typeface != typeface) this.typeface = typeface
            }
        }
    }

    private companion object {
        /** Restyle rows in place: their text and images stay. */
        const val PAYLOAD_STYLE = "style"
        val FLOATING_CHAT_TEXT_COLOR = Color.WHITE
        val FLOATING_CHAT_LINK_COLOR = 0xFF4FC3F7.toInt()
        val FLOATING_CHAT_LINK_HIGHLIGHT_COLOR = 0x334FC3F7
        const val OVERLAY_PADDING_H_DP = 10f
        // Room above and below each line for the Clear outline.
        const val OVERLAY_PADDING_V_DP = 3f
    }
}
