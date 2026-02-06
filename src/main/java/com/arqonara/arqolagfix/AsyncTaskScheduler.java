package com.arqonara.arqolagfix;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class AsyncTaskScheduler {
    private final ArqoLagfix plugin;
    private final ExecutorService executorService;
    
    public AsyncTaskScheduler(ArqoLagfix plugin) {
        this.plugin = plugin;
        // Thread pool with max 4 threads
        this.executorService = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private int counter = 0;
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ArqoLagfix-Worker-" + counter++);
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY); // Low priority
                return thread;
            }
        });
    }
    
    public void runAsync(Runnable task) {
        executorService.submit(task);
    }
    
    public <T> void runAsyncWithCallback(Callable<T> task, Consumer<T> callback) {
        executorService.submit(() -> {
            try {
                T result = task.call();
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            } catch (Exception e) {
                plugin.getLogger().warning("Async task error: " + e.getMessage());
            }
        });
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
