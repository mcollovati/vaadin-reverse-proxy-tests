package com.vaadin.reverseproxy.it;

import java.util.List;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelloFlowIT extends BaseIT {

    // Strings produced by GreetingService for an empty name input.
    private static final String GREETING = "Welcome, friend!";
    private static final String[] STREAMED_GREETINGS = {
            "Hello friend!",
            "Ciao amico!",
            "Hallo Freund!",
            "Hei ystäväni!"
    };

    // Generous timeout for the streamed messages (1s delay each, 4 total).
    private static final LocatorAssertions.IsVisibleOptions STREAM_TIMEOUT =
            new LocatorAssertions.IsVisibleOptions().setTimeout(10_000);

    @ParameterizedTest
    @MethodSource("com.vaadin.reverseproxy.it.BaseIT#pushTransports")
    void buttonsTriggerNotifications(String transport) {
        navigate("hello-flow");
        switchPushTransport(transport);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello").setExact(true)).click();
        assertThat(page.getByText(GREETING)).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello in many languages")).click();
        for (String message : STREAMED_GREETINGS) {
            assertThat(page.getByText(message)).isVisible(STREAM_TIMEOUT);
        }

        if ("SERVER_SENT_EVENTS".equals(transport)) {
            // The messages arriving is not proof that they arrived over SSE:
            // Atmosphere would have fallen back to another transport if the
            // proxy blocked the event stream.
            List<String> eventSources = openedEventSources();
            assertTrue(
                    eventSources.stream()
                            .anyMatch(url -> url.contains("push")),
                    "Expected the client to open an SSE connection to the PUSH "
                            + "endpoint. EventSource URLs opened: "
                            + eventSources);
        }
    }
}
