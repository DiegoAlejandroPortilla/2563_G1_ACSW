package org.dark.chatapp.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop50ByOrderByTimestampAsc(); // Recupera los últimos 50 mensajes ordenados por fecha
}