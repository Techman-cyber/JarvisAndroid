# Jarvis (Android)

A native Android voice assistant: always-on "Hey Jarvis" wake word, a
live animated particle-sphere UI (blue in normal mode, red in serious
mode, pulsing with your voice), speaks only the important line while the
full answer is logged on screen, calls contacts, opens apps, YouTube
search, media/volume control, and Gemini-powered image generation.

Your Gemini API key is baked into the app at build time from a GitHub
secret — it is never committed to the repo and never visible in your
source code.

No Python, no Android Studio required. GitHub Actions builds the APK for
you in the cloud.

## Step 1 — Create the repo
1. Go to github.com → **New repository**. Name it whatever you like
   (e.g. `jarvis-android`). It can be public or private — private is
   fine and free on GitHub for personal repos.
2. Don't add a README/gitignore/license from GitHub's UI (this project
   already has them) — just create the empty repo.

## Step 2 — Upload the project
Easiest way (no git command line needed):
1. Unzip this project on your computer.
2. On your new repo's GitHub page, click **"uploading an existing file"**
   (or the "Add file → Upload files" button).
3. Drag the *contents* of the unzipped folder in (not the folder itself —
   `app/`, `.github/`, `build.gradle.kts`, etc. should all be at the repo
   root).
4. Scroll down, commit directly to `main`.

(If you're comfortable with git instead: `git init`, `git remote add
origin <your-repo-url>`, `git add .`, `git commit -m "init"`,
`git branch -M main`, `git push -u origin main`.)

## Step 3 — Add your Gemini API key as a secret
This is what lets the key be built into the app without ever appearing
in your repo's code:
1. On your repo page: **Settings → Secrets and variables → Actions**.
2. Click **New repository secret**.
3. Name: `GEMINI_API_KEY`
4. Value: paste your actual Gemini API key.
5. Click **Add secret**.

## Step 4 — Build the APK
If you already pushed the code in Step 2, the build already ran
automatically. Otherwise:
1. Go to the **Actions** tab on your repo.
2. Click **Build APK** in the left list, then **Run workflow** → **Run
   workflow** (green button) if it hasn't run yet.
3. Wait 3-5 minutes for it to finish (green check mark).
4. Click into the finished run → scroll to **Artifacts** → download
   `jarvis-debug-apk` (a zip).

## Step 5 — Install on your phone
1. Unzip the downloaded artifact to get `app-debug.apk`.
2. Transfer it to your phone (email it to yourself, use a cloud drive,
   or a USB cable).
3. Tap the file on your phone. If Android blocks it, it'll prompt you to
   allow "install unknown apps" for whichever app you opened it with —
   allow that, then tap install again.
4. Open the app and grant the permissions it asks for (microphone,
   contacts, phone, notifications).

## First run
1. Tap **Start Jarvis** — you'll see a persistent notification; it's now
   listening in the background.
2. Say **"Hey Jarvis"**. If you don't add a command in the same breath,
   it says "Yes sir?" and waits for the next thing you say.
3. Your API key is already built in — Settings only needs touching if
   you want to override it, change the wake word, add contact aliases
   like `boss=John Smith`, switch to "ma'am", or pick a female voice.

### Important — battery optimization
Go to **Settings → Apps → Jarvis → Battery** on your phone and set it to
**Unrestricted** (disable battery optimization for Jarvis). Without this,
Android will kill the background listener after a few minutes with the
screen off — this is a phone-level restriction on every Android app that
listens in the background, not a bug in Jarvis.

## Example commands
- "Hey Jarvis, open YouTube and search for Mr Beast"
- "Hey Jarvis, call mom"
- "Hey Jarvis, generate an image of a cyberpunk city"
- "Hey Jarvis, serious mode" / "normal mode" (or just tap the mode pill)
- "Hey Jarvis, volume up" / "pause" / "next track"
- "Hey Jarvis, remember that my wifi password is..." — Jarvis stores this
  forever (until you tell it to forget) and will use it in future answers.
- "Hey Jarvis, what do you remember?" — lists everything stored.
- "Hey Jarvis, forget my wifi password" — removes anything matching.
- "Hey Jarvis, clear your memory" — wipes both stored facts and the whole
  conversation log (also available as a button in Settings).
- Anything else falls back to Gemini — Jarvis speaks one short line, the
  full answer appears in the on-screen log.

## How memory works
- **Long-term facts**: only created when you explicitly say "remember...".
  These persist forever on the device (SharedPreferences, not synced
  anywhere) and are injected into every Gemini answer so it can actually
  use them.
- **Conversation history**: every exchange is saved automatically and
  restored on screen the next time you open the app — closing the app no
  longer wipes the log. The last several turns are also fed back to
  Gemini so it stays coherent across a conversation instead of treating
  every question in isolation.
- Nothing is sent anywhere except to Gemini's API with your key, and only
  when Jarvis actually needs to answer something — memory itself lives
  only on your phone.

## If a build fails
Open the failed Actions run and read the red step's log — it'll usually
say exactly which line/file is the problem. Paste that error back to me
and I'll fix the source directly.

## Known limitations
- `SpeechRecognizer` isn't a true continuous stream — this app restarts
  it in a fast loop to approximate always-on listening (same approach
  Techman-cyber/Android-Jarvis uses). There's a small gap between
  utterances.
- Some OEM Android skins (MIUI, ColorOS, One UI, etc.) kill background
  services more aggressively than stock Android even with battery
  optimization off — check your phone brand's "auto-start"/"protected
  apps" settings too if it keeps going silent.
- `QUERY_ALL_PACKAGES` (needed to find/launch apps by name) is fine for a
  personal sideloaded APK but would need justification to publish on the
  Play Store.

  ## Made by Techman-cyber
