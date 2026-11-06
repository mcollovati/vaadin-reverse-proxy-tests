package com.vaadin.reverseproxy.it;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte ranges of a media file through the proxy (Flow 25.4+).
 * <p>
 * Since Flow 25.4 a download handler can answer {@code Range} requests. The
 * main use is seeking in audio and video, so {@code DownloadsView} serves a
 * short WAV file, both as an {@code <audio>} source and as a download link.
 * These tests check:
 * <ul>
 * <li>what a media element does: it asks for {@code Range: bytes=N-} with
 * {@code Accept-Encoding: identity;q=1, *;q=0} (what Chrome sends), and needs
 * a {@code 206} with the right {@code Content-Range} and bytes, not
 * compressed. A real {@code <audio>} element loads the file and seeks in
 * it;</li>
 * <li>resuming a download: a client that accepts gzip gets the full file, and
 * a later {@code If-Range} with its {@code ETag} must give a {@code 206}. A
 * proxy that compresses {@code audio/wav} breaks this: Apache's
 * {@code mod_deflate} adds {@code -gzip} to the {@code ETag}, nginx and
 * HAProxy make it weak (and {@code If-Range} never matches a weak
 * {@code ETag}), and some proxies drop {@code Accept-Ranges};</li>
 * <li>the {@code Content-Disposition} header, which carries a quoted file name
 * with a non-ASCII letter, reaches the browser unchanged.</li>
 * </ul>
 * The HTTP checks go through the browser context's request API, so they carry
 * the session cookie that the download URLs are bound to.
 */
class DownloadRangesIT extends BaseIT {

    /** The size of {@code range-sample.wav} in the app. */
    private static final int FILE_SIZE = 16044;

    /** Where the seek tests start reading: the middle of the samples. */
    private static final int SEEK_OFFSET = 8044;

    /** What Chrome sends for the requests of a media element. */
    private static final String MEDIA_ACCEPT_ENCODING = "identity;q=1, *;q=0";

    /**
     * The header Flow sends for {@code DownloadsView.DOWNLOAD_NAME}
     * ({@code range "sample" é.wav}), taken from the app without a proxy on
     * Vaadin 25.4.0-alpha5. For a non-ASCII name Flow sends an RFC 2047
     * encoded {@code filename} for old clients and the RFC 5987
     * {@code filename*} that browsers use.
     */
    private static final String EXPECTED_DISPOSITION = "attachment;"
            + " filename=\"=?UTF-8?Q?range_=22sample=22_=C3=A9.wav?=\";"
            + " filename*=UTF-8''range%20%22sample%22%20%C3%A9.wav";

    private static final Pattern CONTENT_RANGE = Pattern
            .compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private String downloadUrl;

    @BeforeEach
    void openDownloadsView() {
        navigate("downloads");
        // The resolved href: absolute, and through the proxy.
        downloadUrl = (String) page.locator("#range-download")
                .evaluate("a => a.href");
        assertNotNull(downloadUrl, "The download link has no href");
    }

    @Test
    void mediaRequestFromTheStartGetsTheWholeFileAsARange() {
        APIResponse response = get(Map.of("Range", "bytes=0-",
                "Accept-Encoding", MEDIA_ACCEPT_ENCODING));

        assertPartial(response, 0, FILE_SIZE - 1);
        assertEquals("bytes", response.headers().get("accept-ranges"),
                "The proxy dropped Accept-Ranges. Headers: "
                        + response.headers());
        assertEquals(FILE_SIZE, response.body().length,
                "The 206 answer does not have the whole file");
    }

    @Test
    void mediaSeekGetsTheRightBytes() {
        byte[] file = get(Map.of("Accept-Encoding", MEDIA_ACCEPT_ENCODING))
                .body();
        assertEquals(FILE_SIZE, file.length,
                "The full answer does not have the whole file");

        APIResponse response = get(Map.of("Range", "bytes=" + SEEK_OFFSET + "-",
                "Accept-Encoding", MEDIA_ACCEPT_ENCODING));

        assertPartial(response, SEEK_OFFSET, FILE_SIZE - 1);
        assertArrayEquals(Arrays.copyOfRange(file, SEEK_OFFSET, FILE_SIZE),
                response.body(), "The 206 answer has other bytes than the file"
                        + " from offset " + SEEK_OFFSET);
    }

