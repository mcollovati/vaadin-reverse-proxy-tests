package com.vaadin.reverseproxy.it;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
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
}
