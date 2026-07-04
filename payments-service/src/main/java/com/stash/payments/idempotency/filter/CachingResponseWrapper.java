package com.stash.payments.idempotency.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Captures the response body as it is written by the downstream handler,
 * so the idempotency filter can store it in the idempotency_keys row
 * before flushing it to the actual client.
 */
public class CachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    private PrintWriter    captureWriter;
    private ServletOutputStream captureStream;

    public CachingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public PrintWriter getWriter() {
        if (captureWriter == null) {
            captureWriter = new PrintWriter(new OutputStreamWriter(capture, StandardCharsets.UTF_8));
        }
        return captureWriter;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (captureStream == null) {
            captureStream = new ByteArrayServletOutputStream(capture);
        }
        return captureStream;
    }

    public String getCachedBody() {
        if (captureWriter != null) captureWriter.flush();
        return capture.toString(StandardCharsets.UTF_8);
    }

    /**
     * Writes the captured body to the real response.
     * Called after the idempotency row is updated.
     */
    public void copyBodyToResponse() throws IOException {
        if (getResponse().isCommitted()) {
            return;
        }
        byte[] body = capture.toByteArray();
        if (body.length == 0) {
            return;
        }
        getResponse().setContentLength(body.length);
        getResponse().getOutputStream().write(body);
    }

    private static class ByteArrayServletOutputStream extends ServletOutputStream {
        private final OutputStream delegate;
        ByteArrayServletOutputStream(OutputStream delegate) { this.delegate = delegate; }
        @Override public void write(int b) throws IOException { delegate.write(b); }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener l) {}
    }
}
