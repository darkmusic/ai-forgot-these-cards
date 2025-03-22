package com.darkmusic.aiforgotthesecards.web.controller;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.repositories.UserDAO;
import com.darkmusic.aiforgotthesecards.web.contracts.UserAuthResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Getter
@RestController
public class UserController {
    private final UserDAO userDAO;

    @Autowired
    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @PutMapping("/api/user/{id}")
    public void saveUser(@PathVariable long id, @RequestBody User user) {
        if (userDAO.findById(id).isPresent()) {
            userDAO.save(user);
        }
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
}
