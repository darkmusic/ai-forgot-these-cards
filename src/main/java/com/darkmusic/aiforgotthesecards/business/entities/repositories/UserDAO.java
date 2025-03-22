package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.User;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface UserDAO extends CrudRepository<User, Long> {
    List<User> query(TypedQuery<User> query);

    Optional<User> findByUsername(String username);
}
