package skillfactory.luzhin.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import skillfactory.luzhin.booking.config.JwtProperties;

import java.time.Clock;
import java.time.Instant;

/**
 * Выпускает короткоживущие JWT для внутренних вызовов Hotel Service.
 */
@Service
@RequiredArgsConstructor
public class InternalJwtService {

    private static final long TOKEN_VALIDITY_SECONDS = 300;
    private static final String SERVICE_SUBJECT = "booking-service";
    private static final String INTERNAL_ROLE = "INTERNAL";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * Выпускает служебный JWT с ролью INTERNAL и сроком действия пять минут.
     *
     * @return подписанный служебный JWT
     */
    public String issueToken() {
        Instant issuedAt = clock.instant();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(TOKEN_VALIDITY_SECONDS))
                .subject(SERVICE_SUBJECT)
                .claim("role", INTERNAL_ROLE)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
