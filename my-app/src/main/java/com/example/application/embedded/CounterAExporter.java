package com.example.application.embedded;

import com.vaadin.flow.component.WebComponentExporter;
import com.vaadin.flow.component.webcomponent.WebComponent;

/**
 * Exports {@link EmbeddedCounter} as {@code <rp-counter-a>}. There are two
 * exporters so that the embedding page loads two scripts in parallel, which is
 * what a load balancer can split across backends.
 */
public class CounterAExporter extends WebComponentExporter<EmbeddedCounter> {

    public CounterAExporter() {
        super("rp-counter-a");
    }

    @Override
    protected void configureInstance(WebComponent<EmbeddedCounter> webComponent,
            EmbeddedCounter component) {
        // Nothing to configure: the component works as it is.
    }
}
