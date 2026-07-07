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

## Installation

You don't need to build anything — grab the packaged plugin from the Releases page:

1. Download the latest **`idlepay-<version>.zip`** from the
   [**Releases**](https://github.com/Gizmo091/idlepay-jetbrains-plugin/releases/latest) page.
   Download the `.zip` as-is — **do not unzip it**.
2. Open your IDE's settings:
   - **macOS:** `<IDE name> → Settings…`  (`⌘,`)
   - **Windows / Linux:** `File → Settings…`  (`Ctrl+Alt+S`)
3. Select **Plugins** in the left-hand list.
4. Click the **gear icon ⚙** (the cog, top of the panel next to the *Marketplace* / *Installed* tabs).
5. Choose **Install Plugin from Disk…**
6. Pick the downloaded `idlepay-<version>.zip` and click **OK**.
7. Click **Restart IDE** when prompted.

After the restart, open any project: the green **idlepay** widget appears in the status bar
(bottom-right). Click it → **Sign in to idlepay** to start earning. If you're already signed in on
this machine (a valid `~/.idlepay/identity.json` exists — e.g. from the official VS Code client), the
plugin adopts it automatically and the widget is signed in on first launch.

Repeat in each JetBrains IDE you use — the same `.zip` works everywhere.

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
# → build/distributions/idlepay-<version>.zip  (install it per the Installation section above)
```

## Signing in

`Tools → idlepay → Sign in to idlepay`, or click the status-bar widget (signed out → clicking starts
sign-in; signed in → clicking opens the idlepay menu).

1. Your browser opens `idlepay.co/connect` for the Google sign-in.
2. **Auto-capture (best-effort):** the server redirects to `jetbrains://idlepay.idlepay/linked?token=…`
   (it echoes the `scheme` we pass). JetBrains routes it to our `linked` command by the first path
   segment, so if your OS hands `jetbrains://` to an IDE that has this plugin installed, the token is
   captured automatically.
3. **Manual paste (reliable fallback):** OS-level `jetbrains://` routing isn't guaranteed, so if the
   IDE doesn't pick it up, paste the token — or the **whole `jetbrains://…` URL**, the plugin extracts
   the token — into the dialog the plugin opens.

Either way the plugin writes `~/.idlepay/identity.json` and starts crediting. Sign out from the
widget's menu or `Tools → idlepay → Sign out` (removes `identity.json`, keeping your device id).

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

- ✅ Ad fetch + status-bar widget (idlepay green, left-click menu) — validated against the live backend
- ✅ Credited heartbeat with faithful activity gating — earnings verified live
- ✅ `identity.json` sign-in → both the Claude Code terminal status line and the editor surface earn
- ✅ Sign-in callback format confirmed (`jetbrains://…/linked`); paste fallback accepts the full URL
- ✅ CI: build on push/PR, GitHub Release with the plugin `.zip` on `v*` tags
- ⏳ Optional: JetBrains Marketplace publishing (needs a vendor account + idlepay's OK on the name)

## License

Source-available in the spirit of the upstream client's FSL-1.1-MIT. Trademarks and the idlepay
service belong to idlepay.
