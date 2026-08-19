package com.backend.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    @Value($$"${jwt.secret}") private val jwtSecret: String,
): OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = extractToken(request)

        if(token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            val role = claims["role"] as? String
            if(role != "USER" && role != "ADMIN") {
                filterChain.doFilter(request, response)
                return
            }

            val userId = UUID.fromString(claims.subject)
            val principal = JwtUserPrincipal(userId = userId, role = role)
            val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, authorities)

            logger.debug("\n\t[DEBUG] [jwt_auth_filter][do_filter_internal] Authenticated user {} with role {}", userId, role)
        } catch(e: JwtException) {
            logger.warn("\n\t[WARN] [jwt_auth_filter][do_filter_internal] Invalid token: {}", e.message)
        } catch(_: IllegalArgumentException) {
            logger.warn("\n\t[WARN] [jwt_auth_filter][do_filter_internal] Malformed user id in token subject")
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if(header.startsWith("Bearer ")) header.substring(7) else null
    }
}