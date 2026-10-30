package app.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import app.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** bcrypt only reads the first 72 bytes, so cap the length there. */
    public record Credentials(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }

    private final AuthService auth;
    private final SecurityContextRepository contexts;

    public AuthController(AuthService auth, SecurityContextRepository contexts) {
        this.auth = auth;
        this.contexts = contexts;
    }

    /** Sets the XSRF-TOKEN cookie. The front-end calls this once before its first POST. */
    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> register(@Valid @RequestBody Credentials body) {
        return view(auth.register(body.email(), body.password()));
    }

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody Credentials body,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = auth.login(body.email(), body.password());
        // New session id at login, so a session id planted before login is worthless (session fixation).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return view((CurrentUser) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not_signed_in");
        }
        return view(user);
    }

    /** Only these fields ever leave the server; never return the password hash. */
    private static Map<String, Object> view(CurrentUser user) {
        return Map.of("id", user.id(), "email", user.email(), "role", user.role());
    }
}
