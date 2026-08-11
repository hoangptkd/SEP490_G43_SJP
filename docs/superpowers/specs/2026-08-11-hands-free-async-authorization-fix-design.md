# Hands-Free Async Authorization Fix Design

## Context

The question-scoped hands-free endpoint returns a Spring MVC `Callable` because audio normalization, Gladia transcription, and optional Silero VAD processing can take several seconds. The initial `REQUEST` dispatch contains a valid Bearer token and passes the existing `CANDIDATE` authorization rules. When the callable completes, Spring MVC performs an `ASYNC` dispatch to write the response.

Spring Security 6.2 authorizes every dispatcher type by default. The application's `JwtAuthenticationFilter` extends `OncePerRequestFilter` and therefore skips async dispatches by default. In the observed failure, the initial authenticated request reaches the long-running workflow, but the completion dispatch has no usable authentication and is rejected with an empty `403 Forbidden` response.

Other candidate APIs remain healthy because they complete during the initial request dispatch.

## Decision

Permit `DispatcherType.ASYNC` in the HTTP authorization rules before the protected path matchers:

```java
.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
```

The original `REQUEST` dispatch remains protected by both:

- the `/candidate/**` rule requiring `ROLE_CANDIDATE`; and
- the controller-level `@PreAuthorize("hasRole('CANDIDATE')")` check before the controller creates the callable.

An async dispatch is a continuation created by the servlet container for an already accepted request. It is not a new public endpoint invocation initiated by the browser. Permitting this dispatcher type therefore allows response completion without weakening authorization of the original hands-free upload.

## Alternatives Considered

### Run JWT authentication again on async dispatch

Override `JwtAuthenticationFilter.shouldNotFilterAsyncDispatch()` so the JWT and current user are validated again. This preserves URL authorization on the completion dispatch, but repeats token parsing and a database lookup for every async completion. It also couples authentication more tightly to servlet dispatch mechanics.

### Make the controller synchronous

Return `ResponseEntity` directly instead of `Callable`. This avoids the second dispatch but keeps a servlet request thread occupied throughout FFmpeg, Gladia polling, and VAD processing. That reduces concurrency and discards the existing dedicated `ai-interview-io-*` executor design.

## Scope

The implementation changes only Spring Security dispatcher authorization and focused tests. It does not change:

- JWT validation or candidate role requirements on the initial request;
- multipart fields or idempotency semantics;
- Gladia, FFmpeg, or Silero VAD processing;
- transcript persistence or answer confirmation;
- scoring, including `overallScore`.

## Test Design

Add a Spring Security integration test that exercises the real two-dispatch lifecycle:

1. An authenticated candidate submits a valid multipart capture.
2. The initial request starts async processing.
3. `asyncDispatch` completes successfully with HTTP 200 and the provider-neutral response.
4. The same initial endpoint invocation without authentication remains rejected.

The existing controller multipart-binding test remains useful and unchanged. Run the focused security/controller tests, the full backend test suite, and the backend package build.

## Expected Result

The hands-free endpoint returns its normal JSON response instead of an empty 403 after processing. All initial candidate requests still require a current candidate JWT, and no audio, transcript, VAD, persistence, or scoring behavior changes.
