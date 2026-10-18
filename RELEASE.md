# Building signed GitHub release APKs

The **Signed release APK** workflow runs when a GitHub release is published
(including prereleases). It checks out the release commit, runs release unit tests
and lint, builds the APK, verifies its signing certificate against the SHA-256
fingerprint in README.md, and attaches `PollenWitan.apk` and a SHA-256 checksum.
The existing local `keystore.properties` signing setup is unchanged.

## One-time secret setup

Keep your existing JKS and its offline backup. Use the same key and alias that
signed previous releases so installed apps can update. Do not commit the JKS,
its Base64 representation, or passwords, or paste them into a chat or issue.

In repository Settings → Secrets and variables → Actions, add these **repository
secrets** (not variables):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the existing JKS |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password (`storePassword`) |
| `ANDROID_KEY_ALIAS` | Signing key alias (`keyAlias`) |
| `ANDROID_KEY_PASSWORD` | Private key password (`keyPassword`; may equal the store password) |

On Linux, with GitHub CLI authenticated, upload the JKS directly without creating
an encoded file or printing it to the terminal. Replace the example path:

```bash
set -o pipefail
base64 -w 0 /path/to/your-signing-key.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo ryan-buttery/PollenWitan
```

The encoded value must fit GitHub's 48 KB secret limit. Base64 is an encoding,
not encryption; GitHub's secret storage protects it. Add the other three values
using the GitHub secret entry form. An empty password is not supported by this
workflow. The runner reconstructs the key in its temporary directory and writes
owner-only `keystore.properties`; both are removed even when the build fails.
They are never uploaded as artifacts or deliberately printed.

Only trusted repository maintainers should publish releases or modify workflows:
code running with these secrets can access the signing key. If the JKS contains
keys for other apps, prefer uploading a separate keystore containing only the
existing PollenWitan signing entry (preserve that entry's private key/certificate).

## Publish a release

1. Merge this workflow through the active release branch into `master`, following
   the repository's normal release process. The release tag must include this
   workflow; publishing a release for an older commit does not retrofit it.
2. Increment `versionCode` and set `versionName` in `app/build.gradle.kts` before
   tagging. The workflow does not change Android version numbers.
3. Tag the intended release commit, then publish a GitHub Release from that tag.
4. Watch **Actions → Signed release APK**. The release is public immediately;
   its APK and checksum appear after the build succeeds.
5. Download and test installing/upgrading the APK on a device for the first run.

Draft releases do not trigger the workflow. No signing secrets are supplied by
this workflow to pull-request builds. No additional personal access token is
needed: attachment upload uses the workflow's repository-scoped `GITHUB_TOKEN`.

## Failures and retries

After correcting missing secrets, use **Re-run failed jobs** on the release's
workflow run. An invalid key/password fails the build; a different signing
certificate fails verification before upload. Do not change the expected
fingerprint just to bypass that check.

Uploads deliberately do not overwrite existing assets. If an upload was only
partly completed, inspect the release and remove only the incomplete run's assets
before retrying. For a release that users have already downloaded, prefer a new
version/tag rather than replacing its APK. Immutable releases must also use a
new release if their assets have been locked.

The real signing/build/upload path must be verified after the owner configures
secrets. Local static checks cannot establish that the supplied JKS is correct.
