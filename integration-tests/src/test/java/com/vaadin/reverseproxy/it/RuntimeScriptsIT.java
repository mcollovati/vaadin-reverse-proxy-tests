package com.vaadin.reverseproxy.it;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runtime {@code @JavaScript} URLs through the proxy (Flow 25.4+).
 * <p>
 * A {@code @JavaScript} value with a runtime prefix such as {@code context://}
 * is not bundled any more. On the app shell Flow writes it into the initial
 * page as a {@code script} element; on a view it adds the element when the view
 * is shown. Either way the URL is relative to the page, so the browser fetches
 * the file through the proxy, from the servlet context root. A proxy that
 * rewrites the public path, or forwards only the Vaadin servlet path, turns
 * that into a 404 and the script silently never runs.
 * <p>
 * Each script sets a flag on {@code window}. A flag proves that the browser
 * fetched and ran the file; the recorded response status says why it did not.
 */
class RuntimeScriptsIT extends BaseIT {

    private static final List<String> SHELL_SCRIPTS = List
            .of("shell-script.js", "shell-module.js");
    private static final String VIEW_SCRIPT = "view-script.js";

    // How long to wait for a flag. The scripts load with the page, so this is
    // only slack for a slow CI runner.
    private static final double FLAG_TIMEOUT_MS = 10_000;

    @Test
    void shellAndViewScriptsLoad() {
        Map<String, Integer> statuses = recordScriptResponses();

        navigate("runtime-scripts");

        assertFlag("__rpShellScript", "js/shell-script.js", statuses);
        assertFlag("__rpShellModule", "js/shell-module.js", statuses);
        assertFlag("__rpViewScript", "js/view-script.js", statuses);
        for (String script : SHELL_SCRIPTS) {
            assertEquals(200, statuses.get(script),
                    "Unexpected status for js/" + script + ": " + statuses);
        }
        assertEquals(200, statuses.get(VIEW_SCRIPT),
                "Unexpected status for js/" + VIEW_SCRIPT + ": " + statuses);
    }

    @Test
    void shellScriptsLoadOnEveryView() {
        Map<String, Integer> statuses = recordScriptResponses();

        // The app shell scripts are part of the initial page, whatever the
        // first view is. The view script must not be there.
        navigate("");

        assertFlag("__rpShellScript", "js/shell-script.js", statuses);
        assertFlag("__rpShellModule", "js/shell-module.js", statuses);
        assertNull(statuses.get(VIEW_SCRIPT),
                "js/view-script.js was loaded on a view that does not declare it");
    }

    /**
     * Records the status of every response for one of the test scripts, keyed
     * by file name. Must be called before navigating.
     */
    private Map<String, Integer> recordScriptResponses() {
        Map<String, Integer> statuses = new ConcurrentHashMap<>();
        page.onResponse(response -> {
            String url = response.url();
            int query = url.indexOf('?');
            String path = query < 0 ? url : url.substring(0, query);
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (path.contains("/js/") && (SHELL_SCRIPTS.contains(name)
                    || VIEW_SCRIPT.equals(name))) {
                statuses.put(name, response.status());
            }
        });
        return statuses;
    }

    private void assertFlag(String flag, String file,
            Map<String, Integer> statuses) {
        try {
            page.waitForFunction("() => window." + flag + " === true", null,
                    new Page.WaitForFunctionOptions()
                            .setTimeout(FLAG_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            fail(file + " did not run (window." + flag
                    + " is not set). Script responses seen: " + statuses
                    + ". Script elements on the page: " + scriptSources(),
                    e);
        }
    }

    private List<String> scriptSources() {
        List<?> sources = (List<?>) page.evaluate(
                "() => [...document.querySelectorAll('script[src]')]"
                        + ".map(s => (s.type || 'classic') + ' ' + s.src)");
        return sources.stream().map(String::valueOf).toList();
    }
}
