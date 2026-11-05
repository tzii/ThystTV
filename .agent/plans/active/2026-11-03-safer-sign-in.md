# Safer reauthorization and cancellable sign-in requests

## Goal

Keep the existing login when reauthorization is cancelled, restart only when the account
changes, and make sign-in requests cancel at the HTTP layer with an elapsed-time limit.

## Non-goals

Converting other Helix and GQL calls; changing ordinary Log in and Log out.

## Current context

Branch `feat/safer-sign-in`, on `feat/auth-http-cancellation`. The clip flow depends on it.

## Files likely involved

`util/CancellableHttp.kt`, `repository/AuthRepository.kt`, `model/id/TokenResponse.kt`,
`ui/login/Reauthorization.kt`, `ui/login/LoginActivity.kt`, `ui/main/MainActivity.kt`.

## Risks

Risk level: medium. Token storage and revocation order matter. Tokens are never logged.

## Human approval

Requested directly by the maintainer. Real sign-ins are the maintainer's QA.

## Implementation steps

1. `CancellableHttp` for OkHttp, Cronet and HttpEngine with cancellation and a timeout.
2. `AuthRepository` on top of it.
3. `Reauthorization.plan` and `commit`: tokens change only after the new one validates.
4. `LoginActivity` modes that preserve the sign-in; `reauthorizeResultLauncher`.

## Verification

- [x] Focused tests: `CancellableHttpTest`, `ReauthorizationTest`, device code tests
- [x] assembleDebug, test and lintDebug in the combined QA build
- [ ] Device QA: docs/MANUAL_QA.md > Safer sign-in

Human QA completed: none yet.

## Progress log

- 2026-11-03: Implemented; awaiting the maintainer's device QA.

## Decisions

`HttpTimeoutException` is an `IOException`, because `TimeoutCancellationException` is a
`CancellationException` and would otherwise look like the caller cancelling.
A TV clip grant is stored apart from `C.TOKEN`, since `MainViewModel.validate` treats a
401 on the main token as expiry.

## Final PR summary draft

Reauthorizing keeps the current sign-in until the new one is confirmed, and sign-in
requests now cancel the underlying HTTP call and time out after 20 seconds.
