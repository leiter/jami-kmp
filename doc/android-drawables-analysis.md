# Android Drawables Analysis

Analysis of XML drawables in `android-app/src/androidMain/res/drawable/` for potential migration to shared Compose Multiplatform resources (`composeResources/drawable/`).

**Date:** 2026-03-28

---

## Summary

| Category | Count | Compose Migration |
|----------|-------|-------------------|
| Vector | 6 | Direct copy to composeResources |
| Layer-list (icon wrappers) | 8 | Unwrap or skip if base vector exists |
| Layer-list (message bubbles) | 4 | Compose Shape/Canvas |
| Selector | 10 | Compose state logic (if/when) |
| Shape | 52 | Compose Modifier/Shape |
| Ripple | 8 | Built-in Compose Indication |
| Animated-vector | 1 | Compose animation APIs |
| Rotate | 1 | Compose InfiniteTransition |
| Broken (https prefix) | 3 | Fix or skip |
| **Total** | **93** | |

---

## 1. Vectors (6 files) -- Direct copy to composeResources

Pure `<vector>` root element. Can be moved as-is.

| File | Description |
|------|-------------|
| `baseline_edit_twoton_24dp.xml` | Two-tone edit icon |
| `baseline_flip_camera_24_off.xml` | Camera flip off icon |
| `baseline_share_twoton_24dp.xml` | Two-tone share icon |
| `baseline_videocam_24_primary.xml` | Videocam (primary tint) |
| `ic_launcher_foreground.xml` | App launcher foreground |
| `new_invitation.xml` | New invitation icon |

---

## 2. Layer-lists wrapping vectors (8 files) -- Unwrap or skip

These scale or compose existing vector icons. If the base vector already exists in composeResources, these can be skipped (use Compose `Modifier.size()` instead).

| File | Description |
|------|-------------|
| `baseline_content_copy_16.xml` | Copy icon 16dp |
| `baseline_delete_16.xml` | Delete icon 16dp |
| `baseline_edit_rounded_24.xml` | Edit rounded icon |
| `baseline_file_download_16.xml` | Download icon 16dp |
| `baseline_open_in_new_16.xml` | Open-in-new icon 16dp |
| `baseline_share_twoton_16dp.xml` | Share icon 16dp |
| `ic_jami_24.xml` | Jami logo 24dp |
| `ic_jami_48.xml` | Jami logo 48dp |

---

## 3. Layer-lists for message bubbles (4 files)

Composite drawables combining shapes for reply message backgrounds.

| File | Description |
|------|-------------|
| `textmsg_bg_in_reply.xml` | Incoming reply bubble |
| `textmsg_bg_in_reply_first.xml` | Incoming reply bubble (first) |
| `textmsg_bg_out_reply.xml` | Outgoing reply bubble |
| `textmsg_bg_out_reply_first.xml` | Outgoing reply bubble (first) |

**Compose approach:** `RoundedCornerShape` + `Modifier.background()` + `Column` layering.

---

## 4. Selectors (10 files) -- Compose state logic

State-based drawables (`android:state_checked`, `android:state_pressed`, `android:state_selected`). In Compose, state is handled via `if`/`when` on boolean state values.

| File | Description |
|------|-------------|
| `tab_selector.xml` | Tab selected/unselected colors |
| `baseline_mic_24.xml` | Mic on/off by checked state |
| `baseline_sound_24.xml` | Sound on/off by checked state |
| `baseline_videocam_24.xml` | Video on/off by checked state |
| `call_button_background_checkable.xml` | Call button checked state |
| `call_button_background_pressable.xml` | Call button pressed state |
| `call_hangup_btn_background.xml` | Hangup button state |
| `textmsg_bg_input.xml` | Text input focused/unfocused |
| `loccationshare_bg_gradient.xml` | Location share gradient |
| `tv_header_bg.xml` | TV header background |

**Compose approach:** Already handled in screen files via state-driven icon/color switching (e.g., `CallScreen.kt` uses `if (state.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic`).

---

## 5. Shapes (52 files) -- Compose Modifier/Shape

Simple geometric shapes (rectangles, ovals, rings) with solid fills, gradients, strokes, and corners. In Compose these become `Modifier.background(color, shape)` or `Modifier.border()`.

### Message bubble backgrounds (14 files)

| File | Description |
|------|-------------|
| `textmsg_bg_in.xml` | Incoming message (single) |
| `textmsg_bg_in_first.xml` | Incoming message (first in group) |
| `textmsg_bg_in_last.xml` | Incoming message (last in group) |
| `textmsg_bg_in_middle.xml` | Incoming message (middle) |
| `textmsg_bg_out.xml` | Outgoing message (single) |
| `textmsg_bg_out_first.xml` | Outgoing message (first in group) |
| `textmsg_bg_out_last.xml` | Outgoing message (last in group) |
| `textmsg_bg_out_middle.xml` | Outgoing message (middle) |
| `textmsg_bg_preview.xml` | Message preview |
| `textmsg_call_background.xml` | Call history message |
| `filemsg_background_in.xml` | Incoming file message |
| `filemsg_background_out.xml` | Outgoing file message |
| `linkpreview_bg_out_first_or_middle.xml` | Link preview (first/middle) |
| `linkpreview_bg_out_last_or_single.xml` | Link preview (last/single) |

