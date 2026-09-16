package com.vaadin.reverseproxy.it;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HelloHillaIT extends BaseIT {

    private static final String GREETING = "Welcome, friend!";
    private static final String[] STREAMED_GREETINGS = {
            "Hello friend!",
            "Ciao amico!",
            "Hallo Freund!",
            "Hei ystäväni!"
    };

    private static final LocatorAssertions.IsVisibleOptions STREAM_TIMEOUT =
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000);

    @ParameterizedTest
    @MethodSource("com.vaadin.reverseproxy.it.BaseIT#pushTransports")
    void buttonsTriggerNotifications(String transport) {
        navigate("hello-hilla");
        switchPushTransport(transport);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello").setExact(true)).click();
        assertThat(page.getByText(GREETING)).isVisible();

        // Everything above is a plain endpoint call over HTTP. What follows
        // needs Hilla's push channel, which is WebSocket-only, so it cannot run
        // against a proxy that has no upgrade support.
        assumeTrue(webSocketAvailable(),
                "Hilla reactive endpoints need WebSocket: FluxConnection "
                        + "subscribes with transport and fallbackTransport both "
                        + "set to 'websocket', and this scenario's proxy cannot "
                        + "upgrade a connection");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello in many languages")).click();
        for (String message : STREAMED_GREETINGS) {
            assertThat(page.getByText(message)).isVisible(STREAM_TIMEOUT);
        }
    }
}
