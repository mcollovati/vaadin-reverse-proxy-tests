package com.example.application.views;

import org.springframework.beans.factory.annotation.Value;

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
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * The main view is a top-level placeholder for other views.
 */
@Layout
public class MainLayout extends AppLayout {

    private String appName;
    private H2 viewTitle;

    public MainLayout(@Value("${app.name:My App}") String appName) {
        this.appName = appName;
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.NONE);

        addToNavbar(true, toggle, viewTitle);
    }

    private void addDrawerContent() {
        H1 appName = new H1(this.appName);
        appName.addClassNames(LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.NONE);

        Select<Transport> pushTransport = new Select<>();
        pushTransport.setLabel("Push Transport");
        pushTransport.setItems(Transport.values());
        pushTransport.addValueChangeListener(event -> UI.getCurrent()
                .getPushConfiguration().setTransport(event.getValue()));
        pushTransport.setValue(
                UI.getCurrent().getPushConfiguration().getTransport());

        Header header = new Header(appName);

        Scroller scroller = new Scroller(createNavigation());

        addToDrawer(header, pushTransport, scroller, createFooter());
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
    protected void afterNavigation() {
        super.afterNavigation();
        MenuConfiguration.getPageHeader().ifPresent(viewTitle::setText);
    }

}
