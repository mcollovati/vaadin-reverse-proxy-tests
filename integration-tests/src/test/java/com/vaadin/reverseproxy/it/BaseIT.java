package com.vaadin.reverseproxy.it;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseIT {

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(
                System.getProperty("pw.headless", "true"));
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void newContext() {
        NewContextOptions options = new NewContextOptions();
        // HTTPS scenarios use a self-signed cert; trust it during tests.
        if (baseUrl().startsWith("https://")) {
            options.setIgnoreHTTPSErrors(true);
        }
        context = browser.newContext(options);
        page = context.newPage();
        // Runs before any application script, so it catches the push
        // connection Atmosphere opens during the initial page load.
        page.addInitScript("""
                window.__sseUrls = [];
                const NativeEventSource = window.EventSource;
                window.EventSource = function (url, config) {
                    window.__sseUrls.push(String(url));
                    return new NativeEventSource(url, config);
                };
                window.EventSource.prototype = NativeEventSource.prototype;
                window.EventSource.CONNECTING = NativeEventSource.CONNECTING;
                window.EventSource.OPEN = NativeEventSource.OPEN;
                window.EventSource.CLOSED = NativeEventSource.CLOSED;
                """);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    protected String baseUrl() {
        String url = System.getProperty("app.base.url",
                "http://localhost:8080/");
        return url.endsWith("/") ? url : url + "/";
    }

    /**
     * Navigates to {@code relPath} resolved against {@link #baseUrl()}.
     * {@code relPath} must NOT start with {@code /} so that the proxy prefix
     * (e.g. {@code /app/} or {@code /ui/}) is preserved.
     */
    protected void navigate(String relPath) {
        if (relPath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "relPath must not start with '/': " + relPath);
        }
        page.navigate(baseUrl() + relPath);
    }

    /**
     * The push transports the transport-parameterized tests run through, taken
     * from the comma-separated {@code it.push.transports} system property.
     * <p>
     * The default covers the two WebSocket flavours. Scenarios whose proxy
     * deliberately cannot upgrade a connection (the {@code *-sse} ones) pass
     * {@code SERVER_SENT_EVENTS} instead, and a run against an SSE-capable
     * build can ask for all three.
     */
    static Stream<String> pushTransports() {
        return Arrays
                .stream(System
                        .getProperty("it.push.transports",
                                "WEBSOCKET_XHR,WEBSOCKET")
                        .split(","))
                .map(String::trim).filter(name -> !name.isEmpty());
    }

    /**
     * Whether the scenario's proxy can carry a WebSocket connection, from the
     * {@code it.websocket} system property.
     * <p>
     * The {@code *-sse} scenarios set it to false: their proxy has no upgrade
     * support at all. That is fine for Flow push over SSE, but Hilla's reactive
     * endpoints cannot work there — {@code FluxConnection} subscribes with
     * {@code transport: 'websocket'} and {@code fallbackTransport: 'websocket'},
     * so Hilla push is WebSocket-only with no fallback.
     */
    static boolean webSocketAvailable() {
        return Boolean.parseBoolean(System.getProperty("it.websocket", "true"));
    }

    /**
     * The URLs of every {@code EventSource} the page has opened.
     * <p>
     * This is how a test tells a real SSE push connection from a silent
     * fallback to another transport. It reads the browser's own behaviour
     * rather than Playwright's network events, which do not reliably surface a
     * long-lived event stream that stays open for the whole test.
     */
    protected List<String> openedEventSources() {
        List<?> urls = (List<?>) page
                .evaluate("() => window.__sseUrls ? [...window.__sseUrls] : []");
        return urls.stream().map(String::valueOf).toList();
    }

    /**
     * Switches the Vaadin push transport via the Select in the MainLayout
     * side drawer. The drawer is forced open first because it may be
     * collapsed on smaller viewports.
     * <p>
     * Selecting the transport that is already active is a no-op, so this can be
     * called unconditionally whatever a scenario's default transport is.
     */
    protected void switchPushTransport(String transportName) {
        page.evaluate(
                "() => { const l = document.querySelector('vaadin-app-layout');"
                        + " if (l) l.drawerOpened = true; }");
        page.getByLabel("Push Transport").click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions()
                .setName(transportName).setExact(true)).click();
        // give Atmosphere time to reconnect with the new transport
        page.waitForTimeout(500);
    }
}
