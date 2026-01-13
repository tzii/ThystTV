# Multi-Stream Feature - FAQ

## About Private Branches

### Can I make a branch private?

**No, branches cannot be private individually.** Branches inherit the visibility of their repository.

**Current Status**:
- Your repository `tzii/ThystTV` is **PUBLIC**
- Therefore, `feature/multi-stream` branch is also **PUBLIC**
- Anyone with access to the repository can see all branches

### Options for Private Development

If you need to keep development work private, you have these options:

#### Option 1: Make the Repository Private (Recommended for Forks)

**Best if**: You want complete privacy and control

**Steps**:
1. Create a **private fork** of `crackededed/Xtra` to your personal account
2. Push your `feature/multi-stream` branch to your private fork
3. Work there - nobody can see it

**Commands**:
```bash
# Create a private fork on GitHub.com (private checkbox)
# Then add it as a remote
git remote add my-private-fork https://github.com/YOUR_USERNAME/Xtra-private.git
git push -u my-private-fork feature/multi-stream
```

**Pros**:
- Complete privacy
- Full control
- Can still merge to upstream later

**Cons**:
- Need to manage multiple remotes
- Can't open PR from private repo to public repo directly

#### Option 2: Use a Private Repository

**Best if**: You want to keep existing `tzii/ThystTV` public

**Steps**:
1. Create a new **private repository** (e.g., `Xtra-multi-stream-dev`)
2. Push your branch there
3. Work in isolation
4. When ready, push to `tzii/ThystTV` and create PR

**Commands**:
```bash
# Create private repo on GitHub.com
git remote add private-dev https://github.com/YOUR_USERNAME/Xtra-multi-stream-dev.git
git push -u private-dev feature/multi-stream
```

**Pros**:
- Keeps `tzii/ThystTV` public
- Clean separation of dev work

**Cons**:
- Need to sync changes later
- Extra step when ready to merge

#### Option 3: Use Draft Pull Requests

**Best if**: You want privacy during development but transparency in review

**Steps**:
1. Create a **draft PR** from `feature/multi-stream`
2. Work in the branch, push commits
3. Draft PR is visible but marked as "Work in Progress"
4. Convert to ready for review when done

**Commands**:
```bash
# Create draft PR via GitHub CLI
gh pr create --title "Multi-Stream Feature (WIP)" --body "Work in progress" --draft
```

**Pros**:
- Visible but clearly marked as WIP
- Easier to convert to real PR later
- GitHub tracks changes

**Cons**:
- Still visible to everyone with repo access
- Not truly private

#### Option 4: Keep Current Setup

**Best if**: You don't mind public visibility

**Rationale**:
- Your branch is already public
- Work is well-documented (architecture, integration guide)
- Nothing sensitive in the code
- Open source community can contribute

**Pros**:
- Simplest approach
- Potential community contributions
- No extra setup

**Cons**:
- Everyone can see work-in-progress
- No privacy

### Recommendation

**For this project**: Use **Option 3 (Draft PR)**

**Why**:
1. Code is already public in `tzii/ThystTV`
2. Feature is documented and structured
3. Draft PR signals "work in progress"
4. Easy to convert to regular PR when integration is done
5. Allows for community feedback during development

**How**:
```bash
gh pr create \
  --title "Multi-Stream Feature: Dual Twitch Stream Viewing" \
  --body "See docs/MULTI_STREAM_INTEGRATION.md for implementation status" \
  --draft
```

---

## Implementation Questions

### Why is the feature 70-80% complete?

The **infrastructure is 100% complete** (service layer, UI components, layouts, controller, dialogs). What's missing is **integration** - wiring everything together in the existing PlayerFragment and ExoPlayerFragment.

Think of it like building furniture:
- ✅ All pieces cut and prepared (Phase 1-5)
- ✅ Instructions written (documentation)
- ⏳ Pieces need to be assembled (integration)
- ⏳ Furniture needs testing (final verification)

### How long will integration take?

**Estimated**: 2-3 hours for a developer familiar with the codebase

**Breakdown**:
- PlayerFragment integration: 30 min
- ExoPlayerFragment integration: 45 min
- ExoPlayerService updates: 15 min
- PiP mode handling: 30 min
- Device rotation: 30 min
- Testing & debugging: 30-60 min

### Can I test the feature before completing integration?

**No**. The feature won't work until integration is complete. The UI components exist but aren't connected to the player.

**To see progress during integration**:
1. Complete PlayerFragment integration first
2. Test basic multi-stream button functionality
3. Then complete ExoPlayerFragment integration
4. Test full feature

### What if I encounter issues during integration?

**Follow this process**:

