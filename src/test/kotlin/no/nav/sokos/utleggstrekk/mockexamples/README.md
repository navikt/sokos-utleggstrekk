# mockexamples

This package contains tests that **deliberately fail**. They demonstrate two distinct
mocking pitfalls that exist in this codebase and explain why certain logger-verification
tests cannot be written naively.

None of these files contain production logic. They are living documentation — executable
proofs of subtle JVM and MockK behaviours.

---

## The two problems

### Problem 1 — `private val logger` is frozen at class-load time

Several files in the codebase declare their logger at **file scope**:

```kotlin
// SkeClient.kt, BehandleTrekkService.kt, Repository.kt, etc.
private val logger = KotlinLogging.logger { }

class SkeClient(...) {
    fun hentUtleggstrekkFraSekvensnr(...) {
        logger.info { "Henter utleggstrekk..." }  // uses the file-level val
    }
}
```

`private val logger` at file scope compiles into the **static initialiser** of the
generated `SkeClientKt` class. The JVM runs a static initialiser exactly **once per
ClassLoader** — the first time anything in that file is referenced, whether that is
constructing `SkeClient(...)` or calling any method on it.

Whatever `KotlinLogging.logger {}` returns at that exact moment is stored in the val
**permanently**. No subsequent `mockkObject(KotlinLogging)` call can replace an
already-stored reference.

**The consequence:**

```
Test A — constructs SkeClient() with no mock active
          → JVM loads SkeClientKt static initialiser
          → private val logger = KotlinLogging.logger {}  ← REAL logger captured, forever

Test B — calls mockkObject(KotlinLogging)
          → installs the mock
          → constructs SkeClient() again
          → calls hentUtleggstrekkFraSekvensnr()
          → logger.info { ... }  ← goes to the FROZEN val = REAL logger
          → verify(mockLogger.info(...))  ← FAILS: mock was never called
```

**The fix:** use a function instead of a val, so the logger is resolved at call-time:

```kotlin
// Before (broken for testing):
private val logger = KotlinLogging.logger { }

// After (testable):
private fun logger() = KotlinLogging.logger { }
```

With `fun logger()`, every call inside `hentUtleggstrekkFraSekvensnr` goes through
`KotlinLogging` at runtime, so `mockkObject(KotlinLogging)` intercepts it correctly.

---

### Problem 2 — `clearAllMocks()` contaminates other specs

`clearAllMocks()` wipes the recorded calls **and stubs** of every MockK mock in the
JVM, regardless of which spec created them. When called in one spec's `afterSpec` or
`afterTest`, it silently removes stubs that other specs depend on, causing mysterious
failures that only appear when running the full suite — not in isolation.

```
SpecA.beforeSpec  → every { serviceA.call() } returns "answer"
SpecA.test 1      → serviceA.call() == "answer"  ✅

SpecB.afterSpec   → clearAllMocks()              ← wipes SpecA's stubs too

SpecA.test 2      → serviceA.call()              ← no stub → MockKException ❌
```

**The fix:** use `clearMocks(mock1, mock2, ...)` which only clears the specific
instances you own:

```kotlin
// Dangerous — affects the whole JVM:
afterSpec { clearAllMocks() }

// Safe — only clears what this spec created:
afterSpec { clearMocks(slackService, mockTokenProvider) }
```

---

## Files in this package

### `SkeClientValLoggerDemoTest.kt`

**Demonstrates Problem 1 using `SkeClient` directly, with `beforeSpec`/`afterSpec` for mock setup.**

Contains two ordered specs:

**`SkeClientFreezerSpec` (order 1)** — no `KotlinLogging` mock. Calls `SkeClient`
normally. Side effect: loads `SkeClientKt`'s static initialiser and freezes
`private val logger` to the real logger permanently.

**`SkeClientVictimSpec` (order 2)** — installs `mockkObject(KotlinLogging)` in
`beforeSpec` and tears it down in `afterSpec`, exactly as real tests do. `afterTest`
clears recorded calls between tests. Every logger verify still fails because the val
was already frozen by the freezer spec:

| Test | Expected result | Why |
|------|----------------|-----|
| `mocks logger in beforeSpec — val already frozen, mockLogger never called` | ❌ FAILS | `beforeSpec` ran after the val was frozen; the mock is active for new calls but the frozen val bypasses it |
| `new SkeClient() instance does not re-evaluate the file-level val — still frozen` | ❌ FAILS | File-level val is shared across all instances; constructing a new `SkeClient` does not re-run the static initialiser |

---

### `SkeClientEachTestOwnMockDemoTest.kt`

**Demonstrates that pointing the stub at a different mock per test is also insufficient.**

