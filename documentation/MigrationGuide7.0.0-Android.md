# Migration Guide for Android SDK v7.0.0: RxJava → Kotlin Coroutines (suspend/Flow)

This guide describes how to update your code after the Polar BLE SDK migrated from RxJava to Kotlin Coroutines.

> **For agentic AI use:** This document can be used directly as an instruction set for an AI coding agent. Each section describes a concrete, mechanical transformation — the agent should apply the listed before/after patterns to all matching call sites in the codebase, update `build.gradle` dependencies as specified, and raise `minSdkVersion` to 33. No behavioural logic changes are required; the migration is purely a syntactic and structural adaptation.

---

## Summary of Changes

| RxJava type | Coroutines equivalent | Notes |
|---|---|---|
| `Single<T>` | `suspend fun`: T | Call from a coroutine scope |
| `Completable` | `suspend fun` (returns `Unit`) | Call from a coroutine scope |
| `Observable<T>` / `Flowable<T>` | `Flow<T>` | Collect from a coroutine scope |
| `Disposable` | `Job` / `CoroutineScope` | Cancel the scope or job |

---

## Gradle: Dependency Changes

Remove RxJava dependencies and add Kotlin Coroutines in your app's `build.gradle` (or `build.gradle.kts`):

```groovy
// Remove
implementation("io.reactivex.rxjava3:rxjava:3.x.x")
implementation("io.reactivex.rxjava3:rxandroid:3.x.x")
// Also remove if you used the RxJava adapter for Retrofit:
implementation("com.squareup.retrofit2:adapter-rxjava3:2.x.x")

// Add
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

// For tests, also add:
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

> **Note:** the SDK itself already bundles `kotlinx-coroutines-core`. You only need to add
> `kotlinx-coroutines-android` explicitly in your app to get Android-specific dispatchers
> (e.g. `Dispatchers.Main`), and `kotlinx-coroutines-test` for unit tests.

---

## Gradle: Minimum SDK Version Change

The SDK now requires **minSdk 33** (Android 13, Tiramisu). Update your app module's `build.gradle`:

```groovy
android {
    defaultConfig {
        // Before — could be lower, e.g.:
        // minSdkVersion 24

        // After:
        minSdkVersion 33   // Android 13 required
    }
}
```

If your app currently targets a `minSdk` lower than 33, you must either:
- Raise `minSdkVersion` to **33** and drop support for older Android versions, or
- Gate all SDK usage behind a runtime API-level check (`if (Build.VERSION.SDK_INT >= 33)`).

---

## API Method Changes

### Streaming / Observing data
Methods that previously returned `Observable<T>` or `Flowable<T>` now return `Flow<T>`.

**Before:**
```kotlin
api.startHrStreaming(deviceId, sensorSetting)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { hrData -> /* handle data */ },
        { error -> /* handle error */ },
        { /* completed */ }
    )
```

**After:**
```kotlin
lifecycleScope.launch {
    api.startHrStreaming(deviceId, sensorSetting)
        .catch { error -> /* handle error */ }
        .collect { hrData -> /* handle data */ }
}
```

---

### One-shot operations
Methods that previously returned `Single<T>` now are `suspend fun` returning `T` directly.

**Before:**
```kotlin
api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.HR)
    .subscribe(
        { settings -> /* use settings */ },
        { error -> /* handle error */ }
    )
```

**After:**
```kotlin
lifecycleScope.launch {
    try {
        val settings = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.HR)
        // use settings
    } catch (e: Exception) {
        // handle error
    }
}
```

---

### Fire-and-forget / completion operations
Methods that previously returned `Completable` now are `suspend fun` returning `Unit`.

**Before:**
```kotlin
api.setLocalTime(deviceId, LocalDateTime.now())
    .subscribe(
        { /* success */ },
        { error -> /* handle error */ }
    )
```

**After:**
```kotlin
lifecycleScope.launch {
    try {
        api.setLocalTime(deviceId, LocalDateTime.now())
        // success
    } catch (e: Exception) {
        // handle error
    }
}
```

---

### Device search
`searchForDevice()` previously returned `Flowable<PolarDeviceInfo>`, now returns `Flow<PolarDeviceInfo>`.

**Before:**
```kotlin
val disposable = api.searchForDevice()
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { deviceInfo -> /* found device */ },
        { error -> /* handle error */ }
    )
// Stop search:
disposable.dispose()
```

**After:**
```kotlin
val searchJob = lifecycleScope.launch {
    api.searchForDevice()
        .catch { error -> /* handle error */ }
        .collect { deviceInfo -> /* found device */ }
}
// Stop search:
searchJob.cancel()
```

---

### HR broadcast listening
`startListenForPolarHrBroadcasts()` previously returned `Observable<PolarHrBroadcastData>`, now returns `Flow<PolarHrBroadcastData>`.

**Before:**
```kotlin
val disposable = api.startListenForPolarHrBroadcasts(null)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe { hrBroadcastData -> /* handle */ }
```

**After:**
```kotlin
val job = lifecycleScope.launch {
    api.startListenForPolarHrBroadcasts(null)
        .collect { hrBroadcastData -> /* handle */ }
}
```

---

### Auto-connect
`autoConnectToDevice()` previously returned `Completable`, now is `suspend fun`.

**Before:**
```kotlin
api.autoConnectToDevice(-50, "180D", null)
    .subscribe(
        { /* connecting */ },
        { error -> /* handle error */ }
    )
