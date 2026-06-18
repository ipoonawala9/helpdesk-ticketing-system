package com.ibrahim.helpdesk.user.repository;

import com.ibrahim.helpdesk.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository
extends JpaRepository<User, Long>{

}
