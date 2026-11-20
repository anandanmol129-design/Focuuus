# Focuuus

An Android app to cut down on distractions: block apps on a schedule, and put limits on the rest.

Designed like a mechanical diver's watch: a black dial, warm white print, neon orange hands. The
home screen is a GMT watch with today's routines on its 24-hour bezel; a pill at the bottom switches
between Routines and Limits.

## Features

**Routines**: block chosen apps at set times on set days, set by dragging two handles round a
24-hour bezel. Routines can run past midnight. Today's routines show as orange arcs on the home
screen's GMT watch face. Opening a blocked app shows a "Locked" screen that only lets you leave.

**App limits**, per app:
- **Daily limit**: total time per day.
- **Wait before opening**: a countdown dial before the app opens.
- **Ask how long**: after the wait, choose 5, 10, 15, 30 or 60 minutes (never more than what's left
  today). Or turn it off and give every visit a fixed length.
- **Floating timer**: a draggable pill over the app with time left and time used. Tap it to switch
  to a compact countdown. It turns orange in the last minute.
- **Auto-close**: when time runs out, close the app (otherwise you only get a reminder).

## Permissions

- **Accessibility service**: to see which app is in front, cover blocked apps, and draw the floating timer.
  On Android 13+, sideloaded apps may need App info › ⋮ › Allow restricted settings first.
- **Usage access**: to read today's screen time per app.
- **Battery unrestricted** (recommended): keeps phone makers' battery savers from stopping the service.

Nothing leaves the phone; there is no internet permission.

## Staying alive

Android binds the accessibility service itself and restarts it within seconds if its process is
killed. The visit you chose is saved, so a restart carries on with the same time left. Force-stopping
Focuuus turns its accessibility service off until you turn it on again; the home screen then shows
the setup card.

## Build

```
./gradlew assembleDebug     # or assembleRelease (R8, debug-signed)
```

| File | What it does |
| --- | --- |
| `FocusService.kt` | Accessibility service: watches the foreground app, enforces routines and limits |
| `Bubble.kt` | The floating timer overlay |
| `BlockActivity.kt` | Locked, time's-up and waiting (choose how long) screens |
| `Apps.kt` | App list, icons, today's usage from UsageStatsManager |
| `Store.kt`, `Model.kt` | Routines and limits, saved as JSON in shared preferences |
| `ui/Watch.kt` | Watch face, 24-hour range dial, subdials, countdown dial, bezel ruler |
| `ui/` | Home, editors, app picker, palette and type |

## Licence

Code: MIT, see [LICENSE](LICENSE). Fonts: [Jost](https://github.com/indestructible-type/Jost) and
[JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License,
see [licenses/](licenses).
