# Motion design

Movement in Finances explains a change of state. It is short, decelerating and never decorative, and it never delays an action or changes what a financial figure says.

All helpers live in `app/src/main/java/com/teppe21/finances/core/ui/Motion.kt`.

## Timing and easing

| Token | Value | Used for |
| --- | --- | --- |
| `DURATION_SHORT` | 150 ms | Fades, press feedback, colour changes, error text |
| `DURATION_MEDIUM` | 250 ms | Content swapping, expand/collapse, list rows moving or collapsing |
| `DURATION_LONG` | 350 ms | Screen-level moves, chart geometry |
| `Decelerate` | cubic-bezier(0.05, 0.7, 0.1, 1) | Things entering or moving |
| `Accelerate` | cubic-bezier(0.3, 0, 0.8, 0.15) | Things leaving (and exits are faster than entrances) |
| `Standard` | FastOutSlowIn | Changes between two visible states |
| `press()` | spring, no bounce, medium stiffness | Direct-manipulation feedback |

## Patterns

- **Screen transitions** (`MainAppNavigation`): switching between the four tabs fades (the screens are peers). Opening a screen from another slides it in a short distance (a tenth of the width) with a fade; Back reverses it. The camera screen only ever fades, so the viewfinder never moves.
- **Bars**: the top bar and bottom bar animate in and out when the camera opens or a sub-screen is shown; the title cross-fades; the Back arrow fades in.
- **Loading**: the dashboard shows nothing until its first data arrives, then fades in once, so "Unavailable" or zero totals never flash first.
- **Lists**: rows have stable keys and use `animateItemPlacement`, so a banner appearing, a filter or sort change, or a reordered watchlist moves rows instead of jumping. Only the empty to non-empty switch of the transaction list is a cross-fade; the list is otherwise the same composable, so scroll position is kept.
- **Removal with feedback** (review queue): the card collapses first, then the data changes, then a snackbar says what happened. Buttons are disabled while the card is leaving, so nothing can be confirmed twice.
- **Expanding content**: `animateContentSize` on budget cards when "Over budget" or a missing-rate note appears; `AnimatedVisibility` with `Motion.appear()` / `Motion.disappear()` for errors in the scan screen and review sheet.
- **Charts**: bar heights, category shares and budget progress grow to their value (`animatedFraction`); the spending ring draws in clockwise when its data changes (`rememberReveal`, read in the draw phase so only drawing is invalidated). Only geometry animates. Amounts, totals and percentages are plain text that always shows the exact current value; there is no counting up or down.
- **Press feedback**: `pressClickable` adds a 3 % press scale to the dashboard actions, transaction rows, stock rows and banners; the click is never delayed.
- **State colours**: the Expense/Income toggle and budget bars animate their colour (150 ms).
- **Lock screen**: only the message under the PIN field fades. Locking and unlocking are not animated, so nothing waits on motion and protected screens are never kept on screen to finish an animation.

## Accessibility

- Every Compose animation here is scaled by the system animator duration scale, so turning animations off (Developer options, or Accessibility "Remove animations") makes them instant.
- `rememberReduceMotion()` observes that setting; navigation then uses fades only, with no slide.
- Errors and progress on the camera screen are live regions; the error banner can be dismissed by tapping it.
- Status is never carried by motion or colour alone: banners, snackbars and labels say it in words, and the exact amounts stay in content descriptions.

## Performance

- Nothing loops. There are no shimmer or idle effects.
- Per-frame work is limited to drawing (chart reveal) or layer changes (press scale). Charts read animation state in the draw phase.
- No new dependency: everything uses Compose animation, foundation and Navigation 2.7 APIs that were already on the classpath.
- No frame-rate claims are made. Nothing has been profiled on a low-end device; check the screens listed in [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md) by hand.

## Adding motion

Prefer a stable key plus `animateItemPlacement` over a custom animation; use the `Motion` specs instead of new numbers; animate geometry or opacity, never a financial value; make sure a screen is still correct with animations turned off.