### Call-related backgrounds (10 files)

| File | Description |
|------|-------------|
| `background_call_banner.xml` | Call notification banner |
| `background_call_menu.xml` | Call menu overlay |
| `call_bg_missed.xml` | Missed call background |
| `call_bg_missed_in_first.xml` | Missed call incoming (first) |
| `call_bg_missed_in_last.xml` | Missed call incoming (last) |
| `call_bg_missed_in_middle.xml` | Missed call incoming (middle) |
| `call_bg_missed_out_first.xml` | Missed call outgoing (first) |
| `call_bg_missed_out_last.xml` | Missed call outgoing (last) |
| `call_bg_missed_out_middle.xml` | Missed call outgoing (middle) |
| `background_conference_participant.xml` | Conference participant tile |

### UI element backgrounds (18 files)

| File | Description |
|------|-------------|
| `background_amber_dot_6dp.xml` | Small amber dot indicator |
| `background_appbar_gradient.xml` | App bar gradient |
| `background_conference_hand.xml` | Raised hand indicator |
| `background_conference_label.xml` | Conference label badge |
| `background_item_conv_image_time.xml` | Image message timestamp |
| `background_item_conv_image.xml` | Image message container |
| `background_jami_edittext.xml` | Styled EditText border |
| `background_jami_id_left.xml` | Jami ID left segment |
| `background_jami_id_middle.xml` | Jami ID middle segment |
| `background_jami_id_right.xml` | Jami ID right segment |
| `background_qrcode.xml` | QR code container |
| `background_reaction_chip.xml` | Reaction chip pill |
| `background_rounded_12.xml` | Rounded 12dp rectangle |
| `background_tab_indicator.xml` | Tab indicator bar |
| `background_unread_counter.xml` | Unread count badge |
| `background_welcome_jami_main_box.xml` | Welcome screen container |
| `custom_popup_background.xml` | Popup/dialog background |
| `reply_contact_msg_bg.xml` | Reply quote background |

### Misc shapes (10 files)

| File | Description |
|------|-------------|
| `drawer_handlebar.xml` | Bottom sheet handle |
| `file_icon_background.xml` | File type icon circle |
| `ic_online_indicator.xml` | Online presence dot |
| `ic_tv_online_indicator.xml` | TV online presence dot |
| `item_color_background.xml` | Generic item background |
| `layout_divider.xml` | List divider line |
| `rounded_background.xml` | Rounded background |
| `static_rounded_background.xml` | Static rounded background |
| `tv_item_selected_background.xml` | TV item selected state |
| `tv_item_unselected_background.xml` | TV item unselected state |

---

## 6. Ripples (8 files) -- Built-in Compose Indication

Android `<ripple>` drawables. Compose provides `Indication` and `clickable(indication = ...)` natively.

| File | Description |
|------|-------------|
| `background_clickable.xml` | Generic clickable ripple |
| `background_item_smartlist.xml` | Conversation list item ripple |
| `background_pin_code.xml` | PIN entry ripple |
| `background_rounded_16.xml` | Rounded 16dp ripple |
| `background_rounded_24.xml` | Rounded 24dp ripple |
| `baseline_ripple_effect.xml` | Generic ripple effect |
| `tv_button_shape.xml` | TV button ripple |
| `tv_group_call_button_shape.xml` | TV group call button ripple |

---

## 7. Other (5 files)

| File | Root Element | Description |
|------|-------------|-------------|
| `typing_indicator_animation.xml` | `animated-vector` | Typing dots animation |
| `rotate.xml` | `layer-list` (with rotate) | Loading spinner |
| `background_status_optional.xml` | Broken (https comment) | Status indicator |
| `background_status_recommended.xml` | Broken (https comment) | Status indicator |
| `background_status_required.xml` | Broken (https comment) | Status indicator |

---

## Migration Priority

### Phase 1: Trivial (6 files)
Move the 6 pure `<vector>` files to `composeResources/drawable/`. No changes needed.

### Phase 2: Skip or defer (8 layer-list icon wrappers)
Most base icons already exist in composeResources at 24dp. The 16dp variants can be achieved with `Modifier.size(16.dp)` in Compose. The `ic_jami_24.xml` and `ic_jami_48.xml` logos already have `ic_jami.xml` and `ic_jami_logo.xml` in composeResources.

### Phase 3: Selectors already handled (10 files)
The state-switching logic is already implemented in Compose screens via conditional expressions. No migration needed.

### Phase 4: Shapes when needed (52 files)
Convert shapes to Compose `Modifier.background()`/`Modifier.border()` as screens are built. Message bubble shapes are the highest priority (already partially implemented in `ChatScreen.kt`).

### Phase 5: Ripples/animations (9 files)
Use Compose built-in `ripple()` indication and animation APIs. Low priority.
