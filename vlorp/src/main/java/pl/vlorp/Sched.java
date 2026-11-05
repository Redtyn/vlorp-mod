package pl.vlorp;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Sched {
    private static class Task { int left; Runnable r; Task(int l, Runnable r) { left = l; this.r = r; } }
    private static final List<Task> TASKS = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<Runnable> due = new ArrayList<>();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (--t.left <= 0) { due.add(t.r); it.remove(); }
            }
            due.forEach(Runnable::run);
        });
    }

    public static void after(int ticks, Runnable r) { TASKS.add(new Task(ticks, r)); }
}
