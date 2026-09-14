package com.ibrahim.helpdesk.message.mapper;

import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.entity.Message;
import com.ibrahim.helpdesk.user.mapper.UserMapper;

public final class MessageMapper {

    private MessageMapper() {
    }

    public static MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        return new MessageResponse(
                message.getId(),
                message.getTicket().getId(),
                UserMapper.toSummary(message.getSender()),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
