package com.mavela.backend.admin.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Converts only configured, signed Cognito group claims into the small fixed
 * permission set accepted by the admin API.
 */
@Component
public class AdminJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AdminAuthProperties properties;

    public AdminJwtAuthenticationConverter(AdminAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<AdminPermission> permissions = AdminPermission
                .fromTrustedGroups(groupsFrom(jwt));

        Collection<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(AdminPermission::authority)
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new JwtAuthenticationToken(
                jwt,
                authorities,
                jwt.getSubject()
        );
    }

    private List<String> groupsFrom(Jwt jwt) {
        Object claim = jwt.getClaims().get(properties.getGroupsClaim());

        if (claim instanceof Collection<?> values) {
            List<String> groups = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String group) {
                    groups.add(group);
                }
            }
            return groups;
        }

        if (claim instanceof String group) {
            return List.of(group);
        }

        return List.of();
    }
}
