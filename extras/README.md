# Extras

Files the app downloads when it needs them, instead of carrying them in every update.

- `subfont.ttf`: the full subtitle font for the MPV player (letters for every language). The app ships a small
  Roboto font and downloads this one once in the background (see `AppUpgradeHandler.copySubfont`).
  Don't rename or move it: installed apps download it from this exact path on `main`.
