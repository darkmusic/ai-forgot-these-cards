package com.darkmusic.aiforgotthesecards.web.controller;

import java.util.Map;

import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.UserAuthResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

@Getter
@RestController
public class UserController {
    private final UserDAO userDAO;

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    private static boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @PutMapping("/api/user/{id}")
    public User saveUser(@PathVariable long id, @RequestBody User user, Authentication authentication) {
        var existing = userDAO.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Always trust the path id over any body id.
        user.setId(existing.getId());

        if (!isAdmin(authentication)) {
            var me = userDAO.findByUsername(authentication == null ? null : authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (!me.getId().equals(existing.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            // Non-admins can only update their own non-privilege fields.
            user.setUsername(existing.getUsername());
            user.setAdmin(existing.isAdmin());
            user.setActive(existing.isActive());
            user.setDecks(existing.getDecks());
        }

        return userDAO.save(user);
    }

    @GetMapping("/api/user/{id}")
    public User getUser(@PathVariable long id, Authentication authentication) {
        var user = userDAO.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (isAdmin(authentication)) {
            return user;
        }

        var me = userDAO.findByUsername(authentication == null ? null : authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!me.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }

    @PostMapping("/api/user")
    @Secured("ROLE_ADMIN")
    public User addUser(@RequestBody User user) {
        return userDAO.save(user);
    }

    @DeleteMapping("/api/user/{id}")
    @Secured("ROLE_ADMIN")
    public void deleteUser(@PathVariable long id) {
        userDAO.deleteById(id);
    }

    @GetMapping("/api/user/all")
    @Secured("ROLE_ADMIN")
    public Iterable<User> getAllUsers() {
        return userDAO.findAll();
    }

    @GetMapping("/api/user/username/{username}")
    public User getUserByUsername(@PathVariable String username, Authentication authentication) {
        var requested = userDAO.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (isAdmin(authentication)) {
            return requested;
        }

        var me = userDAO.findByUsername(authentication == null ? null : authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!me.getId().equals(requested.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return requested;
    }

    @GetMapping("/api/current-user")
    public UserAuthResponse getCurrentUser(Authentication authentication) {
        var userAuthResponse = new UserAuthResponse();
        userAuthResponse.setAuthenticated(authentication != null);
        assert authentication != null;
        var authorities = authentication.getAuthorities();
        for (var authority : authorities) {
            userAuthResponse.getRoles().add(authority.getAuthority());
        }
        userAuthResponse.setUsername(authentication.getName());
        return userAuthResponse;
    }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        return Map.of(
            "headerName", token.getHeaderName(),
            "parameterName", token.getParameterName(),
            "token", token.getToken()
        );
    }
}
