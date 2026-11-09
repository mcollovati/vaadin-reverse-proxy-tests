package com.vaadin.reverseproxy.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Push recovers by itself after the proxy restarts.
 * <p>
 * Restarting the proxy is the everyday way a push connection gets cut: the
 * browser side of the connection dies at once, the backend side some time
 * later. Flow 25.4 fixed three ways this went wrong:
 * <ul>
 * <li>the late disconnect of the old connection cleared the new one, after
 * which server pushes stopped reaching the browser (vaadin/flow#26690);</li>
 * <li>repeated drops switched the page to long polling for good
 * (vaadin/flow#26617);</li>
 * <li>with WEBSOCKET_XHR, a lost connection was never shown to the user
 * (vaadin/flow#26309).</li>
 * </ul>
 * The test opens {@code PushCounterView}, which pushes a counter every 500 ms,
 * restarts the proxy several times with the command in
 * {@code it.proxy.restart.cmd}, and after each restart checks, with no user
 * input in between, that the counter moves again, that the client reports
 * itself connected, and that it reconnected with the transport it started
 * with.
 * <p>
 * Skipped when {@code it.proxy.restart.cmd} is empty: only whoever started the
 * scenario knows how to restart its proxy. {@code scripts/ci-run-chunk.sh}
 * sets it.
 */
class PushReconnectIT extends BaseIT {

    private static final int RESTARTS = 3;

    // Long enough for a proxy restart plus Atmosphere's reconnect interval,
    // which Flow sets to 5 s, a few times over.
    private static final double RECOVERY_TIMEOUT_MS = 60_000;
    private static final long RESTART_TIMEOUT_S = 120;

    private String restartCommand;

    @BeforeEach
    void requireRestartCommand() {
        restartCommand = System.getProperty("it.proxy.restart.cmd", "").trim();
        assumeTrue(!restartCommand.isEmpty(),
                "it.proxy.restart.cmd is not set; nothing can restart the proxy");
        // Runs before any application script, like the EventSource hook in
        // BaseIT, so it records the push connection of the initial page load.
        page.addInitScript("""
                window.__pushSockets = [];
                const NativeWebSocket = window.WebSocket;
                window.WebSocket = function (url, protocols) {
                    const socket = protocols === undefined
                        ? new NativeWebSocket(url)
                        : new NativeWebSocket(url, protocols);
                    if (String(url).includes('push')
                            && !String(url).includes('HILLA')) {
                        window.__pushSockets.push(socket);
                    }
                    return socket;
                };
                window.WebSocket.prototype = NativeWebSocket.prototype;
                window.WebSocket.CONNECTING = NativeWebSocket.CONNECTING;
                window.WebSocket.OPEN = NativeWebSocket.OPEN;
                window.WebSocket.CLOSING = NativeWebSocket.CLOSING;
                window.WebSocket.CLOSED = NativeWebSocket.CLOSED;
                """);
    }

    @ParameterizedTest
    @MethodSource("com.vaadin.reverseproxy.it.BaseIT#pushTransports")
    void pushRecoversAfterProxyRestarts(String transport) throws Exception {
        navigate("push-counter");
        switchPushTransport(transport);
        boolean sse = "SERVER_SENT_EVENTS".equals(transport);

        waitForCounterAbove(counter() + 2, "before the first restart");
        assertConnected("before the first restart");

        for (int restart = 1; restart <= RESTARTS; restart++) {
            String when = "after proxy restart " + restart + " of " + RESTARTS
                    + " (" + transport + ")";
            int connectionsBefore = sse ? openedEventSources().size()
                    : pushSockets();

            restartProxy();

            // Read after the restart: ticks pushed while the proxy was down
            // may arrive in one go once it is back.
            waitForCounterAbove(counter() + 3, when);
            assertConnected(when);

            if (sse) {
                List<String> eventSources = openedEventSources();
                assertTrue(eventSources.size() > connectionsBefore,
                        "Push kept working " + when
                                + " without a new EventSource, so it fell back to"
                                + " another transport or the restart did not cut"
                                + " the connection. EventSource URLs: "
                                + eventSources);
            } else {
                int connectionsAfter = pushSockets();
                assertTrue(connectionsAfter > connectionsBefore,
                        "Push kept working " + when
                                + " without opening a new WebSocket, so it fell"
                                + " back to long polling or the restart did not cut"
                                + " the connection. Push WebSockets opened: "
                                + connectionsAfter);
                assertEquals(1, latestPushSocketState(),
                        "The newest push WebSocket is not open " + when);
            }
        }
    }

    private void restartProxy() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("sh", "-c", restartCommand)
                .redirectErrorStream(true).start();
        boolean done = process.waitFor(RESTART_TIMEOUT_S, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        if (!done) {
            process.destroyForcibly();
            fail("Proxy restart did not finish in " + RESTART_TIMEOUT_S
                    + " s: " + restartCommand + "\n" + output);
        }
        assertEquals(0, process.exitValue(), "Proxy restart failed: "
                + restartCommand + "\n" + output);
    }

    private int counter() {
        Object value = page.evaluate(
                "() => Number(document.getElementById('push-counter')"
                        + "?.textContent ?? -1)");
        return ((Number) value).intValue();
    }

    private void waitForCounterAbove(int target, String when) {
        try {
            page.waitForFunction(
                    "target => Number(document.getElementById('push-counter')"
                            + "?.textContent ?? -1) >= target",
                    target, new Page.WaitForFunctionOptions()
                            .setTimeout(RECOVERY_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            fail("The pushed counter stopped " + when + ": it is "
                    + counter() + ", expected at least " + target
                    + ". Connection state: " + connectionState(), e);
        }
    }

    private void assertConnected(String when) {
        try {
            page.waitForFunction(
                    "() => window.Vaadin?.connectionState?.state === 'connected'",
                    null, new Page.WaitForFunctionOptions()
                            .setTimeout(RECOVERY_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            fail("The client does not report itself connected " + when
                    + ": " + connectionState(), e);
        }
    }

    private Object connectionState() {
        return page.evaluate("() => window.Vaadin?.connectionState?.state");
    }

    private int pushSockets() {
        return ((Number) page.evaluate("() => window.__pushSockets.length"))
                .intValue();
    }

    /**
     * The {@code readyState} of the newest push WebSocket: 1 is OPEN.
     */
    private int latestPushSocketState() {
        Object state = page.evaluate(
                "() => { const s = window.__pushSockets;"
                        + " return s.length ? s[s.length - 1].readyState : -1; }");
        return ((Number) state).intValue();
    }
}
