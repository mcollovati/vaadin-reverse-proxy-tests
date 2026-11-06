package com.vaadin.reverseproxy.it;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte ranges of a download through a compressing proxy (Flow 25.4+).
 * <p>
 * Since Flow 25.4 a download handler can answer {@code Range} requests, which
 * media players need to seek and browsers need to resume a download. Every
 * proxy in this repository compresses {@code application/json}, the type of
 * the download in {@code DownloadsView}, so these tests check what a
 * compressing proxy must leave alone:
 * <ul>
 * <li>a {@code 206 Partial Content} answer is not compressed, keeps its
 * {@code Content-Range} and has exactly the requested number of bytes;</li>
 * <li>the {@code ETag} is the same whether or not the proxy compressed the full
 * answer. Apache's {@code mod_deflate} adds {@code -gzip} to it, and then an
 * {@code If-Range} with that value never matches, so a resumed download
 * silently gets the whole content again;</li>
 * <li>the {@code Content-Disposition} header, which carries a quoted file name
 * with a non-ASCII letter, reaches the browser unchanged.</li>
 * </ul>
 * The requests go through the browser context's request API, so they carry the
 * session cookie that the download URL is bound to.
 */
class DownloadRangesIT extends BaseIT {

    /**
     * The header Flow sends for {@code DownloadsView.DOWNLOAD_NAME}
     * ({@code range "sample" é.json}), taken from the app without a proxy on
     * Vaadin 25.4-SNAPSHOT. For a non-ASCII name Flow sends an RFC 2047
     * encoded {@code filename} for old clients and the RFC 5987
     * {@code filename*} that browsers use.
     */
    private static final String EXPECTED_DISPOSITION = "attachment;"
            + " filename=\"=?UTF-8?Q?range_=22sample=22_=C3=A9.json?=\";"
            + " filename*=UTF-8''range%20%22sample%22%20%C3%A9.json";

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
    void rangeIsServedUncompressed() {
        APIResponse response = get(Map.of("Range", "bytes=0-99",
                "Accept-Encoding", "gzip"));

        assertEquals(206, response.status(), "Range request not answered with"
                + " 206 Partial Content. Headers: " + response.headers());
        assertNull(response.headers().get("content-encoding"),
                "The proxy compressed a 206 answer. Headers: "
                        + response.headers());
        Matcher range = contentRange(response);
        assertEquals("0", range.group(1));
        assertEquals("99", range.group(2));
        assertEquals(100, response.body().length,
                "The 206 answer does not have the requested 100 bytes");
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

    private static Matcher contentRange(APIResponse response) {
        String value = response.headers().get("content-range");
        assertNotNull(value, "No Content-Range on a 206 answer. Headers: "
                + response.headers());
        Matcher matcher = CONTENT_RANGE.matcher(value);
        assertTrue(matcher.matches(), "Unexpected Content-Range: " + value);
        return matcher;
    }
}
