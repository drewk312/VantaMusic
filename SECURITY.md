# Security

## Only install official releases

Download VANTA only from this repository's [Releases page](https://github.com/drewk312/VantaMusic/releases). Mirrors, re-uploads and "modded" copies are not VANTA and may be unsafe.

## Verify your download

Each release lists the APK's SHA-256 checksum. To check a download on a computer:

- **Windows (PowerShell):** `Get-FileHash .\vanta.apk -Algorithm SHA256`
- **macOS / Linux:** `shasum -a 256 vanta.apk`

The result must match the checksum in the release notes exactly.

On Android 9 and newer, official VANTA releases from 1.00 on are verified with this signing certificate (SHA-256):

```
2bda2ae08fcec66d44115544daacb1b8fa8575e05ee32f52f84d8594ae88b0b9
```

A checksum confirms the file wasn't changed in transit; Android's signature check confirms it came from us. Updates signed by anyone else won't install over an official VANTA.

## Reporting a problem

Never post passwords, signing keys, access tokens, private backups or personal account data in issues or attachments. If you think you've found a security issue, message us privately on [Discord](https://discord.gg/vN6ztK6m6g) instead of opening a public issue.
