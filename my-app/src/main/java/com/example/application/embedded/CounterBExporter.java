package com.example.application.embedded;

import com.vaadin.flow.component.WebComponentExporter;
import com.vaadin.flow.component.webcomponent.WebComponent;

/**
 * Exports {@link EmbeddedCounter} as {@code <rp-counter-b>}. There are two
 * exporters so that the embedding page loads two scripts in parallel, which is
 * what a load balancer can split across backends.
 */
public class CounterBExporter extends WebComponentExporter<EmbeddedCounter> {

    public CounterBExporter() {
        super("rp-counter-b");
    }

    @Override
    protected void configureInstance(WebComponent<EmbeddedCounter> webComponent,
            EmbeddedCounter component) {
        // Nothing to configure: the component works as it is.
    }
}
