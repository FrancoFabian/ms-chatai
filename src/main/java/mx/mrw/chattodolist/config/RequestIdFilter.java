package mx.mrw.chattodolist.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.mrw.chattodolist.support.RequestContext;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incomingRequestId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = StringUtils.hasText(incomingRequestId) ? incomingRequestId : UUID.randomUUID().toString();

        request.setAttribute(RequestContext.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(RequestContext.REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.remove(RequestContext.REQUEST_ID);
        }
    }
}