Uses `beforeSpec`/`afterSpec` for `mockkObject`/`unmockkObject`. Two separate mock
instances (`mockLoggerA`, `mockLoggerB`) are declared at spec level. The stub is
re-pointed at the appropriate mock in `beforeTest`. No `try/finally` anywhere.

| Test | In isolation | In full suite | Why |
|------|-------------|---------------|-----|
| `test 1` — stub points to mockLoggerA | ✅ PASSES | ❌ FAILS | In isolation: `SkeClientKt` loads here while mockLoggerA is the active stub → val captures mockLoggerA. In full suite: `SkeClientFreezerSpec` ran first → val frozen to the real logger |
| `test 2` — stub re-pointed to mockLoggerB | ❌ FAILS | ❌ FAILS | Val holds whatever was captured at first class-load; mockLoggerB is a different object and was never stored in `SkeClientKt.logger` |

The second test **always** fails: stub reinstallation intercepts future
`KotlinLogging.logger {}` calls but cannot update a reference already stored in a val.

---

### `CrossSpecValLoggerFreezeLocalObjectTest.kt`

**Demonstrates Problem 1 with a self-contained `object` and `class`, no external
dependencies.**

Contains four specs in one file, ordered 1–4:

| Spec | Test | Expected result | Why |
|------|------|----------------|-----|
| `ValLoggerFreezerSpec` (order 1) | calls `LocalService.doWork()` with no mock | ✅ PASSES | First access to this file; static initialiser runs; **both** `logger` and `classLogger` are frozen to the real logger |
| `ValLoggerVictimSpec` (order 2) | mock installed in `beforeSpec`, `doWork()` called | ❌ FAILS | Val was frozen in order 1 |
| `ValLoggerVictimSpec` (order 2) | fresh `KotlinLogging.logger {}` call with mock active | ✅ PASSES | Proves `mockkObject` works for new calls — the problem is only the frozen val |
| `ClassFreezerSpec` (order 3) | constructs `ClassBasedService()` with no mock | ✅ PASSES | `classLogger` was already frozen in order 1 (same file); this confirms construction alone is sufficient to trigger the static initialiser if the file hadn't already been loaded |
| `ClassVictimSpec` (order 4) | new instance, mock installed, `doWork()` called | ❌ FAILS | Creating a new instance does **not** re-run the file-level val initialiser |
| `ClassVictimSpec` (order 4) | fresh logger call with mock active | ✅ PASSES | Same contrast as order 2 |

**Important:** because `logger` and `classLogger` are in the **same file**, they are
both frozen at the same moment — when `ValLoggerFreezerSpec` first loads the file.
`ClassFreezerSpec` does not trigger a second freeze; that already happened.

---

### `CrossSpecValLoggerFreezeTest.kt`

**Demonstrates Problem 1 using the real `Repository` and `DBListener`.**

Two specs using the actual database:

| Spec | Test | Expected result | Why |
|------|------|----------------|-----|
| `CrossSpecFreezerSpec` (order 1) | accesses `DBListener.repository` with no mock | ✅ PASSES | `DBListener.repository` is a `lazy`; first access triggers it, loads `Repository.kt`, freezes `private val logger` to the real logger |
| `CrossSpecVictimSpec` (order 2) | installs mock in `beforeSpec`, calls `deleteOldData()` | ❌ FAILS | Repository's lazy is already initialised; the same instance is returned; its frozen val bypasses the mock |

This shows the interaction between Kotlin's `lazy` delegation and class-load-time
val initialisation. The lazy only fires once — whichever spec accesses
`DBListener.repository` first determines what `private val logger` captures, for
every subsequent spec in that JVM run.

---

### `RepositoryLoggingDemoTest.kt`

**Demonstrates Problem 1 in isolation — works correctly even without other specs
running first. Uses `beforeSpec`/`afterSpec`, not `try/finally`.**

`mockkObject(KotlinLogging)` is installed in the spec's own `beforeSpec`. But
`DBListenerWithEagerRepository` is registered as an **extension**, and the extension's
`beforeSpec` fires before the spec's own `beforeSpec` — loading `Repository.kt` and
freezing the val before the spec's `beforeSpec` even runs:

```
extension.beforeSpec  →  spec.beforeSpec  →  test body  →  spec.afterSpec  →  extension.afterSpec
     ↑                        ↑
  val frozen here         too late
```

| Test | Expected result | Why |
|------|----------------|-----|
| `Repository val logger is NOT the mock we install in this test` | ❌ FAILS | Extension's `beforeSpec` froze the val; spec's `beforeSpec` installs `mockkObject` too late |

