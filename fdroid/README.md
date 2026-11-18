# F-Droid submission notes

Application ID: `com.seagori.hgr`

Suggested fdroiddata recipe values:

- License: `GPL-3.0-or-later`
- SourceCode: `https://github.com/seagori/hgr`
- IssueTracker: `https://github.com/seagori/hgr/issues`
- AuthorName: `seagori`
- AuthorWebSite: `https://github.com/seagori`
- Categories: `Productivity`
- AntiFeatures: none
- AutoName: `One Step`
- RepoType: `git`
- Repo: `https://github.com/seagori/hgr`

Store listing metadata for five locales lives in
`fastlane/metadata/android/` (en-US, ko-KR, ja-JP, zh-CN, zh-TW).

The build recipe itself (`metadata/com.seagori.hgr.yml`) is submitted to
the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository via merge
request. The recipe must reference a tagged commit; tag the release first.

Current version: versionName `0.2.0`, versionCode `1`.

## Build notes

- `app/build.gradle.kts` disables Google `dependenciesInfo` (`includeInApk = false`, `includeInBundle = false`) so no proprietary dependency metadata block is embedded in release APKs.
- All dependencies are open-source (Apache-2.0 / MIT-style), with no Google Play Services, ML Kit, or Firebase.
- Example `Builds` block for `metadata/com.seagori.hgr.yml`:

```yaml
Builds:
  - versionName: 0.2.0
    versionCode: 1
    commit: v0.2.0
    subdir: app
    gradle:
      - yes
```
