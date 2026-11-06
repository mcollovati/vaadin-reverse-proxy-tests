package com.example.application.views.pushcounter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Pushes a counter to the browser every 500 ms, with no user input, for
 * {@code PushReconnectIT}.
 * <p>
 * The counter only moves while the push connection works, so a test can tell
 * whether push recovered after the proxy dropped the connection, without
 * clicking anything that would itself trigger a reconnect.
 */
@PageTitle("Push Counter")
@Route("push-counter")
public class PushCounterView extends VerticalLayout {

    private static final ScheduledExecutorService TICKER = Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "push-counter");
                thread.setDaemon(true);
                return thread;
            });

    private final Span counter = new Span("0");
    private int value;
    private ScheduledFuture<?> ticks;

    public PushCounterView() {
        counter.setId("push-counter");
        add(new Paragraph("Pushed ticks:"), counter);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        UI ui = event.getUI();
        ticks = TICKER.scheduleAtFixedRate(() -> ui.access(() -> {
            value++;
            counter.setText(String.valueOf(value));
        }), 500, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (ticks != null) {
            ticks.cancel(false);
            ticks = null;
        }
    }
}
