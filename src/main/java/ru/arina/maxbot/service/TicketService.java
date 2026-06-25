package ru.arina.maxbot.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.domain.BotUser;
import ru.arina.maxbot.domain.Ticket;
import ru.arina.maxbot.domain.TicketStatus;
import ru.arina.maxbot.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final BotProperties botProperties;

    public TicketService(TicketRepository ticketRepository, BotProperties botProperties) {
        this.ticketRepository = ticketRepository;
        this.botProperties = botProperties;
    }

    @Transactional
    public Ticket create(BotUser user) {
        Ticket ticket = new Ticket();
        ticket.setRequesterId(user.getId());
        ticket.setRequesterDisplayName(user.getDisplayName());
        ticket.setFullName(user.getDraftFullName());
        ticket.setCompanyName(user.getDraftCompanyName());
        ticket.setProblemType(user.getDraftProblemType());
        ticket.setDescription(user.getDraftDescription());
        return ticketRepository.save(ticket);
    }

    public Optional<Ticket> findById(Long ticketId) {
        return ticketRepository.findById(ticketId);
    }

    public Page<Ticket> pagedOpenTickets(int page) {
        return ticketRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(TicketStatus.NEW, TicketStatus.ACCEPTED),
                PageRequest.of(page, botProperties.getPageSize())
        );
    }

    public long countUserTickets(Long userId) {
        return ticketRepository.countByRequesterId(userId);
    }

    @Transactional
    public Ticket accept(Ticket ticket, BotUser admin) {
        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket.setAssignedAdminId(admin.getId());
        ticket.setAssignedAdminName(admin.getDisplayName());
        ticket.setAcceptedAt(Instant.now());
        ticket.setRejectionReason(null);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket reject(Ticket ticket, BotUser admin, String reason) {
        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setAssignedAdminId(admin.getId());
        ticket.setAssignedAdminName(admin.getDisplayName());
        ticket.setRejectedAt(Instant.now());
        ticket.setRejectionReason(reason);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket complete(Ticket ticket, BotUser admin) {
        ticket.setStatus(TicketStatus.COMPLETED);
        ticket.setAssignedAdminId(admin.getId());
        ticket.setAssignedAdminName(admin.getDisplayName());
        ticket.setCompletedAt(Instant.now());
        return ticketRepository.save(ticket);
    }
}