1. **Check the pattern**: Look at how similar features are implemented in the codebase
2. **Read the error**: Check logs in Android Studio Logcat
3. **Consult documentation**: Review `docs/MULTI_STREAM_INTEGRATION.md`
4. **Check architecture**: Review `docs/MULTI_STREAM_ARCHITECTURE.md`
5. **Simplify**: Comment out complex parts, get basic flow working first
6. **Gradually re-enable**: Add complexity back piece by piece

**If stuck after 3 failed attempts**:
1. Revert to last working state: `git checkout .`
2. Document what you tried
3. Ask for help or consult the architecture

### Can I skip some phases?

**No, do not skip**. Each phase builds on the previous one.

**Order matters**:
1. PlayerFragment → provides base structure
2. ExoPlayerFragment → provides actual implementation
3. ExoPlayerService → provides stream loading
4. PiP Mode → handles edge case
5. Device Rotation → preserves state

Skipping will cause crashes or missing functionality.

### Do I need to implement all "Future Enhancements"?

**No**. Those are optional improvements.

**Must implement**:
- Phase 7-11 (integration, PiP, rotation)

**Nice to have** (later):
- Layout mode button
- Gestures for layout cycling
- Preferences
- More than 2 streams

---

## Technical Questions

### Why use two players in one service instead of separate services?

**Design choice for simplicity and resource efficiency**.

**Pros**:
- Single ServiceConnection (simpler lifecycle)
- Shared resources (audio focus, MediaSession)
- Easier to manage audio switching
- Less memory overhead

**Cons**:
- Slightly more complex service code

**Alternative considered**: Separate ExoPlayerService instances for each stream
- Too complex
- Audio management becomes difficult
- MediaSession conflicts

### Why is the secondary player muted by default?

**Performance and user experience**.

**Reasons**:
1. **Bandwidth**: Two audio streams would use more bandwidth
2. **CPU**: Decoding two audio streams is unnecessary work
3. **Confusion**: Hearing two streamers talking is confusing
4. **Pattern**: SmartTwitchTV and other apps do the same

### Why use ConstraintSet for layout transitions?

**Smooth, performance-optimized animations**.

**Pros**:
- Native Android animation support
- Hardware-accelerated
- Easy to define different layouts
- Smooth 300ms transitions
- Handles view hierarchy automatically

**Alternatives considered**:
- Manual animation: Too much code, error-prone
- Transition API: Less control over specific constraints
- Different layouts per XML: Too many files, hard to maintain

### Why is StreamSurfaceView a custom View?

**Reusability and encapsulation**.

**Benefits**:
- Single component for both primary and secondary streams
- Hides complexity (buffering, audio indicators, touch listeners)
- Easy to reuse in other parts of app
- Consistent appearance

**Alternative**: Inline XML in fragment_player.xml
- Duplicated code
- Hard to maintain
- Inconsistent styling

---

## Git & Workflow Questions

### How do I update the task checklist?

**Edit the markdown file directly**:

```bash
# Edit the checklist
code docs/MULTI_STREAM_TASKS.md

# Or use any editor
nano docs/MULTI_STREAM_TASKS.md
vim docs/MULTI_STREAM_TASKS.md
```

**Mark tasks complete**: Change `- [ ]` to `- [x]`

**Update progress bar**: Adjust the progress visualization at the bottom

### How do I create a commit for integration work?

**Follow atomic commit practice**:

```bash
# 1. Check what changed
git status

# 2. Stage files for a specific task
git add app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt

# 3. Commit with descriptive message
git commit -m "Integrate multi-stream controller into PlayerFragment

- Add multiStreamController and multiStreamBinding fields
- Initialize multi-stream views in onCreateView()
- Add abstract onServiceConnected() method
- Add cleanup in onDestroyView()
"

# 4. Repeat for each major change
```

**Good commit messages**:
- Describe WHAT you did
- Describe WHY you did it
- One logical change per commit

### How do I create a pull request when integration is complete?

**Option 1: GitHub CLI (easiest)**

```bash
gh pr create \
  --title "Multi-Stream Feature: Complete Integration" \
  --body "Implements dual-stream viewing capability.

## What's Implemented
- Dual player support in ExoPlayerService
- Four layout modes: Single, Split Horizontal, Split Vertical, PiP
- Audio switching between streams
- Stream picker for followed channels
- Device rotation support
- PiP mode handling

## Testing
All features tested on device. See testing checklist in docs/MULTI_STREAM_TASKS.md

## Documentation
- Integration guide: docs/MULTI_STREAM_INTEGRATION.md
- Architecture: docs/MULTI_STREAM_ARCHITECTURE.md
- Task list: docs/MULTI_STREAM_TASKS.md" \
  --base master
```

**Option 2: GitHub Web UI**

1. Go to: https://github.com/tzii/ThystTV/compare/master...feature/multi-stream
2. Review changes
3. Click "Create Pull Request"
4. Fill in title and description
5. Click "Create Pull Request"

