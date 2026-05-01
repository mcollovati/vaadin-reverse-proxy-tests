package com.vaadin.reverseproxy.it;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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
    @ValueSource(strings = {"WEBSOCKET_XHR", "WEBSOCKET"})
    void buttonsTriggerNotifications(String transport) {
        navigate("hello-flow");

        if (!"WEBSOCKET_XHR".equals(transport)) {
            switchPushTransport(transport);
        }

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello").setExact(true)).click();
        assertThat(page.getByText(GREETING)).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Say hello in many languages")).click();
        for (String message : STREAMED_GREETINGS) {
            assertThat(page.getByText(message)).isVisible(STREAM_TIMEOUT);
        }
    }
}
