package com.pcd.manager.controller;

import com.pcd.manager.model.User;
import com.pcd.manager.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserApiController {

    private final UserService userService;

    @Autowired
    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> userDtos = userService.getAllUsers().stream()
            .map(u -> Map.of(
                "id", (Object) u.getId(),
                "name", (Object) (u.getName() != null ? u.getName() : ""),
                "email", (Object) (u.getEmail() != null ? u.getEmail() : ""),
                "role", (Object) (u.getRole() != null ? u.getRole() : "")
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(userDtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(u -> Map.of(
                    "id", (Object) u.getId(),
                    "name", (Object) (u.getName() != null ? u.getName() : ""),
                    "email", (Object) (u.getEmail() != null ? u.getEmail() : ""),
                    "role", (Object) (u.getRole() != null ? u.getRole() : "")
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}


