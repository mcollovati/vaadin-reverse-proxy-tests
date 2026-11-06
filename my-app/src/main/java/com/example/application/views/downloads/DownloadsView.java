package com.example.application.views.downloads;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;

/**
 * A download that answers {@code Range} requests, for
 * {@code DownloadRangesIT}.
 * <p>
 * Since Flow 25.4 download handlers can serve byte ranges, which media players
 * need to seek and browsers need to resume a download. A proxy sits in the
 * middle of that: every proxy in this repository compresses
 * {@code application/json}, and a compressing proxy must not compress a
 * {@code 206 Partial Content} answer, must keep {@code Content-Length}, and
 * must not change the {@code ETag} that {@code If-Range} is compared with.
 * <p>
 * The download name contains quotes and a non-ASCII letter. Flow quotes it in
 * {@code Content-Disposition} as RFC 6266 describes, and also uses it, encoded,
 * as the last segment of the download URL, so the proxy has to pass both
 * through unchanged.
 */
@PageTitle("Downloads")
@Route("downloads")
public class DownloadsView extends VerticalLayout {

    /** The file name the browser is told to save the download as. */
    public static final String DOWNLOAD_NAME = "range \"sample\" é.json";

    public DownloadsView() {
        Anchor download = new Anchor(DownloadHandler
                .forClassResource(DownloadsView.class, "range-sample.json",
                        DOWNLOAD_NAME)
                .withContentType("application/json").enableRangeRequests(),
                "Download the range sample");
        download.setId("range-download");
        add(new Paragraph(
                "A JSON download that answers Range requests."), download);
    }
}
