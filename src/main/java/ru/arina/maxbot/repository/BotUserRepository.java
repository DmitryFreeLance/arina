package ru.arina.maxbot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.arina.maxbot.domain.BotUser;
import ru.arina.maxbot.domain.UserRole;

import java.util.Collection;
import java.util.List;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {

    List<BotUser> findByRole(UserRole role);

    Page<BotUser> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<BotUser> findByIdIn(Collection<Long> ids);
}
