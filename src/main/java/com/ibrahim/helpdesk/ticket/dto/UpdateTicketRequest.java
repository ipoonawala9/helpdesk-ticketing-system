package com.ibrahim.helpdesk.ticket.dto;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTicketRequest {

    private String title;
    private String description;
    private TicketCategory category;
}