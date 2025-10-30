package com.darkmusic.aiforgotthesecards.business.entities.repositories;

import com.darkmusic.aiforgotthesecards.business.entities.Card;
import com.darkmusic.aiforgotthesecards.business.entities.User;
import com.darkmusic.aiforgotthesecards.business.entities.UserCardSrs;
import jakarta.persistence.TypedQuery;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface UserCardSrsDAO extends CrudRepository<UserCardSrs, Long> {
    List<UserCardSrs> query(TypedQuery<UserCardSrs> query);

    Optional<UserCardSrs> findByUserAndCard(User user, Card card);

    List<UserCardSrs> findByUser(User user);
}
