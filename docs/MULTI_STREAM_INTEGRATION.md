# Multi-Stream Integration Guide

This guide provides step-by-step instructions for completing the multi-stream viewing feature integration.

## Overview

The multi-stream infrastructure is **70-80% complete**. All core components have been built:
- ✅ Service layer (ExoPlayerService with dual player slots)
- ✅ UI components (StreamSurfaceView)
- ✅ Layout system (MultiStreamLayoutManager with 4 modes)
- ✅ Controller (MultiStreamController)
- ✅ Stream picker (StreamPickerDialog)

**What's missing**: Integration with PlayerFragment and ExoPlayerFragment to wire everything together.

## Architecture Recap

### Service Layer
```
ExoPlayerService
├── primaryPlayer (existing player, backward-compatible)
├── secondaryPlayer (new, muted by default)
├── createSecondaryPlayer() - creates muted secondary player
├── releaseSecondaryPlayer() - cleans up secondary player
├── setAudioSource(slot) - switches audio between players
├── toggleAudioSource() - convenience toggle
└── updateMetadataForActiveSlot() - updates MediaSession
```

### UI Components
```
StreamSurfaceView (reusable)
├── AspectRatioFrameLayout (16:9 aspect ratio)
├── SurfaceView (for ExoPlayer rendering)
├── BufferingIndicator (circular progress)
├── AudioIndicator (speaker icon)
├── StreamInfoOverlay (channel name, title)
└── TouchOverlay (tap to switch audio)

fragment_player.xml
├── primaryPlayerContainer (existing)
├── secondaryPlayerContainer (new)
├── primaryAudioIndicator (new, hidden by default)
├── secondaryAudioIndicator (new)
├── secondaryStreamInfoOverlay (new)
├── secondaryCloseButton (new, for PiP mode)
└── multiStreamButton (in player_layout.xml)
```

### Controller
```
MultiStreamController
├── addSecondaryStream(channelId, channelName, thumbnailUrl)
├── removeSecondaryStream()
├── switchAudioTo(slot)
├── toggleAudio()
├── cycleLayoutMode()
├── bindService(service: ExoPlayerService)
├── unbindService()
└── Callbacks: onAudioSwitched, onSecondaryStreamClosed, onLayoutModeChanged
```

## Integration Steps

### Step 1: Update PlayerFragment (Abstract Base)

**File**: `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`

**Add fields** (around line 117, after `chatFragment`):
```kotlin
// Multi-stream support
protected var multiStreamController: MultiStreamController? = null
private var multiStreamBinding: FragmentPlayerBinding? = null
```

**In `onCreateView()`** (around line 207):
```kotlin
override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _binding = FragmentPlayerBinding.inflate(inflater, container, false)
    multiStreamBinding = _binding // Use the same binding for multi-stream views
    return binding.root
}
```

**Add abstract methods** (around line 189, with other abstract methods):
```kotlin
open fun onServiceConnected(service: ExoPlayerService?) {}
open fun onServiceDisconnected() {}
```

**Add cleanup in `onDestroyView()`** (if method exists, or add it):
```kotlin
override fun onDestroyView() {
    multiStreamController?.unbindService()
    multiStreamController = null
    _binding = null
    multiStreamBinding = null
    super.onDestroyView()
}
```

### Step 2: Update ExoPlayerFragment

**File**: `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/ExoPlayerFragment.kt`

**Add fields** (around line 74, after `playerListener`):
```kotlin
private var multiStreamController: MultiStreamController? = null
private var multiStreamBinding: FragmentPlayerBinding? = null
```

