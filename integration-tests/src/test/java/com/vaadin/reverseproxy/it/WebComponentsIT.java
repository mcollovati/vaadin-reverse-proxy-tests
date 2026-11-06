package com.vaadin.reverseproxy.it;

import java.net.URI;
import java.util.List;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.HttpHeader;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exported web components embedded in a plain page behind the proxy.
 * <p>
 * The embedding page ({@code embed.html}, served by a filter in
 * {@code Application}) loads two web component scripts in parallel, with no
 * session yet. Since Flow 25.4 those script requests, and favicon requests, no
 * longer create a session (vaadin/flow#26304, #26532, #26535), which removed a
 * race in Flow itself: two parallel requests each created a session, and one
 * of the components then answered "Session Expired". What is left of that race
 * is the proxy's: a load balancer that pins the session with its own cookie
 * can still send the two cookie-less requests to different backends. A proxy
 * that rewrites cookie paths can also drop the session cookie that the
 * embedded components need.
 */
class WebComponentsIT extends BaseIT {

    private static final List<String> SCRIPTS = List
            .of("web-component/rp-counter-a.js", "web-component/rp-counter-b.js");

    @Test
    void scriptsAndFaviconCreateNoSession() {
        // A new browser context has no cookies yet, so these are the requests
        // a first visit to the embedding page makes.
        for (String script : SCRIPTS) {
            APIResponse response = page.request().get(baseUrl() + script);
            assertEquals(200, response.status(), script + " did not load");
            assertNoSessionCookie(script, response);
        }
        // The browser asks for the favicon at the root of the origin, not under
        // the scenario's prefix, because embed.html names no icon. Under a
        // /ui/* servlet mapping, <base>/favicon.ico would just be a Vaadin
        // route, which rightly gets a session.
        String favicon = URI.create(baseUrl()).resolve("/favicon.ico")
                .toString();
        assertNoSessionCookie(favicon, page.request().get(favicon));
    }

    @Test
    void embeddedComponentsWork() {
        navigate("embed.html");

        Locator counterA = page.locator("#counter-a");
        Locator counterB = page.locator("#counter-b");

        // Both components have rendered and handle a click. Locators reach
        // into open shadow roots, so this works however the component is
        // attached.
        click(counterA);
        assertThat(counterA.locator(".embedded-counter-count"))
                .hasText("Clicked 1 times");
        click(counterB);
        assertThat(counterB.locator(".embedded-counter-count"))
                .hasText("Clicked 1 times");
        click(counterA);
        assertThat(counterA.locator(".embedded-counter-count"))
                .hasText("Clicked 2 times");

        assertThat(page.getByText("Session Expired")).hasCount(0);
    }

    private static void click(Locator counter) {
        counter.locator(".embedded-counter-button").click();
    }

    /**
     * Fails if the response sets the servlet session cookie. HAProxy's
     * {@code load-balancer-cookie-prefix} renames nothing, it only prefixes the
     * value, so the name check covers it too. The proxies' own affinity
     * cookies ({@code ROUTEID}) are expected and allowed.
     */
    private static void assertNoSessionCookie(String what,
            APIResponse response) {
        List<String> sessionCookies = response.headersArray().stream()
                .filter(header -> header.name.equalsIgnoreCase("set-cookie"))
                .map(header -> header.value)
                .filter(value -> value.startsWith("JSESSIONID="))
                .toList();
        assertTrue(sessionCookies.isEmpty(), what
                + " created a session through the proxy: " + sessionCookies
                + ". All headers: " + headers(response));
    }

    private static String headers(APIResponse response) {
        StringBuilder all = new StringBuilder();
        for (HttpHeader header : response.headersArray()) {
            all.append(header.name).append(": ").append(header.value)
                    .append("; ");
        }
        return all.toString();
    }
}