### What if I need to update the PR after creating it?

**Just push more commits to the branch**:

```bash
# Make changes
git add .
git commit -m "Fix audio indicator not updating correctly"
git push origin feature/multi-stream

# PR automatically updates with new commits
```

---

## Troubleshooting

### "找不到 binding.multiStreamButton" (Binding not found)

**Cause**: Button ID in XML doesn't match code

**Solution**:
1. Check `player_layout.xml` for the button ID
2. Use correct ID: `R.id.multiStreamButton` or `R.id.multi_stream_button`
3. Or find the button dynamically:
```kotlin
val multiStreamButton = binding.playerControls.findViewById<ImageView>(R.id.multiStreamButton)
```

### "StreamPickerDialog crashes on show"

**Cause**: ViewModel not initialized or null data

**Solution**:
1. Check if ViewModel is initialized: `viewModel.followedStreams.collectLatest`
2. Check if excludeChannelId is null
3. Add null checks in adapter:
```kotlin
if (channelId != excludeChannelId) {
    // Show in list
}
```

### "Secondary stream doesn't play"

**Cause**: URL is null or secondaryPlayer is null

**Solution**:
1. Log the URL: `Log.d(TAG, "Stream URL: $url")`
2. Check if secondaryPlayer is created: `service.secondaryPlayer != null`
3. Check if loadSecondaryStream is called
4. Check ExoPlayer logs for errors

### "Audio doesn't switch when tapping"

**Cause**: Callback not set up or listener is null

**Solution**:
1. Check if callback is set in multiStreamController:
```kotlin
setOnAudioSwitchedListener { slot ->
    updateAudioIndicators(slot)
}
```
2. Check if onTapListener is set on StreamSurfaceView
3. Add logging to verify callback fires:
```kotlin
setOnAudioSwitchedListener { slot ->
    Log.d(TAG, "Audio switched to $slot")
    updateAudioIndicators(slot)
}
```

### "Layout doesn't animate on mode change"

**Cause**: TransitionManager not enabled or constraints not applied

**Solution**:
1. Check if TransitionManager.beginDelayedTransition() is called
2. Check if constraintSet.applyTo() is called
3. Check if transition duration is set (300ms)
4. Check if device supports animations (developer options)

### "Crash on device rotation"

**Cause**: State not saved or restored correctly

**Solution**:
1. Check if onSaveInstanceState is called
2. Check if state is restored in onViewCreated
3. Check if multiStreamController is null after rotation
4. Add null checks:
```kotlin
multiStreamController?.let { controller ->
    controller.restoreState(savedInstanceState)
}
```

---

## Getting Help

### Documentation Files

1. **Integration Guide**: `docs/MULTI_STREAM_INTEGRATION.md`
   - Step-by-step integration instructions
   - Code examples
   - Testing checklist

2. **Architecture Document**: `docs/MULTI_STREAM_ARCHITECTURE.md`
   - System design overview
   - Component responsibilities
   - Data flow diagrams

3. **Task Checklist**: `docs/MULTI_STREAM_TASKS.md`
   - Complete task list
   - Progress tracking
   - Testing checklist

### Code References

- **Service**: `ExoPlayerService.kt` (lines 1-200 for multi-stream additions)
- **Controller**: `MultiStreamController.kt` (complete implementation)
- **Layout**: `MultiStreamLayoutManager.kt` (complete implementation)
- **UI**: `StreamSurfaceView.kt` (complete implementation)
- **Dialog**: `StreamPickerDialog.kt` (complete implementation)
- **Fragment**: `PlayerFragment.kt` (base class to extend)
- **Implementation**: `ExoPlayerFragment.kt` (concrete implementation)

### Commit History

```bash
# View multi-stream commits
git log --oneline --grep="multi-stream" --all

# View specific commit
git show c68e6c8d

# View diff from master
git diff master...feature/multi-stream
```

---

## Glossary

- **PiP**: Picture-in-Picture mode (small video overlay)
- **Slot**: Player slot (PRIMARY or SECONDARY)
- **Layout Mode**: One of SINGLE, SPLIT_HORIZONTAL, SPLIT_VERTICAL, PIP_OVERLAY
- **ConstraintSet**: Android API for defining layout constraints programmatically
- **TransitionManager**: Android API for animated layout changes
- **ServiceConnection**: Android binding mechanism for connecting to services
- **BottomSheetDialog**: Material Design dialog that slides up from bottom
- **MediaItem**: ExoPlayer representation of media to play
- **MediaSession**: Android system representation of media player (for lock screen, etc.)
- **ServiceBinder**: Interface for bound services (ExoPlayerService)

---

**Last Updated**: January 13, 2026
**Feature Status**: Phase 1-6 Complete, Phase 7-11 Pending
**Integration Progress**: 0% (ready to start)
