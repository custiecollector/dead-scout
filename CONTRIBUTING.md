# Contributing

DeadScout is developed as a standalone Android-first RF/SDR packet analysis app.

## Development checks

Before opening a change, run:

```bash
gradle --no-daemon clean assemauxRelease
./scripts/build_desktop.sh
java -jar build/deadscout-desktop.jar --self-test
```

## Repository hygiene

Do not commit:

- Android SDK paths or `local.properties`.
- Keystores, signing passwords, tokens, private keys, or generated release artifacts.
- Captures that include private packet data or location history.
- Local notes, machine-specific paths, or machine-specific diagnostic output.

Keep public documentation focused on product behavior, build requirements, and hardware/runtime boundaries.
