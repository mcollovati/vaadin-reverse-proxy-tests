package com.example.application.views;

import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.stream.Stream;

import com.vaadin.experimental.FeatureFlags;
import com.vaadin.flow.component.PushConfiguration;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;

/**
 * The main view is a top-level placeholder for other views.
 */
@Layout
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final String appName;
    private final H2 viewTitle;

    public MainLayout(@Value("${app.name:My App}") String appName) {
        this.appName = appName;
        this.viewTitle = new H2();
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle.getStyle().set("font-size", "var(--vaadin-font-size-l)")
                .set("margin", "0");

        addToNavbar(true, toggle, viewTitle);
    }

    private void addDrawerContent() {
        H1 appName = new H1(this.appName);
        appName.getStyle().set("font-size", "var(--vaadin-font-size-l)")
                .set("margin", "0");

        Select<Transport> pushTransport = new Select<>();
        pushTransport.setLabel("Push Transport");
        pushTransport.setItems(availableTransports());
        pushTransport.addValueChangeListener(
                event -> switchTransport(event.getValue()));
        pushTransport.setValue(
                UI.getCurrent().getPushConfiguration().getTransport());

        Header header = new Header(appName);

        Scroller scroller = new Scroller(createNavigation());

        addToDrawer(header, pushTransport, scroller, createFooter());
    }

    /**
     * Switches the push transport of the live connection.
     * <p>
     * {@link PushConfiguration#setTransport} documents that "the new transport
     * type will not be used until the push channel is disconnected and
     * reconnected if already active" — the client copies the transport into the
     * Atmosphere configuration in {@code AtmospherePushConnection.init()} and
     * watches only {@code pushMode} for changes. Toggling push off and on is
     * that reconnect.
     * <p>
     * The toggle has to span two client round-trips, which is what the
     * {@code executeJs} hop buys. Setting DISABLED and then AUTOMATIC inside a
     * single response would leave the value unchanged as far as the client is
     * concerned, so no change event would fire and the connection would never
     * be re-created.
     */
    private void switchTransport(Transport transport) {
        UI ui = UI.getCurrent();
        PushConfiguration push = ui.getPushConfiguration();
        push.setTransport(transport);
        push.setPushMode(PushMode.DISABLED);
        ui.getPage().executeJs("return true").then(Boolean.class,
                ignored -> push.setPushMode(PushMode.AUTOMATIC));
    }

    /**
     * The transports the running Flow build actually offers.
     * <p>
     * SERVER_SENT_EVENTS is experimental: selecting it while the
     * {@code ssePushTransport} feature flag is off throws server-side, so it is
     * only listed once the flag is on. Matching on the transport identifier
     * instead of the enum constant keeps this compiling against Flow versions
     * that predate the transport.
     */
    private static List<Transport> availableTransports() {
        FeatureFlags featureFlags = FeatureFlags
                .get(VaadinService.getCurrent().getContext());
        return Stream.of(Transport.values())
                .filter(transport -> !"sse".equals(transport.getIdentifier())
                        || featureFlags.isEnabled("ssePushTransport"))
                .toList();
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        MenuConfiguration.getMenuEntries().stream().map(this::createSideNavItem)
                .forEach(nav::addItem);
        return nav;
    }

    private SideNavItem createSideNavItem(MenuEntry menuItem) {
        SideNavItem item = new SideNavItem(menuItem.title());
        if (menuItem.menuClass() != null) {
            item.setPath(menuItem.menuClass());
        } else {
            item.setPath(menuItem.path());
        }
        if (menuItem.icon() != null) {
            item.setPrefixComponent(new Icon(menuItem.icon()));
        } else {
            item.setPrefixComponent(VaadinIcon.FILE.create());
        }
        return item;
    }

    private Footer createFooter() {
        return new Footer();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        MenuConfiguration.getPageHeader().ifPresent(viewTitle::setText);
    }

}
