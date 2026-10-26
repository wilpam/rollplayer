package dev.infernity.rollplayer;

import dev.infernity.rollplayer.listeners.commands.*;
import dev.infernity.rollplayer.listeners.interfaces.CommandDataCapable;
import dev.infernity.rollplayer.listeners.interfaces.MinuteTicking;
import dev.infernity.rollplayer.listeners.managers.MetricsManager;
import dev.infernity.rollplayer.listeners.managers.PaginationManager;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Listeners {
    public final List<EventListener> listeners = new ArrayList<>();
    public final ArrayList<CommandData> commands = new ArrayList<>();
    @SuppressWarnings("FieldCanBeLocal")
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    public final ScheduledFuture<?> minuteTicking;
    public long tick;

    public Listeners(){
        var pm = new PaginationManager();
        this.listeners.addAll(List.of(new EightBall(), new Choose(), new TicTacToe(), new Info(), new Roll(), new RollHelp(), /*new Settings(),*/ new RandomCommand()));
        this.listeners.addAll(List.of(pm, new MetricsManager()));
        // this.listeners.addAll(List.of(new Test(), new TestArguments()));
        for (EventListener listener : listeners) {
            if (listener instanceof CommandDataCapable capable) {
                commands.addAll(capable.getCommandData());
            }
        }
        Resources.getInstance().setPaginationManager(pm);

        minuteTicking = scheduler.scheduleAtFixedRate(this::minuteTick, 60, 60, SECONDS);
    }

    public void minuteTick(){
        tick++;
        for (EventListener listener : listeners) {
            if (listener instanceof MinuteTicking ticking) {
                ticking.minuteTick(tick);
            }
        }
    }
}
