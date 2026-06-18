package com.ibrahim.helpdesk.user.controller;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }
}
