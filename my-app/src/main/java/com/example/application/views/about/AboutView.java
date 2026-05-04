package com.example.application.views.about;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;

@PageTitle("About")
@Route(value = "")
@Menu(title = "About", icon = "file", order = 1)
public class AboutView extends VerticalLayout {

    public AboutView() {
        setSpacing(false);

        String imagePath = VaadinService.getCurrent()
                .getContextRootRelativePath(VaadinRequest.getCurrent())
                + "images/empty-plant.png";
        Image img = new Image(imagePath, "placeholder plant");
        img.setWidth("200px");
        add(img);

        add(new H2("This place intentionally left empty"));
        add(new Paragraph("It’s a place where you can grow your own UI 🤗"));

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");
    }

}
