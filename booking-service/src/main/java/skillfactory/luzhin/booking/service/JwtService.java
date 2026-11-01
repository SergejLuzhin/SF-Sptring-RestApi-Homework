package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import skillfactory.luzhin.booking.config.JwtProperties;
import skillfactory.luzhin.booking.dto.AuthResponse;
import skillfactory.luzhin.booking.entity.User;

import java.time.Clock;
import java.time.Instant;

/**
 * Выпускает подписанные JWT для пользователей.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final long TOKEN_VALIDITY_SECONDS = 3600;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * Выпускает JWT со сроком действия ровно один час.
     *
     * @param user пользователь, для которого выпускается токен
     * @return токен и параметры его использования
     */
    public AuthResponse issueToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(TOKEN_VALIDITY_SECONDS);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", TOKEN_VALIDITY_SECONDS);
    }
}