```

**After:**
```kotlin
lifecycleScope.launch {
    try {
        api.autoConnectToDevice(-50, "180D", null)
    } catch (e: Exception) {
        // handle error
    }
}
```

---

### Waiting for connection
`waitForConnection()` is a new `suspend fun` (replaces connection callback patterns).

**After:**
```kotlin
lifecycleScope.launch {
    try {
        api.waitForConnection(deviceId)
        // device is now connected
    } catch (e: Exception) {
        // connection failed
    }
}
```

---

## Managing Lifecycle / Cancellation

**Before (RxJava):**
```kotlin
private val disposables = CompositeDisposable()

override fun onDestroy() {
    disposables.clear()
    super.onDestroy()
}
```

**After (Coroutines):**
```kotlin
// Use lifecycleScope (in Activity/Fragment) or viewModelScope (in ViewModel).
// Coroutines launched in these scopes are cancelled automatically on destroy.

lifecycleScope.launch {
    // coroutine here is cancelled when Activity is destroyed
}

// For manual control:
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
}
```

---

## Error Handling

**Before:**
```kotlin
observable.subscribe(
    { result -> /* success */ },
    { error -> /* error is Throwable */ }
)
```

**After:**
```kotlin
// For suspend functions:
try {
    val result = suspendFunction()
} catch (e: SomeException) {
    // handle
}

// For Flows:
flow
    .catch { e -> /* handle */ }
    .collect { result -> /* success */ }
```

---

## Thread Scheduling

RxJava required explicit scheduler declarations. Coroutines use dispatchers.

**Before:**
```kotlin
.subscribeOn(Schedulers.io())
.observeOn(AndroidSchedulers.mainThread())
```

**After:**
```kotlin
// Launch on Main dispatcher for UI updates (default in lifecycleScope):
lifecycleScope.launch(Dispatchers.Main) { ... }

// Switch to IO for background work inside:
withContext(Dispatchers.IO) { ... }

// Or use flowOn for Flow:
flow.flowOn(Dispatchers.IO)
    .collect { /* runs on caller's dispatcher */ }
```

> In most cases the SDK handles its own threading internally, so you only need to ensure UI updates happen on the main dispatcher.

---

## Complete Before/After Example

**Before:**
```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var api: PolarBleApi
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        )

        // subscribeOn(Schedulers.io()) moves BLE work off the main thread.
        // observeOn(AndroidSchedulers.mainThread()) brings results back to the UI thread.
        disposables += api.searchForDevice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ device ->
                // Runs on main thread — safe to update UI or call connectToDevice()
                api.connectToDevice(device.deviceId)
            }, { error ->
                Log.e("TAG", "Search error", error)
            })

        disposables += api.startHrStreaming(deviceId, settings)
            .subscribeOn(Schedulers.io())              // SDK/BLE work on IO thread
            .observeOn(AndroidSchedulers.mainThread()) // emit results on main thread
            .subscribe({ hrData ->
                // Runs on main thread — safe to update UI
                updateUi(hrData)
            }, { error ->
                Log.e("TAG", "Stream error", error)
            })
    }

    override fun onDestroy() {
        disposables.clear()
        api.shutDown()
        super.onDestroy()
    }
}
```

**After:**
```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var api: PolarBleApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        )

        // lifecycleScope uses Dispatchers.Main by default, so the coroutine starts on the main thread.
        // The SDK internally shifts BLE/IO work to a background thread via its own dispatchers —
        // you do NOT need to add flowOn(Dispatchers.IO) or withContext(Dispatchers.IO) for SDK calls.
        lifecycleScope.launch {
            api.searchForDevice()
                .catch { error -> Log.e("TAG", "Search error", error) }
                .collect { device ->
                    // collect{} resumes on Dispatchers.Main (inherited from lifecycleScope)
                    // — safe to update UI or call connectToDevice() here
                    api.connectToDevice(device.deviceId)
                }
        }

        lifecycleScope.launch {
            // Again: SDK does the BLE work on a background thread internally.
            // flowOn() is NOT needed here unless you do additional CPU/IO work yourself
            // before passing the data to the UI.
            api.startHrStreaming(deviceId, settings)
                .catch { error -> Log.e("TAG", "Stream error", error) }
                // If you need to do heavy processing on each emission before updating the UI,
                // add .flowOn(Dispatchers.Default) here to move that work off the main thread:
                // .map { raw -> heavyProcessing(raw) }.flowOn(Dispatchers.Default)
                .collect { hrData ->
                    // collect{} runs on Dispatchers.Main — safe to update UI directly
                    updateUi(hrData)
                }
        }
    }

    override fun onDestroy() {
        api.shutDown()
        super.onDestroy()
        // lifecycleScope is cancelled automatically — no manual dispose needed
    }
}
```

### Key threading rules at a glance

| What you want | How |
|---|---|
| SDK/BLE work off main thread | Handled internally by the SDK — nothing to do |
| Your own CPU/IO work off main thread | `withContext(Dispatchers.IO/Default) { }` or `.flowOn(Dispatchers.IO)` on the Flow |
| UI update on main thread | Use `lifecycleScope` (already on Main) or `withContext(Dispatchers.Main) { }` |
