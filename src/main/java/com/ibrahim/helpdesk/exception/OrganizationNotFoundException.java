package com.ibrahim.helpdesk.exception;

public class OrganizationNotFoundException
    extends RuntimeException {
        public OrganizationNotFoundException(Long id) {
            super("Organization with id " + id + " not found");
        }
    }

