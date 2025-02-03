package org.dark.chatapp.chat;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RestController
@RequestMapping("/api/messages") // Endpoint para historial
public class ChatController {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Lista de usuarios conectados
    private final Set<String> activeUsers = new HashSet<>();

    public ChatController(MessageRepository messageRepository, SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        messageRepository.save(Message.builder()
                .type(chatMessage.getType())
                .sender(chatMessage.getSender())
                .content(chatMessage.getContent())
                .timestamp(LocalDateTime.now())
                .build());
        return chatMessage;
    }

    @MessageMapping("/chat.privateMessage")
    public void sendPrivateMessage(@Payload ChatMessage chatMessage) {
        String recipient = chatMessage.getRecipient();

        if (recipient == null || recipient.isEmpty()) {
            System.out.println("❌ Error: El destinatario está vacío.");
            return;
        }

        chatMessage.setType(MessageType.PRIVATE);

        // Guardar el mensaje en la base de datos
        messageRepository.save(Message.builder()
                .type(MessageType.PRIVATE)
                .sender(chatMessage.getSender())
                .recipient(recipient)
                .content(chatMessage.getContent())
                .timestamp(LocalDateTime.now())
                .build());

        System.out.println("✅ Mensaje privado recibido en backend: " + chatMessage.getSender() + " → " + recipient);
        System.out.println("📨 Contenido: " + chatMessage.getContent());

        // Enviar el mensaje solo al destinatario correcto
        messagingTemplate.convertAndSendToUser(recipient, "/queue/messages", chatMessage);
        System.out.println("📩 Mensaje enviado a /user/" + recipient + "/queue/messages");
    }



    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        activeUsers.add(chatMessage.getSender());

        messagingTemplate.convertAndSend("/topic/activeUsers", activeUsers);

        return chatMessage;
    }

    @MessageMapping("/chat.removeUser")
    @SendTo("/topic/public")
    public ChatMessage removeUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        headerAccessor.getSessionAttributes().remove("username");
        activeUsers.remove(chatMessage.getSender());

        messagingTemplate.convertAndSend("/topic/activeUsers", activeUsers);

        return ChatMessage.builder()
                .type(MessageType.LEAVE)
                .sender(chatMessage.getSender())
                .content(chatMessage.getSender() + " ha salido del chat")
                .build();
    }

    @GetMapping("/users")
    public Set<String> getActiveUsers() {
        return activeUsers;
    }

    @GetMapping
    public List<Message> getChatHistory() {
        return messageRepository.findTop50ByOrderByTimestampAsc();
    }
}