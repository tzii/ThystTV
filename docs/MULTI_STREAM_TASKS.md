# Multi-Stream Feature - Task Checklist

This checklist tracks all tasks for the multi-stream viewing feature implementation.

## Status Overview
- **Completed**: 17 tasks (All phases complete! ✅)
- **In Progress**: 0 tasks
- **Pending**: 0 tasks
- **Total**: 17 tasks

**Feature is now 100% integrated!** Build and test to verify.

## Completed Tasks ✅

### Phase 1: Service Refactoring
- [x] Refactor ExoPlayerService to support multiple player slots
  - [x] Add PlayerSlot enum (PRIMARY, SECONDARY)
  - [x] Create secondaryPlayer field
  - [x] Implement createSecondaryPlayer()
  - [x] Implement releaseSecondaryPlayer()
  - [x] Implement setAudioSource(slot)
  - [x] Implement toggleAudioSource()
  - [x] Implement updateMetadataForActiveSlot()
- [x] Add multi-stream constants to C.kt

### Phase 2: StreamSurfaceView Component
- [x] Create StreamSurfaceView.kt
  - [x] Add AspectRatioFrameLayout wrapper
  - [x] Add SurfaceView for video rendering
  - [x] Add buffering indicator (CircularProgressIndicator)
  - [x] Add audio indicator (speaker icon)
  - [x] Add stream info overlay (channel name, title)
  - [x] Add touch overlay for gestures
- [x] Create view_stream_surface.xml layout
- [x] Add audio indicator overlay styling
- [x] Create bg_audio_indicator.xml drawable
- [x] Create bg_stream_info_overlay.xml drawable

### Phase 3: Layout System
- [x] Update fragment_player.xml with secondary container
  - [x] Add multiStreamContainer (ConstraintLayout)
  - [x] Add secondaryPlayerContainer (AspectRatioFrameLayout)
  - [x] Add secondary audio indicator
  - [x] Add secondary stream info overlay
  - [x] Add secondary close button (PiP mode)
  - [x] Add primary audio indicator
- [x] Create MultiStreamLayoutManager.kt
  - [x] Define LayoutMode enum (SINGLE, SPLIT_HORIZONTAL, SPLIT_VERTICAL, PIP_OVERLAY)
  - [x] Implement applyLayoutMode() with ConstraintSet
  - [x] Add animated transitions with TransitionManager
  - [x] Implement PiP positioning (4 corners)
  - [x] Implement PiP size configuration
  - [x] Implement stream swap functionality

### Phase 4: MultiStreamController
- [x] Create MultiStreamController.kt
- [x] Implement addSecondaryStream()
- [x] Implement removeSecondaryStream()
- [x] Implement switchAudioTo()
- [x] Implement toggleAudio()
- [x] Implement cycleLayoutMode()
- [x] Implement bindService(service)
- [x] Implement unbindService()
- [x] Add onAudioSwitched callback
- [x] Add onSecondaryStreamClosed callback
- [x] Add onLayoutModeChanged callback

### Phase 5: Stream Picker UI
- [x] Create StreamPickerDialog.kt
  - [x] BottomSheetDialogFragment implementation
  - [x] Add RecyclerView for stream list
  - [x] Implement search functionality
  - [x] Implement StreamPickerAdapter with DiffUtil
  - [x] Exclude current channel from selection
- [x] Create StreamPickerViewModel.kt
  - [x] Load followed streams from GraphQL/Helix APIs
  - [x] Implement search filtering
  - [x] Handle loading/error states
- [x] Create dialog_stream_picker.xml layout
- [x] Create item_stream_picker.xml layout
- [x] Create ic_multi_stream.xml icon
- [x] Add Multi-Stream button to player_layout.xml

### Phase 6: Audio Switching
- [x] Add tap-to-switch-audio gesture on stream surfaces
- [x] Add visual audio indicator (speaker icon)
- [x] Implement audio indicator state updates

## Pending Tasks ⏳