The test asserts that `KotlinLogging.logger {}` called directly in the test body
returns the mock — confirming `mockkObject` is wired correctly. The only issue is the
already-frozen val in `Repository`.

---

### `DBListenerWithEagerRepository.kt`

**A test infrastructure object, not a test spec.**

Identical to `DBListener` except its `beforeSpec` calls `repository.deleteOldData()`
before any test body runs. This is the single line that makes `RepositoryLoggingDemoTest`
fail for the correct reason even in isolation:

```kotlin
override suspend fun beforeSpec(spec: Spec) {
    mockkObject(PropertiesConfig)
    every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")

    // ← This is the only difference from DBListener.
    // Triggers DBListenerWithEagerRepository.repository lazy → loads Repository.kt
    // → private val logger captured = REAL logger (no mockkObject(KotlinLogging) yet)
    repository.deleteOldData()
}
```

---

## How `mockkObject` works — and why it cannot fix the frozen val

Understanding why `mockkObject(KotlinLogging)` cannot rescue an already-frozen val
requires understanding what `mockkObject` actually does at the bytecode level.

### What `mockkObject` does

A Kotlin `object` compiles to a JVM class with a single static instance held in a
field named `INSTANCE`. `mockkObject` uses a **bytecode instrumentation agent** (via
`ByteBuddy` or `Instrumentation`) to redefine the class at runtime, wrapping every
method call with a MockK interceptor. The `INSTANCE` reference itself is NOT replaced
— the same singleton object exists, but its methods now route through MockK.

```
Before mockkObject(KotlinLogging):
  KotlinLogging.INSTANCE.logger { } → real KotlinLogging implementation → real KLogger

After mockkObject(KotlinLogging):
  KotlinLogging.INSTANCE.logger { } → MockK interceptor → returns whatever you stubbed
```

So `mockkObject` intercepts **future calls** to the object's methods. It does not
change what has already been returned and stored elsewhere.

### Why the frozen val is immune

When `private val logger = KotlinLogging.logger { }` was evaluated, the returned
`KLogger` instance was stored directly in a field of the `SkeClientKt` class:

```
SkeClientKt.logger  →  (points to) → real KLogger instance
```

`mockkObject(KotlinLogging)` installed after this point intercepts the **method**
`KotlinLogging.logger {}` — but it cannot go back and change what was already
written into `SkeClientKt.logger`. That field still points to the real `KLogger`.

When `SkeClient.hentUtleggstrekkFraSekvensnr()` runs:

```kotlin
logger.info { "Henter utleggstrekk..." }
//  ↑
//  reads SkeClientKt.logger at runtime
//  → real KLogger (unchanged)
//  → NOT the mock
```

The mock is never involved. This is why `verify { mockLogger.info(...) }` fails.

### Why `fun logger()` works

```kotlin
private fun logger() = KotlinLogging.logger { }
```

Now there is no stored field. Every call site reads through `KotlinLogging.logger {}`
at runtime:

```kotlin
logger().info { "Henter utleggstrekk..." }
// ↑ calls KotlinLogging.logger {}
//   → MockK interceptor (if mockkObject is active) → returns mockLogger
//   → mockLogger.info { ... }  ← mock receives the call ✅
```

The cost is a `KotlinLogging.logger {}` lookup per call — negligible in practice.

---

### The two different `mockkObject` uses in this codebase

`mockkObject` is used for two distinct purposes here, and they behave differently:

#### `mockkObject(KotlinLogging)` — intercepting a factory method

`KotlinLogging` is a Kotlin `object` whose `logger {}` method is a factory that
creates and returns `KLogger` instances. `mockkObject` intercepts this factory so that
`every { KotlinLogging.logger(any<() -> Unit>()) } returns myMock` makes all
subsequent calls to `KotlinLogging.logger {}` return `myMock`.

This **only works for loggers created after `mockkObject` is called**. Any `val logger`
that was already assigned is unaffected — it holds a direct reference to whatever
`KotlinLogging.logger {}` returned before the intercept was installed.

#### `mockkObject(PropertiesConfig)` — stubbing a singleton's property

`PropertiesConfig` is a Kotlin `object` with:

```kotlin
object PropertiesConfig {
    lateinit var config: ApplicationConfig
        private set
    // ...
}
```

`mockkObject(PropertiesConfig)` + `every { PropertiesConfig.config } returns myConfig`
stubs the `config` getter so it returns the test `ApplicationConfig` instead of
requiring `PropertiesConfig.config` to have been initialised via the real startup path.

