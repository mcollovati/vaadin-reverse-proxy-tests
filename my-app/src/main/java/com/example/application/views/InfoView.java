package com.example.application.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;

@Route("info")
public class InfoView extends VerticalLayout {

    public InfoView() {
        add(new H1("Server Info view"));
        VaadinServletRequest request = VaadinServletRequest.getCurrent();

        add(new H2("Request Details"));
        add(new OrderedList(
                new ListItem("Request URI: " + request.getRequestURI()),
                new ListItem("Request URL: " + request.getRequestURL()),
                new ListItem("Context path: " + request.getContextPath()),
                new ListItem("Servlet path: " + request.getServletPath()),
                new ListItem("Path info: " + request.getPathInfo())));
        add(new H2("Request Headers"));
        var headers = new OrderedList();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            headers.add(new ListItem(
                    headerName + ": " + request.getHeader(headerName)));
        });
        add(headers);
        
    }
}
