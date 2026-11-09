package com.example.application.views.runtimescripts;

import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * A view with a runtime {@code @JavaScript} dependency, for
 * {@code RuntimeScriptsIT}.
 * <p>
 * Since Flow 25.4 a {@code @JavaScript} value with a runtime prefix such as
 * {@code context://} is not bundled. Flow resolves it to a URL relative to the
 * page and adds a {@code script} element when the view is shown, so the browser
 * fetches the file through the proxy. A proxy that rewrites the public path, or
 * only forwards the Vaadin servlet path, can make that request a 404. The two
 * scripts on {@link com.example.application.Application} cover the app shell,
 * where the {@code script} elements are written into the initial page instead.
 * <p>
 * The files are static resources in {@code META-INF/resources/js/}, served by
 * the servlet container from the context root whatever the Vaadin servlet
 * mapping is.
 */
@PageTitle("Runtime Scripts")
@Route("runtime-scripts")
@JavaScript("context://js/view-script.js")
public class RuntimeScriptsView extends VerticalLayout {

    public RuntimeScriptsView() {
        add(new Paragraph(
                "This view loads js/view-script.js at runtime. The app shell"
                        + " loads js/shell-script.js and js/shell-module.js."));
    }
}
