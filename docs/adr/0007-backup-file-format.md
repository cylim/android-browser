---
status: accepted
---

# Backups are versioned JSON files, encrypted with a password by default

A backup (SPEC, "Backup") is one JSON file that the person saves and opens with Android's file picker. A plain backup is the data itself, marked `"format": "okapi-backup", "version": 1`. An encrypted backup is marked `"format": "okapi-backup-encrypted", "version": 1`. It holds the plain backup's JSON encrypted with AES-256-GCM, under a key derived from the person's password with PBKDF2-HMAC-SHA256. It also holds the salt, nonce and iteration count (600,000 when written). Those header fields are bound to the ciphertext as associated data, so a changed header fails the same way a wrong password does.

People keep backup files for years, and every later version of Okapi must still read the ones made by this one. That makes the format hard to reverse.

## Considered Options

- **A copy of the Room database file.** We rejected it. Its schema changes with every migration, it holds rows a backup must leave out (downloads, recently closed Tabs, shortcuts), and an import from it couldn't check each value the way typed input is checked.
- **A ZIP of JSON files.** We rejected it: there is nothing large enough to split, and one file is simpler to read, test and encrypt.
- **No encryption, or encryption only on request.** We rejected it. A backup holds a person's history in every Profile, and files are often copied to cloud drives and chats. Encryption is on unless the person turns it off.
- **Argon2 instead of PBKDF2.** We rejected it for now. Android has no Argon2 of its own, and ADR 0002 keeps libraries few. PBKDF2 is in every JDK and Android, so the unit tests run the same code as the app.
- **Android's Auto Backup.** We rejected it. It goes to Google's cloud, and `allowBackup` is off because web data holds signed-in sessions.

## Consequences

- A later version may add fields; readers ignore fields they don't know. A change that an older reader would misread needs a new `version`, and Okapi refuses a version above the one it knows, with a message.
- The iteration count is read from the file, so it can be raised later without a new version. A file that asks for more than 10,000,000 iterations is refused, so a crafted file can't stall the app.
- A forgotten password can't be recovered. The export dialog says so.
