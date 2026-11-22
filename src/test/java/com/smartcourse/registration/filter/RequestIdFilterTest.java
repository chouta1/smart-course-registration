package com.smartcourse.registration.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesSafeProvidedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (filteredRequest, filteredResponse) -> {
            assertEquals("request-123", org.slf4j.MDC.get("requestId"));
        };

        filter.doFilter(request, response, chain);

        assertEquals("request-123", response.getHeader("X-Request-Id"));
        assertEquals(null, org.slf4j.MDC.get("requestId"));
    }

    @Test
    void replacesInvalidRequestIdAndCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "unsafe\r\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (filteredRequest, filteredResponse) ->
                assertFalse(org.slf4j.MDC.get("requestId").contains("\r"));

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader("X-Request-Id");
        org.junit.jupiter.api.Assertions.assertNotNull(requestId);
        org.junit.jupiter.api.Assertions.assertFalse(requestId.isBlank());
        assertEquals(null, org.slf4j.MDC.get("requestId"));
    }
}
