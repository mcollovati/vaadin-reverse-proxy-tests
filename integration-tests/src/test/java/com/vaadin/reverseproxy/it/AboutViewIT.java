package com.vaadin.reverseproxy.it;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AboutViewIT extends BaseIT {

    @Test
    void aboutLoadsAndImageIsServed() {
        navigate("");

        assertThat(page.getByText("This place intentionally left empty"))
                .isVisible();

        Locator img = page.locator("img[alt='placeholder plant']");
        assertThat(img).isVisible();

        // naturalWidth > 0 proves the image actually loaded — catches both
        // a 404 from a misconfigured proxy and a broken decode.
        Boolean loaded = (Boolean) img.evaluate(
                "el => el.complete && el.naturalWidth > 0");
        assertTrue(loaded != null && loaded,
                "About view image did not load (404 or broken decode): "
                        + img.getAttribute("src"));
    }

    @Test
    void auraStylesheetIsLoadedAndApplied() {
        navigate("");

        // 1. Stylesheet is loaded by the browser. Presence in
        // document.styleSheets proves the browser fetched and parsed it
        // (a 404 never appears here).
        String loadResult = (String) page.evaluate(
                "() => {"
                        + "  const sheet = Array.from(document.styleSheets)"
                        + "    .find(s => s.href && s.href.includes('/aura/'));"
                        + "  if (!sheet) return 'missing';"
                        + "  try {"
                        + "    return sheet.cssRules.length > 0"
                        + "      ? 'ok' : 'empty';"
                        + "  } catch (e) { return 'cors-blocked'; }"
                        + "}");
        assertEquals("ok", loadResult,
                "Aura stylesheet not loaded by the browser (got: "
                        + loadResult + ")");

        // 2. Stylesheet is actually applied — Aura sets --aura-base-size
        // on :root as the foundational sizing token from which
        // --vaadin-padding/--vaadin-gap are computed.
        String baseSize = (String) page.evaluate(
                "() => getComputedStyle(document.documentElement)"
                        + ".getPropertyValue('--aura-base-size').trim()");
        assertFalse(baseSize == null || baseSize.isEmpty(),
                "Aura is loaded but --aura-base-size is not applied — "
                        + "stylesheet present but rules not in effect");
    }
}
