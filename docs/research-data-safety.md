# Research Data Safety

This repository is public. Keep implementation examples useful without
committing raw participant data or private lab artifacts.

## Data Classes

Request metadata:

- protocol version;
- session id;
- request id;
- nonce;
- study id;
- questionnaire id;
- open stage and screen sequence;
- optional pseudonymous participant reference;
- optional small questionnaire-owned state such as a selected language code;
- optional caller package/version/engine.

Result payload:

- terminal status;
- questionnaire identity;
- answer JSON;
- optional terminal context;
- optional error code/message;
- optional screen-level timing metadata.

Some questionnaire answer payloads can contain directly identifying data. The
MAIA/spatial renderer records name and signature because that source program
requires them. Treat those result files as participant data and keep them in
caller-private storage until the study retention/export policy moves or
deletes them.

Local runtime data:

- caller-owned private result files;
- panel app-private drafts;
- pending request state in caller private storage.

Lab artifacts:

- APKs;
- screenshots;
- screen recordings;
- logcat bundles;
- device serials;
- signing keys;
- private evidence notes.

Lab artifacts stay out of committed files.

## Request Metadata

Use pseudonymous session and participant references. Do not put names, email
addresses, raw institutional identifiers, or answer values in request ids,
nonces, filenames, callback extras, notification text, or logs.

`participant_ref` is optional and caller-owned. Treat it as a pseudonymous
join key, not as an identity field.

## Answers

Answers live only in the result JSON written to the caller-owned result URI.
The callback is only a completion signal.

Do not send answers through:

- broadcast extras;
- public shared storage;
- MediaStore;
- filenames;
- logcat;
- notifications;
- ADB command arguments.

## Drafts And Retention

Panel drafts are app-private and keyed by a hash of request id plus nonce.
Draft filenames must not contain participant refs, stage names, or answers.

The panel clears the matching draft only after a terminal result is written.
The caller owns retention, export, encryption, and deletion policy for final
result files.

## Timing Metadata

The v1 timing object is intentionally low resolution and screen-level:

- screen entered;
- first answer-changing interaction;
- screen left;
- screen duration;
- answer-changing interaction count;
- validation-failure count.

Do not add gaze traces, controller pose, hand pose, raw event streams, or
high-frequency telemetry to this result envelope without a separate reviewed
data policy and schema.

## Logs And Public Docs

Safe public examples use fake ids, fake nonces, fake participant refs, and
synthetic answers. Before committing, check for local paths, raw participant
data, device serials, APKs, screenshots, log bundles, signing keys, and private
evidence artifacts. The staged scanner also checks for common credential and
private-controller leaks such as bearer tokens, API keys, client secrets,
refresh tokens, ADB keys, Cloudflare/Tailscale tokens, and WireGuard private
keys.

Run the staged public-artifact check before committing documentation, examples,
or lab automation output:

```powershell
python tools\check_public_artifacts.py
```

Use a full tracked-file sweep only when preparing a release hygiene pass:

```powershell
python tools\check_public_artifacts.py --all
```

## Curated Public Media

The committed public media lanes are `docs/media/*.mp4` for reviewed onboarding
clips and `docs/media/panel-renders/*.png` for generated host-side panel UI
renders used by the GitHub Pages guide. Before adding or replacing media there,
confirm:

- the clip is intentionally public onboarding media, not raw lab evidence;
- render PNGs were generated from the host-side panel renderer, not copied from
  ADB screenshots or headset capture output;
- it contains no participant data, device serials, private app lists, account
  names, raw logs, or local machine paths;
- it was reviewed as a final cropped/exported clip, not copied directly from a
  headset sweep directory;
- the original raw recording remains outside this repo.
