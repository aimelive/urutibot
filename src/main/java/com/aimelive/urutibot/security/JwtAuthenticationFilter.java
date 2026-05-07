package com.aimelive.urutibot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Stateless JWT bearer-token filter.
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>No Authorization header</b> → request continues anonymously. The
 *       endpoint's authorization rules then decide whether anonymous is allowed
 *       (e.g. {@code /api/chatbot/**} permits it; {@code /api/appointments/**}
 *       does not).</li>
 *   <li><b>Valid Bearer token</b> → loads the {@link AppUserPrincipal} and
 *       binds it to {@link SecurityContextHolder} for this request thread.</li>
 *   <li><b>Malformed / expired / unknown-user token</b> → short-circuits with
 *       <b>401 Unauthorized</b>. We deliberately do <i>not</i> silently
 *       downgrade to anonymous here, because that produced confusing UX where
 *       a logged-in user with an expired token would be treated as anonymous
 *       by the chatbot and asked to "log in" while the UI still shows them
 *       as signed in. Returning 401 lets the frontend wrapper in api.js
 *       clear the stale token and prompt re-auth.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Empty bearer token");
            return;
        }

        try {
            Claims claims = jwtService.parse(token);
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                writeUnauthorized(response, "Token missing email claim");
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                if (log.isDebugEnabled()) {
                    log.debug("Authenticated request: user={} path={} method={}",
                            email, request.getRequestURI(), request.getMethod());
                }
            }
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT for path={}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Token has expired");
            return;
        } catch (JwtException e) {
            log.debug("Invalid JWT for path={}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Invalid token");
            return;
        } catch (UsernameNotFoundException e) {
            log.debug("JWT references unknown user for path={}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Unknown user");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                        "status", 401,
                        "error", "Unauthorized",
                        "message", message
                ))
        );
    }
}
