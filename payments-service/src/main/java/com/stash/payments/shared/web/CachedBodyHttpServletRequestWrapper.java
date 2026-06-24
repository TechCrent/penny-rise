package com.stash.payments.shared.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps an HttpServletRequest and caches the body bytes so the input
 * stream can be read more than once. Required because the idempotency
 * filter reads the body to compute the request hash, and the downstream
 * handler (Spring MVC) must be able to read the same bytes again.
 *
 * <p>Applied by {@link IdempotencyFilter} before hashing.
 */
public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final InputStream delegate;

        CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override public boolean isFinished() { return available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) {}

        @Override
        public int read() throws IOException { return delegate.read(); }

        @Override
        public int available() {
            try { return delegate.available(); } catch (IOException e) { return 0; }
        }
    }
}
