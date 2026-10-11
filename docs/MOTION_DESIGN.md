# Design language and motion

The UI follows a short set of rules taken from Apple's fluid-interface guidance and translated to Jetpack Compose: respond on touch-down, track the finger 1:1, hand the release velocity to a spring, never block input during motion, and keep every animation interruptible. The warm palette and the privacy-first behaviour are unchanged. Movement explains a change of state; it never delays an action or changes what a financial figure says.

Code: `core/ui/Motion.kt` (springs, press feedback, chart helpers), `core/ui/Gestures.kt` (swipe actions and the gesture maths), `core/ui/TabBar.kt` (floating tab bar), `core/theme/Type.kt` and `Theme.kt`.

## Springs, not durations

Anything a person can touch moves on a spring, because a spring keeps going from where it is when the target changes and accepts a starting velocity. Fixed-duration easing is only used for opacity and colour.

| Token | Spring | Used for |
| --- | --- | --- |
| `Motion.move()` | damping 1.0, stiffness 250 (Apple's "move") | repositioning, expanding, content size |
| `Motion.medium()` | damping 1.0, stiffness 300 | list rows moving, banners opening, enter/exit size |
| `Motion.slow()` | damping 1.0, stiffness 200 | screen-level slides, chart geometry |
| `Motion.sheet()` | damping 0.8, stiffness 440 (Apple's "drawer") | release of a swipe, the tab indicator: **only where a gesture carried momentum** |
| `Motion.press()` | damping 1.0, stiffness 700 | press feedback |
| `Motion.short()` / `exit()` | 150 ms easing | fades and colour changes |

Overshoot is reserved for momentum. Something that merely appears never bounces.

## Response and gestures

- **Press feedback starts on touch-down.** `pressClickable` scales content to 97 % the moment the finger lands and springs back (interruptibly) on release or cancel. It is used on dashboard actions, transaction and stock rows, banners, More and Settings rows, and the tab bar. The click is never delayed.
- **Swipe actions** (`SwipeActionBox`, used in the review queue): swipe right to confirm, left to discard.
  - The row follows the finger 1:1 from the touch slop, keeping the offset at which it was grabbed.
  - A direction with no action (for example Confirm while no account is chosen) resists like a rubber band instead of stopping.
  - A light tick marks the point at which releasing will commit.
  - On release the finger's velocity is handed to the spring, and the outcome comes from the momentum-projected position (`GesturePhysics.project`, the exponential-decay form with deceleration 0.998): a quick flick commits from a short distance, a slow drag that stops short settles back.
  - It is interruptible: touching down mid-flight stops the row where it is and the drag carries on.
  - The same actions are exposed to screen readers as custom accessibility actions, and the Confirm and Discard buttons remain, so no function depends on the gesture.
- **Undo instead of confirmation.** Confirm and Discard show a snackbar with Undo (the transaction goes back to the queue exactly as it was); there is no "are you sure" step for these reversible actions. Deleting a transaction, which is not reversible, keeps its confirmation dialog.
- **Haptics** are reserved for meaningful moments, fired from the causal event: the swipe commit threshold, confirming or discarding a review item, saving a transaction, saving a scanned receipt.

## Navigation and layout

- **Screen transitions**: tabs fade (peers); opening a screen slides it a short distance on a spring with a fade; Back reverses it. The camera only fades. Predictive back is enabled (`enableOnBackInvokedCallback`).
- **Floating tab bar**: a rounded surface inset from the edges, with a selection pill that slides between tabs on a spring so a quick second tap redirects it mid-flight. Tabs are 56 dp tall, selectable with the Tab role, and the first tab is called "Overview" rather than the generic "Home".
- **Top and bottom bars** animate in and out; the title cross-fades.
- **Wide screens**: content keeps a readable width (640 dp) and is centred on tablets, unfolded foldables and landscape; the camera screen stays full width.
- **Loading**: the dashboard shows nothing until its first data arrives and then fades in, so "Unavailable" or zero totals never flash first.
- **Lists**: stable keys plus `animateItemPlacement`, so banners, filters, sorting and reordering move rows instead of jumping. Scroll position is kept.
- **Removal with feedback**: a review card collapses first, then the data changes, then the snackbar says what happened.
- **Charts**: bar heights, category shares, budget progress and the spending ring animate their shape only. Amounts, totals and percentages are plain text that always shows the exact current value; nothing counts up or down.
- **Lock screen**: only the message under the PIN field fades. Locking and unlocking are not animated, so nothing waits on motion and protected screens are never kept on screen to finish an animation.

## Typography and colour

- One family, the system font, so text matches the device and the user's font settings. Hierarchy comes from size, weight and leading together.
- Tracking is optical and in `em`: large text is tracked tighter and leaded tighter (display 40 sp at -0.025 em with 44 sp leading), small text is tracked slightly open (11-12 sp at +0.01 to +0.02 em). Balances use tabular figures. All text is in `sp`; layout in `dp`.
- Colour carries meaning, not decoration: neutral ink for text and titles, the terracotta accent only for things that can be tapped or are selected, green and red for income and expense (always with a word, a sign and an icon as well).
- The palette's container colours are distinct from the surface colour. They were identical before, which made Material pick the terracotta "on primary container" as the default text colour on every card and made selected chips almost indistinguishable.
- Cards use one radius family (20 dp for cards, 18 dp for rows, 22 dp for the summary tiles) and no outlines.

## Accessibility

- Every Compose animation is scaled by the system animator duration scale, so turning animations off (Developer options, or Accessibility "Remove animations") makes them instant, including the swipe settle. `rememberReduceMotion()` observes that setting and navigation then uses fades only.
- Errors and progress on the camera screen are live regions; decorative swipe labels are hidden from screen readers (the actions are exposed once, as custom actions).
- Status is never carried by motion or colour alone, and exact amounts stay in content descriptions.

## Performance

- Nothing loops. There are no shimmer or idle effects.
- Per-frame work is limited to drawing (chart reveal) or layer changes (press scale, swipe offset). Animated values are read in `graphicsLayer` and draw blocks, so they do not recompose.
- No new dependency. The translucent "glass" chrome that blurs what is behind it was not adopted: it needs a backdrop-blur library, and without one a translucent bar over scrolling text hurts legibility. The tab bar is a solid, softly shadowed surface instead.
- No frame-rate claims are made. Nothing has been profiled on a low-end device; check by hand using [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md).

## Adding to the UI

Prefer a stable key plus `animateItemPlacement` over a custom animation; use the `Motion` springs instead of new numbers; start feedback on touch-down; animate geometry or opacity, never a financial value; give every gesture a non-gesture path; and check the screen with animations turned off, the largest font scale and dark theme.
