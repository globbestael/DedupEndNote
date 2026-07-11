package edu.dedupendnote.controllers;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds baseline security response headers to every response — {@code X-Content-Type-Options}
 * (MIME-sniffing), {@code X-Frame-Options} (clickjacking) and {@code Referrer-Policy}.
 *
 * <p>Runs as a servlet {@link jakarta.servlet.Filter} so it applies uniformly to dynamic
 * pages, uploads, the streamed result-file download, static resources and error dispatches.
 * Headers are set before {@code doFilter} so they are present before the response is
 * committed. No Spring Security dependency (security hardening issue 07).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("X-Frame-Options", "SAMEORIGIN");
		response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
		filterChain.doFilter(request, response);
	}
}
