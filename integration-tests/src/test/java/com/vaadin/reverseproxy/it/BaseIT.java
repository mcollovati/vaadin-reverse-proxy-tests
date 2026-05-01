package com.vaadin.reverseproxy.it;

import com.microsoft.playwright.Browser;
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
        context = browser.newContext();
        page = context.newPage();
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
     * Switches the Vaadin push transport via the Select in the MainLayout
     * side drawer. The drawer is forced open first because it may be
     * collapsed on smaller viewports.
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
