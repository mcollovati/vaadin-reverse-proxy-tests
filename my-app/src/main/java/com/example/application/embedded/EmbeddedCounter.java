package com.example.application.embedded;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * A server-side counter embedded in a plain HTML page as a web component, for
 * {@code WebComponentsIT}. Every click is a round trip to the server, so the
 * count only goes up while the embedded UI's session works through the proxy.
 */
public class EmbeddedCounter extends Div {

    private final Span count = new Span("Clicked 0 times");
    private int clicks;

    public EmbeddedCounter() {
        Button button = new Button("Click me", event -> {
            clicks++;
            count.setText("Clicked " + clicks + " times");
        });
        button.addClassName("embedded-counter-button");
        count.addClassName("embedded-counter-count");
        add(button, count);
    }
}
