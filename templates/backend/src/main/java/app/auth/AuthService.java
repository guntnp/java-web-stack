package app.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.security.CurrentUser;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;

    public AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** New accounts are always USER; the role never comes from the request. */
    @Transactional
    public CurrentUser register(String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email_taken");
        }
        String hash = encoder.encode(password);
        long id = users.insert(normalized, hash, "USER");
        return new CurrentUser(id, normalized, hash, "USER");
    }

    /** One answer for a wrong email and a wrong password, so attackers cannot tell which accounts exist. */
    public Authentication login(String email, String password) {
        try {
            return authManager.authenticate(new UsernamePasswordAuthenticationToken(normalize(email), password));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
    }
}
