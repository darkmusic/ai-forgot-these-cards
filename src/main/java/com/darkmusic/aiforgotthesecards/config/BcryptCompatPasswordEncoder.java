package com.darkmusic.aiforgotthesecards.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring's BCryptPasswordEncoder historically encoded as $2a$.
 * The SPA uses bcryptjs, which encodes as $2b$.
 *
 * Some environments/libraries can fail to verify $2b$ hashes. This encoder
 * tolerates $2b$/$2y$ by normalizing to $2a$ for verification.
 */
public final class BcryptCompatPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate;

    public BcryptCompatPasswordEncoder() {
        this.delegate = new BCryptPasswordEncoder();
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        // Fast path: works for $2a$ in all supported Spring versions.
        if (delegate.matches(rawPassword, encodedPassword)) {
            return true;
        }

        // Compatibility path: normalize $2b$/$2y$ -> $2a$ and try again.
        if (encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            String normalized = "$2a$" + encodedPassword.substring(4);
            return delegate.matches(rawPassword, normalized);
        }

        return false;
    }
}
