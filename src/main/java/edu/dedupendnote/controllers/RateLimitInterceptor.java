package edu.dedupendnote.controllers;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	@Value("${dedup.upload-cooldown-seconds:10}")
	private int cooldownSeconds;

	// Two-file mode requires two rapid uploads from the same IP; burst limit ≥ 2
	@Value("${dedup.upload-burst-limit:2}")
	private int burstLimit;

	// Attribute key used to pass the tracking key from preHandle to afterCompletion
	private static final String ATTR_RATE_KEY = RateLimitInterceptor.class.getName() + ".key";

	private record RateLimitEntry(long windowStart, int count) {
	}

	private final ConcurrentHashMap<String, RateLimitEntry> uploadHistory = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		// Track per session so that the two-file flow (two rapid uploads in one page
		// load) is not blocked. Falls back to IP if wssessionId is absent.
		String key = extractKey(request);
		long now = System.currentTimeMillis();
		long cooldownMillis = cooldownSeconds * 1000L;

		RateLimitEntry entry = uploadHistory.get(key);
		if (entry != null && (now - entry.windowStart()) < cooldownMillis && entry.count() >= burstLimit) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType("application/json");
			response.setHeader("Cache-Control", "no-store");
			String json = objectMapper.writeValueAsString(new ApiResponse(
					"ERROR: Too many uploads. Please wait a moment before trying again with the Restart button."));
			response.getWriter().write(json);
			return false;
		}
		request.setAttribute(ATTR_RATE_KEY, key);
		return true;
	}

	/*
	 * Only record the timestamp once the upload actually succeeds (HTTP 200).
	 * Failed requests (traversal attempt = 400, parse error = 400, etc.) do not
	 * consume the cooldown slot so legitimate retries are not accidentally blocked.
	 * afterCompletion is only called when preHandle returned true, so no cleanup
	 * is needed in the rejected path.
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable Exception ex) {
		String key = (String) request.getAttribute(ATTR_RATE_KEY);
		if (key != null && response.getStatus() == HttpServletResponse.SC_OK) {
			long now = System.currentTimeMillis();
			long cooldownMillis = cooldownSeconds * 1000L;
			uploadHistory.compute(key, (k, entry) -> {
				if (entry == null || (now - entry.windowStart()) >= cooldownMillis) {
					return new RateLimitEntry(now, 1);
				}
				return new RateLimitEntry(entry.windowStart(), entry.count() + 1);
			});
			uploadHistory.entrySet().removeIf(e -> now - e.getValue().windowStart() > 2L * cooldownMillis);
		}
	}

	// Uses wssessionId (unique per page load) as the tracking key so that the
	// two-file flow's two uploads share a single counter independent of other
	// sessions from the same IP. Falls back to IP for requests without a session.
	private static String extractKey(HttpServletRequest request) {
		String sessionId = request.getParameter("wssessionId");
		if (sessionId != null && !sessionId.isBlank()) {
			return sessionId;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}
}
