package com.darkmusic.aiforgotthesecards.web.controller;

import java.util.Map;

import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/api/user/{id}")
    public User saveUser(@PathVariable long id, @RequestBody User user) {
        if (userDAO.findById(id).isPresent()) {
            return userDAO.save(user);
        }
        return null;
    }

    @GetMapping("/api/user/{id}")
    public User getUser(@PathVariable long id) {
        return userDAO.findById(id).orElse(null);
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
    public User getUserByUsername(@PathVariable String username) {
        return userDAO.findByUsername(username).orElse(null);
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