This works reliably because `PropertiesConfig.config` is accessed through the getter
**at call-time** — it is never stored in a `val` elsewhere in production code. Every
time something reads `PropertiesConfig.config`, it calls the (now-mocked) getter and
gets the test config. There is no frozen val to bypass.

#### Summary of the difference

| `mockkObject` target | What it intercepts | Frozen val problem? |
|----------------------|--------------------|---------------------|
| `KotlinLogging` | `logger {}` factory method | **Yes** — if `val logger` was assigned before `mockkObject`, the val holds the real logger and is never updated |
| `PropertiesConfig` | `config` property getter | **No** — `config` is always read via the getter at call-time, never stored in a `val` by callers |

---

### `unmockkObject` — `beforeSpec`/`afterSpec` vs `try/finally`

`unmockkObject(KotlinLogging)` reverses the bytecode instrumentation — the class is
redefined back to its original form, and `KotlinLogging.logger {}` returns real loggers
again. It must always be called after the mock is no longer needed to avoid leaking the
instrumented class into subsequent specs.

There are two patterns for ensuring this happens:

#### Pattern 1 — `beforeSpec` / `afterSpec` (preferred, used in these demos)

```kotlin
beforeSpec {
    mockkObject(KotlinLogging)
    every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
}

afterSpec {
    unmockkObject(KotlinLogging)
    clearMocks(mockLogger)
}

test("...") {
    // clean test body — no teardown boilerplate
    verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
}
```

`afterSpec` is guaranteed to run even if a test throws, so `unmockkObject` is always
called. This is the same guarantee as `finally` but expressed as a Kotest lifecycle
hook instead of a language construct.

**All demo tests in this package use this pattern.**

#### Pattern 2 — `try/finally` (for mocks scoped to a single test body)

```kotlin
test("...") {
    val mockLogger = mockk<KLogger>(...)
    mockkObject(KotlinLogging)
    every { KotlinLogging.logger(any<() -> Unit>()) } returns mockLogger
    try {
        verify(exactly = 1) { mockLogger.info(any<() -> Unit>()) }
    } finally {
        unmockkObject(KotlinLogging)
        clearMocks(mockLogger)
    }
}
```

Use this only when the mock is intentionally scoped to one test and you do not want it
to be active for sibling tests in the same spec. In practice, `beforeSpec`/`afterSpec`
is almost always cleaner.

#### What `unmockkObject` does NOT do

`unmockkObject` does **not** un-freeze any `val` that captured a logger while the mock
was active. If a class was loaded while `mockkObject(KotlinLogging)` was active, its
`val logger` holds a reference to the mock object. After `unmockkObject` the object's
instrumentation is removed, but the `val` still points to the same now-uninstrumented
mock — which may return default values or throw, depending on its recorded stubs. This
is another reason why `private val logger` at file scope is fragile for testing: its
state depends on the exact timing of `mockkObject` / `unmockkObject` calls across all
specs in the JVM lifetime.

---

## Execution order within a spec vs across specs

Kotest's execution order for a single spec:

```
extensions.beforeSpec  →  spec.beforeSpec  →  beforeTest  →  test body  →  afterTest  →  spec.afterSpec  →  extensions.afterSpec
```

`DBListenerWithEagerRepository` is registered as an extension, so its `beforeSpec`
fires **before** the spec's own `beforeSpec` and **before** any test body. This means
any `mockkObject(KotlinLogging)` call inside a test body is always too late to
influence a val that the extension loaded.

Across specs, Kotest runs all tests of one spec before starting the next. The
`@Order` annotation controls which spec runs first. Once a class is loaded by the JVM,
it stays loaded — its static initialisers never run again in the same JVM process.

---

## Which production files are affected

File-level `private val logger` (affected by Problem 1 — logger frozen at class-load):

| File | Note |
|------|------|
| `SkeClient.kt` | Demonstrated by `SkeClientValLoggerDemoTest` |
| `Repository.kt` | Demonstrated by `RepositoryLoggingDemoTest` and `CrossSpecValLoggerFreezeTest` |
| `BehandleTrekkService.kt` | Same pattern |
| `UtleggstrekkScheduler.kt` | Same pattern |
| `SyntetiskId.kt` | Same pattern |
| `CommonConfig.kt` | Same pattern |
| `SecurityConfig.kt` | Same pattern |

Member-level `private val logger` inside a class or object body (safe — not affected):

| File | Note |
|------|------|
| `UtleggsTrekkService.kt` | Inside `object` body — initialised when the object is first accessed |
| `JmsListenerService.kt` | Inside class body |
| `JmsProducerService.kt` | Inside class body |
| `MaskinportenAccessTokenClient.kt` | Inside class body |
| `PostgresDataSource.kt` | Inside `object` body |

