# Scheduler

`SchedulerService` (a `Service`) wraps `BukkitScheduler` with intention-revealing methods and provides `Executor` instances for integration with `CompletableFuture`.

## Methods

| Method | Runs on | When |
|---|---|---|
| `sync(Runnable)` | Main thread | Next tick |
| `async(Runnable)` | Async thread | Immediately |
| `later(Runnable, ticks)` | Main thread | After delay |
| `laterAsync(Runnable, ticks)` | Async thread | After delay |
| `repeating(Runnable, delay, period)` | Main thread | Every `period` ticks |
| `repeatingAsync(Runnable, delay, period)` | Async thread | Every `period` ticks |
| `asyncThenSync(Supplier, Consumer)` | Async → Main | Immediately |

All methods return `BukkitTask` (except `asyncThenSync`), which can be used to cancel the task.

## Examples

```java
SchedulerService scheduler = service(SchedulerService.class);

// Run on main thread next tick
scheduler.sync(() -> player.sendMessage("Hello from main thread!"));

// Run asynchronously
scheduler.async(() -> {
    // heavy I/O — do NOT touch Bukkit API here
    String data = fetchFromRemote();
    scheduler.sync(() -> player.sendMessage(data));   // back on main thread
});

// Delay (20 ticks = 1 second)
scheduler.later(() -> player.sendMessage("This was delayed by 5 seconds!"), 20L * 5);

// Repeating task — auto-save every 5 minutes
BukkitTask task = scheduler.repeating(() -> dataStore.save(), 0L, 20L * 60 * 5);

// Cancel when done
task.cancel();
```

## asyncThenSync bridge

The most common async pattern: fetch something off-thread, then apply the result on the main thread:

```java
scheduler.asyncThenSync(
        () -> database.loadPlayer(uuid),     // runs async
        record -> player.sendMessage(        // runs sync
                ColorUtils.parse("<gold>Coins: " + record.coins())));
```

## Executors for CompletableFuture

The database layer uses these executors internally. You can use them directly with `CompletableFuture.supplyAsync`:

```java
CompletableFuture.supplyAsync(() -> heavyComputation(), scheduler.asyncExecutor())
        .thenAcceptAsync(result -> updateHud(player, result), scheduler.mainThreadExecutor());
```

```java
scheduler.asyncExecutor()       // Executor → scheduler.async(...)
scheduler.mainThreadExecutor()  // Executor → scheduler.sync(...)
```

## Tick reference

| Duration | Ticks |
|---|---|
| 1 second | 20 |
| 1 minute | 1200 |
| 5 minutes | 6000 |
| 1 hour | 72000 |