### Phase 7: PlayerFragment Integration (HIGH PRIORITY) ✅ COMPLETED
- [x] Add multi-stream state management to PlayerFragment
  - [x] Add multiStreamController field
  - [x] Add multiStreamBinding field
  - [x] Update onCreateView() to bind multi-stream views
  - [x] Add onDestroyView() cleanup
  - [x] Add abstract onServiceConnected() method
  - [x] Add abstract onServiceDisconnected() method

### Phase 8: ExoPlayerFragment Integration (HIGH PRIORITY) ✅ COMPLETED
- [x] Initialize MultiStreamController in ExoPlayerFragment
  - [x] Initialize multiStreamBinding in onViewCreated()
  - [x] Initialize multiStreamController in onServiceConnected()
  - [x] Bind service to multiStreamController
  - [x] Set up audio switching callback
  - [x] Set up secondary stream closed callback
  - [x] Set up layout mode changed callback
- [x] Handle multi-stream button click
  - [x] Add click listener to multi-stream button
  - [x] Implement showStreamPicker() method
  - [x] Invoke StreamPickerDialog with current channel ID
- [x] Implement secondary stream loading
  - [x] Implement loadSecondaryStream() method
  - [x] Get stream URL from ViewModel/API
  - [x] Call service.loadSecondaryStream()
  - [x] Update UI (show primary audio indicator)
- [x] Implement audio indicator updates
  - [x] Implement updateAudioIndicators() method
  - [x] Update primary audio indicator visibility/alpha
  - [x] Update secondary audio indicator visibility/alpha
- [x] Handle fragment lifecycle
  - [x] Pause secondary stream in onStop()
  - [x] Resume secondary stream in onStart()
  - [x] Unbind service in onServiceDisconnected()

### Phase 9: ExoPlayerService Integration (HIGH PRIORITY) ✅ COMPLETED
- [x] Add loadSecondaryStream() method (via MultiStreamController.addSecondaryStream)
  - [x] Create MediaItem from URL
  - [x] Set media item on secondaryPlayer
  - [x] Prepare and play secondary stream
- [x] Add pauseSecondaryStream() method
- [x] Add resumeSecondaryStream() method

### Phase 10: PiP Mode Handling (MEDIUM PRIORITY) ✅ COMPLETED
- [x] Handle PiP mode entry/exit
  - [x] Override onPictureInPictureModeChanged()
  - [x] Save current multi-stream state on PiP entry
  - [x] Switch to SINGLE layout in PiP
  - [x] Restore state on PiP exit
- [x] Hide multi-stream UI in PiP mode
- [x] Disable multi-stream features in PiP mode

### Phase 11: Device Rotation Handling (MEDIUM PRIORITY) ✅ COMPLETED
- [x] Save multi-stream state in onSaveInstanceState()
  - [x] Save hasSecondaryStream flag
  - [x] Save secondaryChannelId
  - [x] Save current layoutMode
- [x] Restore multi-stream state in onViewCreated()
  - [x] Restore secondary stream if it was active
  - [x] Restore layout mode
  - [x] Re-bind surfaces after rotation

## Future Enhancements 🚀

### Phase 12: Advanced Features (LOW PRIORITY)
- [ ] Add layout mode switching button to player controls
- [ ] Add gesture for cycling layout modes (double-tap/swipe)
- [ ] Add preferences for default layout mode
- [ ] Add preferences for PiP positioning (corner selection)
- [ ] Add preferences for PiP size (small/medium/large)
- [ ] Add support for more than 2 streams (quad view)
- [ ] Add stream preview thumbnails in StreamPickerDialog
- [ ] Add "swap streams" feature to exchange primary/secondary
- [ ] Add stream audio level visualization
- [ ] Add stream-specific volume controls

## Testing Checklist

After completing all integration tasks, test:

### Basic Functionality
- [ ] Multi-stream button appears in player controls
- [ ] Tapping button opens StreamPickerDialog
- [ ] StreamPickerDialog shows followed live streams
- [ ] StreamPickerDialog excludes current channel
- [ ] Search functionality works in StreamPickerDialog
- [ ] Selecting a stream adds secondary stream
- [ ] Secondary stream plays muted