**Add `multiStreamContainer` binding** (you'll need to find this in the XML):
```kotlin
// In onViewCreated(), after binding setup
multiStreamBinding = FragmentPlayerBinding.bind(binding.root.findViewById(R.id.multiStreamContainer))
```

**Initialize MultiStreamController** (in `onServiceConnected()`, after line 83):
```kotlin
// After player?.setVideoSurfaceView(binding.playerSurface)
playbackService?.let { service ->
    // Initialize multi-stream controller
    multiStreamBinding?.let { binding ->
        multiStreamController = MultiStreamController(requireContext(), binding, binding).apply {
            bindService(service)

            // Set up callbacks
            setOnAudioSwitchedListener { slot ->
                updateAudioIndicators(slot)
            }

            setOnSecondaryStreamClosedListener {
                // Reset UI when secondary stream is removed
                binding.primaryAudioIndicator?.isVisible = false
            }

            setOnLayoutModeChangedListener { mode ->
                // Handle layout mode changes if needed
            }
        }
    }
}
```

**Handle multi-stream button click** (find where other player control buttons are set up, around line 699-830 in `playerControls` setup):
```kotlin
// Find the multi-stream button in player_layout.xml
binding.playerControls.findViewById<ImageView>(R.id.multiStreamButton)?.setOnClickListener {
    showStreamPicker()
}
```

**Add `showStreamPicker()` method**:
```kotlin
private fun showStreamPicker() {
    val currentChannelId = requireArguments().getString(KEY_CHANNEL_ID)

    StreamPickerDialog.newInstance(
        excludeChannelId = currentChannelId
    ).apply {
        setOnStreamSelectedListener { stream ->
            loadSecondaryStream(stream)
        }
    }.show(childFragmentManager, "StreamPicker")
}
```

**Add `loadSecondaryStream()` method**:
```kotlin
private fun loadSecondaryStream(stream: com.github.andreyasadchy.xtra.model.ui.Stream) {
    playbackService?.let { service ->
        multiStreamController?.let { controller ->
            // Add secondary stream to controller
            controller.addSecondaryStream(
                channelId = stream.channelId ?: return@let,
                channelName = stream.channelName ?: "",
                thumbnailUrl = stream.thumbnailUrl
            )

            // Load stream in secondary player
            // You'll need to get the stream URL - check how startStream() does it
            val streamUrl = getStreamUrl(stream.channelId) // You may need to implement this
            if (streamUrl != null) {
                service.loadSecondaryStream(streamUrl, stream.channelName ?: "")
            }

            // Show primary audio indicator since we now have two streams
            binding.playerControls.primaryAudioIndicator?.isVisible = true
        }
    }
}
```

**Implement `getStreamUrl()`** (check how `startStream()` gets the URL from ViewModel):
```kotlin
private fun getStreamUrl(channelId: String?): String? {
    // You'll need to adapt this based on how the ViewModel provides stream URLs
    // Check the startStream() method to see how it gets the URL
    // It likely comes from viewModel.streamResult.value
    return null // TODO: Implement
}
```

**Add `updateAudioIndicators()` method**:
```kotlin
private fun updateAudioIndicators(activeSlot: PlayerSlot) {
    val primaryHasAudio = activeSlot == PlayerSlot.PRIMARY
    val secondaryHasAudio = activeSlot == PlayerSlot.SECONDARY

    multiStreamBinding?.let { binding ->
        // Update primary audio indicator (visible when secondary stream exists)
        binding.primaryAudioIndicator?.isVisible = true
        binding.primaryAudioIndicator?.alpha = if (primaryHasAudio) 1.0f else 0.5f

        // Update secondary audio indicator
        binding.secondaryAudioIndicatorContainer?.isVisible = true
        binding.secondaryAudioIndicatorContainer?.alpha = if (secondaryHasAudio) 1.0f else 0.5f
    }
}
```

**Handle fragment lifecycle**:
```kotlin
override fun onStop() {
    super.onStop()
    multiStreamController?.pauseSecondaryStream()
}

override fun onStart() {
    super.onStart()
    multiStreamController?.resumeSecondaryStream()
}
```

**Update `onServiceDisconnected()`** (around line 467):
```kotlin
override fun onServiceDisconnected(name: ComponentName?) {
    playbackService = null
    multiStreamController?.unbindService()
}
```

### Step 3: Update ExoPlayerService

**File**: `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/ExoPlayerService.kt`

**Add `loadSecondaryStream()` method**:
```kotlin
fun loadSecondaryStream(url: String, channelName: String) {
    secondaryPlayer?.let { player ->
        val mediaItem = MediaItem.Builder()
            .setUri(url.toUri())
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(channelName)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.playWhenReady = true
        player.prepare()

        // Secondary player is already muted, no need to mute again
    }
}
```

**Add `pauseSecondaryStream()` and `resumeSecondaryStream()`**:
```kotlin
fun pauseSecondaryStream() {
    secondaryPlayer?.playWhenReady = false
}

fun resumeSecondaryStream() {
    secondaryPlayer?.playWhenReady = true
}
```

### Step 4: Handle PiP Mode

**In PlayerFragment, find the PiP setup code** (around line 266-272):

```kotlin
// When entering PiP mode, hide multi-stream UI
override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    multiStreamController?.apply {
        if (isInPictureInPictureMode) {
            // Save current state
            setPiPMode(true)

            // Switch to SINGLE layout (PiP only supports one stream)
            if (hasSecondaryStream()) {
                // Optionally: save secondary stream info to restore later
                removeSecondaryStream()
            }
        } else {
            // Restore from PiP
            setPiPMode(false)
            // Optionally: restore secondary stream if it was active
        }
    }
}
```

### Step 5: Handle Device Rotation

**In PlayerFragment, add state saving**:

```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    multiStreamController?.let { controller ->
        outState.putBoolean(KEY_HAS_SECONDARY_STREAM, controller.hasSecondaryStream())
        outState.putString(KEY_SECONDARY_CHANNEL_ID, controller.getSecondaryChannelId())
        outState.putInt(KEY_LAYOUT_MODE, controller.getCurrentLayoutMode().ordinal)
    }
}

// Add these constants at the top
companion object {
    // ... existing constants ...
    private const val KEY_HAS_SECONDARY_STREAM = "has_secondary_stream"
    private const val KEY_SECONDARY_CHANNEL_ID = "secondary_channel_id"
    private const val KEY_LAYOUT_MODE = "layout_mode"
}
```

**Restore state in `onViewCreated()`**:
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    savedInstanceState?.let { state ->
        if (state.getBoolean(KEY_HAS_SECONDARY_STREAM, false)) {
            val channelId = state.getString(KEY_SECONDARY_CHANNEL_ID)
            val layoutMode = state.getInt(KEY_LAYOUT_MODE, 0)

            // Restore secondary stream and layout mode
            // This might require reloading stream data
        }
    }
}
```

## Key Patterns to Follow

### 1. Service Binding Pattern (from ExoPlayerFragment)
```kotlin
val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as ExoPlayerService.ServiceBinder
        playbackService = binder.getService()
        // Initialize multi-stream here
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        playbackService = null
    }
}
serviceConnection = connection
val intent = Intent(requireContext(), ExoPlayerService::class.java)
requireContext().startService(intent)
requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
```

### 2. Dialog Pattern (from existing dialogs)
```kotlin
MyDialog.newInstance(...).show(childFragmentManager, "tag")
```

### 3. ViewModel Pattern for Stream Loading
Check how `startStream()` gets the URL from ViewModel:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.streamResult.collectLatest {
            if (it != null) {
                startStream(it)
                viewModel.streamResult.value = null
            }
        }
    }
}
```

