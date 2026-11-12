# Design language and motion

The UI follows a short set of rules taken from Apple's fluid-interface guidance and translated to Jetpack Compose: respond on touch-down, track the finger 1:1, hand the release velocity to a spring, never block input during motion, and keep every animation interruptible. The dark, single-accent palette and the privacy-first behaviour are unchanged. Movement explains a change of state; it never delays an action or changes what a financial figure says.

Code: `core/ui/Motion.kt` (springs, press feedback, chart helpers), `core/ui/Gestures.kt` (swipe actions and the gesture maths), `core/ui/TabBar.kt` (floating tab bar), `core/ui/Glass.kt` (the flat bar chrome), `core/theme/Type.kt` and `Theme.kt`.

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
- **Floating tab bar**: a flat, outlined surface (see "Bar chrome" below) inset from the edges, with a selection pill that slides between tabs on a spring so a quick second tap redirects it mid-flight. Tabs are 56 dp tall, selectable with the Tab role, and the first tab is called "Overview" rather than the generic "Home".
- **Top and bottom bars** animate in and out; the title cross-fades. Content fades into the page for 16 dp above the tab bar (and under the title bar once scrolled) instead of ending in a hard cut. Both fades are drawn in the draw phase, so scrolling does not recompose.
- **Wide screens**: content keeps a readable width (640 dp) and is centred on tablets, unfolded foldables and landscape; the camera screen stays full width.
- **Loading**: the dashboard shows nothing until its first data arrives and then fades in, so "Unavailable" or zero totals never flash first.
- **Lists**: stable keys plus `animateItemPlacement`, so banners, filters, sorting and reordering move rows instead of jumping. Scroll position is kept.
- **Removal with feedback**: a review card collapses first, then the data changes, then the snackbar says what happened.
- **Charts**: bar heights, category shares, budget progress and the spending ring animate their shape only. Amounts, totals and percentages are plain text that always shows the exact current value; nothing counts up or down.
- **Lock screen**: only the message under the PIN field fades. Locking and unlocking are not animated, so nothing waits on motion and protected screens are never kept on screen to finish an animation.

## Typography and colour

- One family, the system font, so text matches the device and the user's font settings. Hierarchy comes from size, weight and leading together.
- Tracking is optical and in `em`: large text is tracked tighter and leaded tighter (display 40 sp at -0.025 em with 44 sp leading), small text is tracked slightly open (11-12 sp at +0.01 to +0.02 em). Balances use tabular figures. All text is in `sp`; layout in `dp`.
- Colour carries meaning, not decoration: neutral ink for text and titles, the blue accent only for things that can be tapped or are selected, green and red for income and expense (always with a word, a sign and an icon as well).
- The palette's container colours are distinct from the surface colour. They were identical before, which made Material pick the accent "on primary container" as the default text colour on every card and made selected chips almost indistinguishable.
- Cards use one radius family (`Radii` in `core/theme/Dimens.kt`: 6, 10, 12 and 14 dp for small controls, controls, rows and cards, 20 dp for sheets) and a hairline outline instead of elevation. Spacing is on an 8 dp grid with 4 dp half steps (`Spacing`), sizes such as the 48 dp touch target are in `Sizes`.
- The app is dark only with one accent (blue); green and red are reserved for money in/out and market up/down. `ThemeContrastTest` holds every text colour to 4.5:1 on the surfaces it is drawn on, and the category colours to 3:1.

## Layout at large font scales

The app is checked at 360 dp wide and at font scale 1.5, in English and Hungarian. The rules that came out of that:

