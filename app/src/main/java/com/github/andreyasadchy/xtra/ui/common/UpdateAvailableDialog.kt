package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUpdateAvailableBinding
import com.github.andreyasadchy.xtra.model.ui.UpdateInfo
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin

object UpdateAvailableDialog {

    fun show(
        context: Context,
        inflater: LayoutInflater,
        updateInfo: UpdateInfo,
        onDownload: () -> Unit,
        onLater: () -> Unit,
        onGithub: (String) -> Unit,
    ): AlertDialog {
        val binding = DialogUpdateAvailableBinding.inflate(inflater)
        bindRelease(context, binding, updateInfo)

        val dialog = context.getAlertDialogBuilder()
            .setView(binding.root)
            .create()

        binding.downloadButton.setOnClickListener {
            dialog.dismiss()
            onDownload()
        }
        binding.laterButton.setOnClickListener {
            dialog.dismiss()
            onLater()
        }
        binding.githubButton.isVisible = !updateInfo.releaseUrl.isNullOrBlank()
        binding.githubButton.setOnClickListener {
            updateInfo.releaseUrl?.let { url ->
                dialog.dismiss()
                onGithub(url)
            }
        }

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.45f)
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }

    private fun bindRelease(context: Context, binding: DialogUpdateAvailableBinding, updateInfo: UpdateInfo) {
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val chipColor = ColorUtils.blendARGB(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainer),
            primary,
            0.16f
        )

        binding.updateVersionChip.text = updateInfo.tagName
        binding.updateVersionChip.background = roundedDrawable(chipColor, 18f, context)
        binding.updateVersionChip.setTextColor(onSurface)
        binding.updateDate.text = updateInfo.publishedAt?.substringBefore("T")?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown)

        Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .build()
            .setMarkdown(binding.releaseNotesText, releaseNotesMarkdown(context, updateInfo))
        binding.releaseNotesText.movementMethod = LinkMovementMethod.getInstance()

        binding.releaseNotesScroll.doOnPreDraw { scroll ->
            val maxHeight = (context.resources.displayMetrics.heightPixels * 0.38f)
                .toInt()
                .coerceAtMost(dp(context, 360f))
            if (scroll.height > maxHeight) {
                scroll.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = maxHeight
                }
            }
        }
    }

    private fun releaseNotesMarkdown(context: Context, updateInfo: UpdateInfo): String {
        val notes = updateInfo.releaseNotes?.takeIf { it.isNotBlank() }
            ?: return context.getString(R.string.no_release_notes)
        val lines = notes.lineSequence().dropWhile { it.isBlank() }.toList()
        val first = lines.firstOrNull()?.trim().orEmpty()
        val firstLooksLikeDuplicateTitle = first.startsWith("#") && listOfNotNull(
            updateInfo.tagName,
            updateInfo.versionName,
            updateInfo.title,
            updateInfo.publishedAt?.substringBefore("T")
        ).any { first.contains(it, ignoreCase = true) }
        val body = if (firstLooksLikeDuplicateTitle) lines.drop(1) else lines
        return body.joinToString("\n").trim().ifBlank {
            context.getString(R.string.no_release_notes)
        }
    }

    private fun roundedDrawable(color: Int, radiusDp: Float, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
    }
}