    @Test
    void audioElementLoadsWithRangesAndSeeks() {
        List<Response> audioResponses = new ArrayList<>();
        page.onResponse(response -> {
            if ("media".equals(response.request().resourceType())) {
                audioResponses.add(response);
            }
        });
        // A fresh load, now that the listener is in place.
        navigate("downloads");

        page.waitForFunction("() => {"
                + " const a = document.querySelector('#range-audio');"
                + " return a && (a.readyState >= 1 || a.error); }");
        Object error = page.evaluate(
                "() => { const e = document.querySelector('#range-audio').error;"
                        + " return e ? e.code + ' ' + e.message : null; }");
        assertNull(error, "The audio element could not load the file");

        page.evaluate("() => new Promise(resolve => {"
                + " const a = document.querySelector('#range-audio');"
                + " a.addEventListener('seeked', resolve, { once: true });"
                + " a.currentTime = 1.5; })");
        double currentTime = ((Number) page.evaluate(
                "() => document.querySelector('#range-audio').currentTime"))
                .doubleValue();
        assertEquals(1.5, currentTime, 0.05, "The audio element did not seek");

        assertFalse(audioResponses.isEmpty(),
                "The browser made no media request");
        for (Response response : audioResponses) {
            Map<String, String> headers = response.allHeaders();
            assertEquals(206, response.status(),
                    "A media request of the audio element got "
                            + response.status() + " instead of 206. Request: "
                            + response.request().allHeaders() + " Headers: "
                            + headers);
            assertNull(headers.get("content-encoding"),
                    "The proxy compressed a media answer. Headers: " + headers);
        }
    }

    @Test
    void fullDownloadAdvertisesRangesAndKeepsTheETag() {
        APIResponse full = get(Map.of("Accept-Encoding", "gzip"));
        assertEquals(200, full.status());
        assertEquals("bytes", full.headers().get("accept-ranges"),
                "The proxy dropped Accept-Ranges. Headers: " + full.headers());
        String fullETag = full.headers().get("etag");
        assertNotNull(fullETag, "No ETag on the full download. Headers: "
                + full.headers());

        // A 206 is never compressed, so its ETag is the one the app sent.
        String rangeETag = get(Map.of("Range", "bytes=0-9")).headers()
                .get("etag");
        assertEquals(rangeETag, fullETag,
                "The ETag of the full download differs from the app's ETag:"
                        + " the proxy changed it, so If-Range cannot match."
                        + " Full download headers: " + full.headers());
    }

    @Test
    void ifRangeWithTheETagResumes() {
        String eTag = get(Map.of("Accept-Encoding", "gzip")).headers()
                .get("etag");
        assertNotNull(eTag, "No ETag on the full download");

        APIResponse resumed = get(Map.of("Range", "bytes=100-199", "If-Range",
                eTag, "Accept-Encoding", "gzip"));

        assertEquals(206, resumed.status(),
                "A Range request with If-Range: " + eTag
                        + " got the whole content instead of the range, so a"
                        + " resumed download starts over. Headers: "
                        + resumed.headers());
        Matcher range = contentRange(resumed);
        assertEquals("100", range.group(1));
        assertEquals("199", range.group(2));
    }

    @Test
    void contentDispositionIsUnchanged() {
        APIResponse response = get(Map.of());
        assertEquals(200, response.status());
        assertEquals(EXPECTED_DISPOSITION,
                response.headers().get("content-disposition"),
                "The proxy changed Content-Disposition");
    }

    private APIResponse get(Map<String, String> headers) {
        RequestOptions options = RequestOptions.create();
        headers.forEach(options::setHeader);
        return page.request().get(downloadUrl, options);
    }

    private static void assertPartial(APIResponse response, int first,
            int last) {
        assertEquals(206, response.status(), "Range request not answered with"
                + " 206 Partial Content. Headers: " + response.headers());
        assertNull(response.headers().get("content-encoding"),
                "The proxy compressed a 206 answer. Headers: "
                        + response.headers());
        Matcher range = contentRange(response);
        assertEquals(String.valueOf(first), range.group(1));
        assertEquals(String.valueOf(last), range.group(2));
        assertEquals(String.valueOf(FILE_SIZE), range.group(3));
    }

    private static Matcher contentRange(APIResponse response) {
        String value = response.headers().get("content-range");
        assertNotNull(value, "No Content-Range on a 206 answer. Headers: "
                + response.headers());
        Matcher matcher = CONTENT_RANGE.matcher(value);
        assertTrue(matcher.matches(), "Unexpected Content-Range: " + value);
        return matcher;
    }
}
