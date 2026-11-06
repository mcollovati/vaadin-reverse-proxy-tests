package com.example.application.views.downloads;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Audio;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;

/**
 * A media file served with {@code Range} support, for {@code DownloadRangesIT}.
 * <p>
 * Since Flow 25.4 download handlers can serve byte ranges. The main use is
 * seeking in audio and video: a media element asks for {@code Range: bytes=N-}
 * and does not accept a compressed answer. The second use is resuming a
 * download with {@code If-Range}, where a proxy that compresses the full
 * answer can change the {@code ETag} that {@code If-Range} is compared with.
 * <p>
 * The file is a short PCM WAV (2 s, 8 kHz, 8-bit mono, 16044 bytes). It is
 * served twice: as an {@link Audio} source, which the browser loads and seeks
 * in, and as a download link under a name with quotes and a non-ASCII letter.
 * Flow quotes that name in {@code Content-Disposition} as RFC 6266 describes,
 * and also uses it, encoded, as the last segment of the download URL, so the
 * proxy has to pass both through unchanged.
 */
@PageTitle("Downloads")
@Route("downloads")
public class DownloadsView extends VerticalLayout {

    /** The media file in this package. */
    static final String RESOURCE = "range-sample.wav";

    /** The MIME type both handlers send. */
    static final String CONTENT_TYPE = "audio/wav";

    /** The file name the browser is told to save the download as. */
    public static final String DOWNLOAD_NAME = "range \"sample\" é.wav";

    public DownloadsView() {
        Audio audio = new Audio();
        audio.setId("range-audio");
        audio.setControlsVisible(true);
        audio.setPreload(Audio.Preload.METADATA);
        audio.addSource(DownloadHandler
                .forClassResource(DownloadsView.class, RESOURCE)
                .withContentType(CONTENT_TYPE).inline().enableRangeRequests(),
                CONTENT_TYPE);

        Anchor download = new Anchor(DownloadHandler
                .forClassResource(DownloadsView.class, RESOURCE, DOWNLOAD_NAME)
                .withContentType(CONTENT_TYPE).enableRangeRequests(),
                "Download the range sample");
        download.setId("range-download");

        add(new Paragraph("A media file that answers Range requests."), audio,
                download);
    }
}
