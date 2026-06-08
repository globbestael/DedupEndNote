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

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	@Value("${dedup.upload-cooldown-seconds:10}")
	private int cooldownSeconds;

	private final ConcurrentHashMap<String, Long> uploadTimestamps = new ConcurrentHashMap<>();

	// Carries the client IP from preHandle to afterCompletion on the same thread.
	private static final ThreadLocal<String> pendingIp = new ThreadLocal<>();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		String ip = extractIp(request);
		long now = System.currentTimeMillis();
		long cooldownMillis = cooldownSeconds * 1000L;

		Long lastUpload = uploadTimestamps.get(ip);
		if (lastUpload != null && (now - lastUpload) < cooldownMillis) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType("application/json");
			response.getWriter().write(
					"{\"result\": \"ERROR: Too many uploads. Please wait a moment before uploading again.\"}");
			return false;
		}
		pendingIp.set(ip);
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
		String ip = pendingIp.get();
		pendingIp.remove();
		if (ip != null && response.getStatus() == HttpServletResponse.SC_OK) {
			long now = System.currentTimeMillis();
			uploadTimestamps.put(ip, now);
			uploadTimestamps.entrySet().removeIf(e -> now - e.getValue() > 2L * cooldownSeconds * 1000L);
		}
	}

	/*
	 * X-Forwarded-For is checked first to get the real client IP when running
	 * behind a reverse proxy. Note: this header can be spoofed if the app is
	 * exposed directly without a proxy — in that case, request.getRemoteAddr()
	 * alone would be more reliable.
	 */
	private static String extractIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}
}