## Testing Checklist

After integration, test:

- [ ] Multi-stream button appears in player controls
- [ ] Tapping button opens StreamPickerDialog
- [ ] StreamPickerDialog shows followed live streams
- [ ] Search functionality works in StreamPickerDialog
- [ ] Selecting a stream adds secondary stream
- [ ] Secondary stream plays muted
- [ ] Layout animates to SPLIT_HORIZONTAL (default)
- [ ] Tapping primary stream switches audio to primary
- [ ] Tapping secondary stream switches audio to secondary
- [ ] Audio indicators update correctly
- [ ] Tap-to-switch-audio works on both streams
- [ ] Cycling layout mode works (need to add button or gesture)
- [ ] Close button works in PiP layout mode
- [ ] PiP mode disables multi-stream correctly
- [ ] Device rotation preserves state (or gracefully resets)
- [ ] Backing out of player cleans up secondary stream
- [ ] No memory leaks

## Troubleshooting

### Issue: Multi-stream button doesn't appear
- Check if button ID is correct in `player_layout.xml`
- Check if button is hidden by default and needs to be shown

### Issue: StreamPickerDialog crashes
- Check if `newInstance()` is being called correctly
- Check if ViewModel is properly initialized
- Check if followed streams API call succeeds

### Issue: Secondary stream doesn't play
- Check if `loadSecondaryStream()` is being called
- Check if URL is valid
- Check if secondaryPlayer is created
- Check ExoPlayerService logs for errors

### Issue: Audio switching doesn't work
- Check if `setAudioSource()` is being called
- Check if callbacks are properly set up
- Check if audio indicators update

### Issue: Layout animations don't work
- Check if TransitionManager is enabled
- Check if constraints are correct
- Check if MultiStreamLayoutManager is initialized

## Next Steps After Integration

1. **Add layout mode switching button** to player controls
2. **Add gesture for cycling layout modes** (e.g., double-tap)
3. **Add preferences** for default layout mode
4. **Add preferences** for PiP positioning (corner, size)
5. **Add support for more than 2 streams** (future enhancement)
6. **Add stream preview thumbnails** in StreamPickerDialog
7. **Add "swap streams" feature** to exchange primary/secondary

## References

- **Service Code**: `ExoPlayerService.kt` (dual player slots, audio switching)
- **Controller**: `MultiStreamController.kt` (manages multi-stream logic)
- **Layout Manager**: `MultiStreamLayoutManager.kt` (layout transitions)
- **UI Component**: `StreamSurfaceView.kt` (reusable video surface)
- **Dialog**: `StreamPickerDialog.kt` (stream selection)
- **ViewModel**: `StreamPickerViewModel.kt` (followed streams data)
- **Layout XML**: `fragment_player.xml` (player layout with containers)
- **Player Fragment**: `PlayerFragment.kt` (abstract base)
- **ExoPlayer Fragment**: `ExoPlayerFragment.kt` (concrete implementation)

## Questions?

If you encounter issues:
1. Check the existing code patterns in PlayerFragment and ExoPlayerFragment
2. Check the service binding pattern
3. Check how dialogs are invoked in the codebase
4. Look at the architecture document: `docs/MULTI_STREAM_ARCHITECTURE.md`
5. Check the commit message for the multi-stream feature: `c68e6c8d`
