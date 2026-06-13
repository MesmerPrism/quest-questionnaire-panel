# BRB Panel Audio Assets

The audio under `app/src/main/assets/BRBStudyAudio` mirrors the BRB study audio
inventory from George's Unity feature branch, excluding Unity `.meta` files
because Android would package those as runtime assets. This keeps the panel
repo faithful to the source audio design while the split-app runtime decides
which app owns each cue.

The speech files were generated with ElevenLabs for this project and imported
with collaborator approval. Keep the project MIT license and this provenance
note together when redistributing the panel source. Confirm the generating
ElevenLabs account/subscription permits the intended public and commercial use
before publishing release APKs or asset bundles.

In the current split-app runtime, the panel plays questionnaire prompts and UI
feedback. Unity remains the owner of condition-session playback, the 3D button,
and physical press evidence unless the contract changes.
