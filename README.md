# idlepay — JetBrains / IntelliJ client (unofficial)

A native JetBrains plugin that brings [idlepay](https://idlepay.co) to IntelliJ IDEA, PyCharm,
WebStorm, Android Studio, and the rest of the JetBrains family. idlepay sells the idle moments of
your AI coding sessions to sponsors and pays you a share of every impression. The official client
ships only as a VS Code extension + a Claude Code status-line script — this plugin fills the
JetBrains gap.

> Not affiliated with idlepay. This is a compatible community client for a service you sign up for at
> idlepay.co. It talks to the same public backend the official client uses.

## How it earns

idlepay credits impressions on two independent **surfaces**. This project makes both work under
JetBrains:

1. **Claude Code terminal status line.** The status-line script (installed at
   `~/.idlepay/idlepay-statusline.mjs`) already fetches ads and sends *credited* impressions on its
   own — but only if `~/.idlepay/identity.json` exists with a valid `{ deviceId, token }`. The
   official client only writes that file from VS Code. **This plugin writes it**, so `claude` running
   in the JetBrains integrated terminal starts earning.
2. **Editor status bar.** A status-bar widget shows the current sponsored line and sends its own
   credited heartbeat (`GET /ad/{developerId}`) every 30 s — the JetBrains equivalent of the VS Code
   extension's status bar. This is additive earning on top of surface 1.

Both are gated identically to upstream: a credited beacon fires only when you're signed in **and**
the IDE is focused (or the terminal heartbeat is fresh) **and** a Claude transcript under
`~/.claude/projects/` was touched in the last 5 minutes. The `x-idlepay-active: 1` assertion is only
ever sent when that gate passes, so it stays honest.

## Requirements

- A JetBrains IDE (2025.2+ recommended) or Android Studio.
- JDK 17+ to build (the repo builds against IntelliJ Platform 2025.2 with a JDK 21 toolchain).

## Works in every JetBrains IDE

The plugin depends only on `com.intellij.modules.platform` (the module shared by all JetBrains IDEs)
and uses no IDE-specific APIs, so the single built `.zip` installs in IntelliJ IDEA, PhpStorm,
WebStorm, PyCharm, GoLand, RubyMine, CLion, Rider, DataGrip, Android Studio, etc. — build 252 (2025.2)
and later, with no upper bound. The `intellijIdea(...)` line in `build.gradle.kts` is only the
build/test target; it does not restrict where the plugin runs.

## Build & run

```bash
# Launch a sandbox IDE (IntelliJ IDEA, the build target) with the plugin loaded:
./gradlew runIde

# Launch the plugin inside your locally installed PhpStorm (no extra download):
./gradlew runPhpStorm      # add runWebStorm/runPyCharm blocks in build.gradle.kts as needed

# Or build an installable package to drop into any JetBrains IDE:
./gradlew buildPlugin
# → build/distributions/idlepay-0.1.0.zip
# Install via: Settings → Plugins → ⚙ → Install Plugin from Disk…
```

## Signing in

`Tools → idlepay → Sign in to idlepay`, or click the status-bar widget.

1. Your browser opens `idlepay.co/connect` for the Google sign-in.
2. **Auto-capture (best-effort):** if the server redirects to `jetbrains://…/linked?token=…` and your
   OS routes the `jetbrains` scheme to this IDE, the plugin captures the token automatically.
3. **Manual paste (reliable fallback):** otherwise, copy the token shown on the idlepay page and paste
   it into the dialog the plugin opens.

Either way the plugin writes `~/.idlepay/identity.json` and starts crediting. Sign out with
`Tools → idlepay → Sign out` (removes `identity.json`, keeping your device id).

> **Known unknown:** the upstream sign-in callback authority (`idlepay.idlepay`) is derived from the
> VS Code extension id, and it's unverified whether idlepay's server will emit a JetBrains-routable
> callback URL. Until confirmed, the manual paste path is the supported one. See the sign-in section
> of the design notes.

## Project layout

```
src/main/kotlin/co/idlepay/intellij/
├── IdlepayConstants.kt          endpoints, timings, keys (ported from upstream)
├── model/Ad.kt                  Ad / AdStyle / DeveloperEarnings / DeveloperProfile
├── api/IdlepayApi.kt            HTTP client: /ad, /ad/{id}, /developer/*, /click (+ SSRF guard)
├── core/
│   ├── IdlepaySettings.kt       persisted deviceId + token
│   ├── IdlepayIdentity.kt       writes ~/.idlepay/identity.json (atomic)
│   ├── ClaudeActivity.kt        transcript freshness + heartbeat gating
│   ├── IdlepayService.kt        ad / heartbeat / account background loops
│   └── IdlepayStartup.kt        startup kick
├── auth/
│   ├── IdlepaySignIn.kt         browser + paste flow
│   └── IdlepayProtocolCommand.kt  jetbrains:// auto-capture (best-effort)
├── statusbar/…                  the status-bar widget
└── actions/…                    Sign in / Sign out / Dashboard
```

## Status

- ✅ Ad fetch + status-bar widget (validated against the live backend)
- ✅ Credited heartbeat with faithful activity gating
- ✅ `identity.json` sign-in → terminal status line earns
- ⏳ Deep-link auto-capture — needs one real sign-in to confirm server behavior
- ⏳ Optional: sponsor colors in the status bar, richer account popup

## License

Source-available in the spirit of the upstream client's FSL-1.1-MIT. Trademarks and the idlepay
service belong to idlepay.