- **An amount is never truncated or wrapped.** `AmountRowLayout` (used by `ListRow`, `ValueRow`, `TransactionRow`, the account cards and the dashboard category rows) measures the amount first, gives the name what is left and moves the amount under the name when that would be under 96 dp. Never put a name and an amount in a plain `Row`: the unweighted name takes the width and the amount collapses to one character per line.
- **Names are ellipsized, never the figures and dates.** Merchants and categories get two lines (three at font scale 1.3 and up); the quiet line under a transaction (category, date, status) may take two lines so the date is not lost.
- **Fixed-size spots shrink their text.** Where the width cannot grow (the tab bar labels, the figure in the middle of the donut) `ShrinkToFitText` steps the size down to a floor of 11 sp at font scale 1.0 and only then ellipsizes. Text is never capped below the user's font scale: the donut ring grows with the scale (220 dp at 1.0, up to 280 dp, which still fits a 360 dp card; `donutRingSize`) so the hole keeps room for the label, the total and the share, and the dashboard shortcuts (`QuickActions`) sit side by side only while the longest word of every label fits its tile, otherwise they stack as full-width rows. A long category name in the donut hole is ellipsized there; the legend below repeats it in full.
- **Choices wrap.** Chips for a short fixed set (currencies, account types, the theme) use `ChipFlow` / `CurrencyChips` (a `FlowRow`), never a `Row` of five chips.
- **Dialogs and sheets scroll.** Multi-field dialogs use `DialogColumn`; the transaction detail sheet scrolls. On Transactions the search field and the period filters are a quick-return header (`QuickReturnScaffold`): shown at the top of the list, hidden once the list is scrolled down by about 24 dp, back on the first scroll up, and kept in place while the search field has focus. Pinned permanently they took about a third of the screen at 1.5. The header moves with a placement offset (not a graphics layer) so its accessibility bounds move with it; a hidden header is still in the accessibility tree.
- **Transfers look different.** Own-account moves and money to or from a person use the grey transfer colour, a swap icon, the label "Transfer between own accounts" or "Transfer with a person" in the row's spoken description and in the detail sheet, and are listed apart from income and spending in Analytics.

## Bar chrome

The floating tab bar is flat: `Modifier.glassChrome` (`core/ui/Glass.kt`) gives it an opaque surface fill and a hairline outline, with no shadow, transparency or blur, so its contrast never depends on what scrolls behind it. `GlassContrastTest` holds the unselected label and the selected label on its accent pill to 4.5:1.

## Not done in Phase 3, and why

| Item | Status |
| --- | --- |
| Predictive back that follows the finger inside the app | Not done. Navigation Compose 2.7.7 animates back with its fixed pop transition; `enableOnBackInvokedCallback` is on, so the system back-to-home animation works. A follow-the-finger screen transform needs Navigation 2.8 / Compose 1.7 or a hand-written `PredictiveBackHandler`, and neither could be verified without a physical gesture-navigation device |
| Shared-element and container transforms | Not done. They need Compose 1.7 (a BOM upgrade); not worth the upgrade risk for an app whose rows open a sheet in place |
| `Modifier.animateItem` | Not applicable yet: this BOM ships `animateItemPlacement`, which every list that reorders already uses |
| Animated (counting) numbers | Deliberately not done: a financial figure always shows its exact current value |
| Baseline profile | Not done: it needs a macrobenchmark module and new dependencies, which is not cheap |
| Month slide in Analytics | Not done: the cross-fade stays; a slide delays reading the new month for no gain |

## Accessibility

- Every Compose animation is scaled by the system animator duration scale, so turning animations off (Developer options, or Accessibility "Remove animations") makes them instant, including the swipe settle. `rememberReduceMotion()` observes that setting and navigation then uses fades only.
- Errors and progress on the camera screen are live regions; decorative swipe labels are hidden from screen readers (the actions are exposed once, as custom actions).
- Status is never carried by motion or colour alone, and exact amounts stay in content descriptions.
- Rows that act as a choice (the Analytics legend) press-scale on touch-down like every other tappable row.
- The donut chart has a spoken description (total and every category with its share); the legend rows are selectable buttons that read as one element and press-scale on touch-down like every other tappable row; the transaction detail sheet's close button is labelled.
- Checked on the emulator: with the animator scale at 0 a tab switch is complete 120 ms after the tap (screen and tab pill); at normal speed the same capture is mid cross-fade with the pill mid-slide.

## Performance

- Nothing loops. There are no shimmer or idle effects.
- Per-frame work is limited to drawing (chart reveal) or layer changes (press scale, swipe offset). Animated values are read in `graphicsLayer` and draw blocks, so they do not recompose.
- No new dependency,, and no backdrop blur. The flat bar is a `background` and a `border`, so it adds no extra layer.
- No frame-rate claims are made. Nothing has been profiled on a low-end device; check by hand using [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md).

## Adding to the UI

Prefer a stable key plus `animateItemPlacement` over a custom animation; use the `Motion` springs instead of new numbers; start feedback on touch-down; animate geometry or opacity, never a financial value; give every gesture a non-gesture path; and check the screen with animations turned off, the largest font scale and dark theme.
