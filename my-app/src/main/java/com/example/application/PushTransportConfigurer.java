package com.example.application;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.PushConfiguration;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.ui.Transport;

/**
 * Overrides the transport that {@code @Push} on {@link Application} would
 * otherwise use, so a scenario can pick one through the
 * {@code VAADIN_PUSH_TRANSPORT} environment variable, e.g.
 * {@code VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS}.
 * <p>
 * The fallback transport is set to the same value on purpose. With the default
 * {@code LONG_POLLING} fallback, Atmosphere quietly degrades when a proxy
 * blocks the chosen transport, so a broken proxy configuration would still look
 * like a working one.
 * <p>
 * This only works because {@link Application} carries no {@code @Push}
 * annotation. With one present, the client-routing bootstrap applies it through
 * {@code AppShellRegistry.modifyPushConfiguration} in
 * {@code JavaScriptBootstrapHandler.createAndInitUI}, which runs *after*
 * {@code super.createAndInitUI} has fired {@code UIInitEvent} — overwriting the
 * transport set here with the annotation's value. Push is therefore enabled with
 * {@code vaadin.pushMode} in {@code application.properties} instead; see the
 * comment there.
 */
@Component
@ConditionalOnProperty("vaadin.push.transport")
public class PushTransportConfigurer implements VaadinServiceInitListener {

    private final Transport transport;

    public PushTransportConfigurer(
            @Value("${vaadin.push.transport}") String transport) {
        this.transport = parseTransport(transport);
    }

    /**
     * Fails with an actionable message rather than a bare
     * {@code IllegalArgumentException}. An unparseable value stops the Spring
     * context, the container exits, and the first visible symptom is the proxy
     * refusing to start with "host not found in upstream" — which says nothing
     * about the real cause.
     */
    private static Transport parseTransport(String name) {
        try {
            return Transport.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("VAADIN_PUSH_TRANSPORT is set to '"
                    + name + "', which this Flow build does not offer. Known transports: "
                    + Arrays.stream(Transport.values()).map(Enum::name)
                            .collect(Collectors.joining(", "))
                    + ". SERVER_SENT_EVENTS needs a Flow with the SSE push transport,"
                    + " e.g. --build-arg FLOW_VERSION=25.4.sse2-SNAPSHOT.", e);
        }
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent -> {
            PushConfiguration push = uiEvent.getUI().getPushConfiguration();
            push.setTransport(transport);
            push.setFallbackTransport(transport);
        });
    }
}
