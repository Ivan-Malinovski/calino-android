# Google Play build

The Play bundle differs from the GitHub APK in one way: the in-app check
against Calino's GitHub releases is off (`R.bool.calino_update_check_enabled`
is false), because Play delivers updates and its policy does not allow steering
people to another update source.

```bash
distrobox enter android-sdk -- bash -lc './gradlew :app:bundleRelease -PplayStore=true'
# app/build/outputs/bundle/release/app-release.aab
```

Without `-PplayStore=true` the flag defaults to the GitHub behaviour, so
`assembleRelease` is unchanged. The bundle is signed with the same release key
(`keystore/`, backed up in Bitwarden; see `HANDOFF.md`).

## Before the first upload

- **Play App Signing.** Upload the existing release key when the app is
  created if people who sideloaded should be able to update from Play. A key
  Google generates cannot update an installed sideloaded build; those users
  would have to uninstall and lose app data. This is chosen once, at creation.
- **Settings > About > Releases** (an uncommitted change when this was written)
  links to GitHub. Hide it in the Play build with the same resource flag.
- Console work that cannot be done from the repo: privacy policy URL, store
  listing and graphics, Data safety form (calendar/contact data goes only to the
  server the person enters; AI photo import goes to the provider they pick),
  content rating, the `SCHEDULE_EXACT_ALARM` declaration (calendar reminders),
  and any closed-testing requirement for the account.
