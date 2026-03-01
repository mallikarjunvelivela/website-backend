package com.demo.fullstack_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.demo.fullstack_backend.dto.UserDto;
import com.demo.fullstack_backend.payload.LoginResponse;
import com.demo.fullstack_backend.security.JwtTokenProvider;
import com.demo.fullstack_backend.service.UserService;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> newUser(@RequestBody UserDto newUser){
        UserDto savedUser = userService.saveUser(newUser);
        String token = tokenProvider.generateToken(savedUser.getUsername());
        // Note: LoginResponse might still need User entity or be updated to use UserDto. 
        // Assuming LoginResponse can handle UserDto or we need to map it back if LoginResponse strictly expects User.
        // Let's check LoginResponse.
        return ResponseEntity.ok(new LoginResponse(token, savedUser));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginUser(@RequestBody Map<String, String> credentials) {
        String emailOrMobile = credentials.get("emailOrMobile");
        String password = credentials.get("password");
        LoginResponse result = userService.loginUser(emailOrMobile, password);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> payload) {
        String emailOrMobile = payload.get("emailOrMobile");
        String result = userService.forgotPassword(emailOrMobile);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("emailOrMobile");
        String otp = payload.get("otp");
        String result = userService.verifyOtp(email, otp);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("emailOrMobile");
        String newPassword = payload.get("newPassword");
        String result = userService.resetPassword(email, newPassword);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    List<UserDto> getAllUsers(){
        return userService.getAllUsers();
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id){
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/user/{id}")
    public ResponseEntity<UserDto> updateUser(@RequestBody UserDto newUser, @PathVariable Long id){
        return ResponseEntity.ok(userService.updateUser(newUser, id));
    }

    @PostMapping("/user")
    public ResponseEntity<UserDto> saveUser(@RequestBody UserDto newUser){
        return ResponseEntity.ok(userService.saveUser(newUser));
    }

    @DeleteMapping("/user/{id}")
    String deleteUser(@PathVariable Long id){
        return userService.deleteUser(id);
    }
}
