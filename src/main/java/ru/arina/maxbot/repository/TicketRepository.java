package ru.arina.maxbot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.arina.maxbot.domain.Ticket;
import ru.arina.maxbot.domain.TicketStatus;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByStatusInOrderByCreatedAtDesc(Collection<TicketStatus> statuses, Pageable pageable);

    long countByRequesterId(Long requesterId);

    List<Ticket> findByStatusInOrderByCreatedAtDesc(Collection<TicketStatus> statuses);
}