### Layout & Transitions
- [ ] Default layout is SPLIT_HORIZONTAL
- [ ] Layout transition animation is smooth (300ms)
- [ ] Layout modes cycle correctly
- [ ] PiP overlay appears correctly
- [ ] PiP overlay is in correct corner
- [ ] Close button appears in PiP mode

### Audio Switching
- [ ] Primary stream has audio by default
- [ ] Tapping primary stream keeps audio on primary
- [ ] Tapping secondary stream switches audio to secondary
- [ ] Tapping secondary stream again switches audio back to primary
- [ ] Audio indicators update immediately
- [ ] Audio indicators show correct opacity (active vs inactive)

### Lifecycle & State
- [ ] Secondary stream pauses when player minimized
- [ ] Secondary stream resumes when player maximized
- [ ] Secondary stream pauses when backing out
- [ ] Secondary stream is cleaned up when player closes
- [ ] No memory leaks when closing player
- [ ] No crashes on repeated open/close

### PiP Mode
- [ ] Entering PiP hides secondary stream
- [ ] Entering PiP switches to SINGLE layout
- [ ] Exiting PiP restores previous layout (if applicable)
- [ ] Multi-stream features are disabled in PiP
- [ ] No crashes when entering/exiting PiP

### Device Rotation
- [ ] Multi-stream state is saved on rotation
- [ ] Secondary stream continues playing after rotation
- [ ] Layout mode is preserved after rotation
- [ ] Audio indicator state is preserved after rotation
- [ ] No crashes on rotation

### Edge Cases
- [ ] Selecting same stream as current (should be excluded)
- [ ] Network error when loading secondary stream
- [ ] Stream goes offline while viewing
- [ ] Selecting stream when no followed channels are live
- [ ] Rapidly opening/closing StreamPickerDialog
- [ ] Rapidly switching audio between streams
- [ ] Rapidly cycling layout modes

## Notes

- **Estimated Time for Integration**: ~2-3 hours
- **Complexity**: Medium (mostly wiring up existing components)
- **Dependencies**: None (all infrastructure is complete)
- **Risks**: Low (existing patterns to follow)

## References

- **Integration Guide**: `docs/MULTI_STREAM_INTEGRATION.md`
- **Architecture Document**: `docs/MULTI_STREAM_ARCHITECTURE.md`
- **Commit**: `c68e6c8d` - "Implement multi-stream viewing architecture (Phase 1-5 complete)"
- **Branch**: `feature/multi-stream`

## How to Use This Checklist

1. **Copy this file**: `docs/MULTI_STREAM_TASKS.md`
2. **Mark tasks as complete**: Change `- [ ]` to `- [x]` as you finish each task
3. **Test after each phase**: Run through the testing checklist after completing a phase
4. **Update status**: Update the status overview at the top as you progress
5. **Document issues**: Add notes if you encounter problems or deviations from the plan

## Progress Tracking

```
Phase 1:  Service Refactoring      ████████████████████ 100% ✅
Phase 2:  StreamSurfaceView       ████████████████████ 100% ✅
Phase 3:  Layout System           ████████████████████ 100% ✅
Phase 4:  MultiStreamController    ████████████████████ 100% ✅
Phase 5:  Stream Picker UI         ████████████████████ 100% ✅
Phase 6:  Audio Switching          ████████████████████ 100% ✅
Phase 7:  PlayerFragment           ████████████████████ 100% ✅
Phase 8:  ExoPlayerFragment        ████████████████████ 100% ✅
Phase 9:  ExoPlayerService         ████████████████████ 100% ✅
Phase 10: PiP Mode                ████████████████████ 100% ✅
Phase 11: Device Rotation         ████████████████████ 100% ✅
Phase 12: Advanced Features        ░░░░░░░░░░░░░░░░░░░░   0% 🚀

Overall Progress:  ███████████████████░  95%
```
