package com.demo.fullstack_backend.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.demo.fullstack_backend.dto.UserDto;
import com.demo.fullstack_backend.exception.UserAlreadyExists;
import com.demo.fullstack_backend.exception.UserNotFoundException;
import com.demo.fullstack_backend.model.User;
import com.demo.fullstack_backend.payload.LoginResponse;
import com.demo.fullstack_backend.repository.UserRepository;
import com.demo.fullstack_backend.security.JwtTokenProvider;
import com.demo.fullstack_backend.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${github.token}")
    private String githubToken;

    @Value("${github.username}")
    private String githubUsername;

    @Value("${github.repo}")
    private String githubRepo;

    @Override
    public UserDto saveUser(UserDto userDto) {
        List<String> existingFields = new ArrayList<>();
        if (!userRepository.findByUsername(userDto.getUsername()).isEmpty()) {
            existingFields.add("Username");
        }
        if (!userRepository.findByEmail(userDto.getEmail()).isEmpty()) {
            existingFields.add("Email");
        }
        if (!userRepository.findByMobileNumber(userDto.getMobileNumber()).isEmpty()) {
            existingFields.add("Mobile number");
        }

        if (!existingFields.isEmpty()) {
            throw new UserAlreadyExists(String.join(", ", existingFields) + " already exists");
        }
        
        User user = new User();
        BeanUtils.copyProperties(userDto, user);
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        
        User savedUser = userRepository.save(user);
        UserDto savedUserDto = new UserDto();
        BeanUtils.copyProperties(savedUser, savedUserDto);
        return savedUserDto;
    }

    @Override
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(user -> {
            UserDto userDto = new UserDto();
            BeanUtils.copyProperties(user, userDto);
            return userDto;
        }).collect(Collectors.toList());
    }

    @Override
    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        UserDto userDto = new UserDto();
        BeanUtils.copyProperties(user, userDto);
        return userDto;
    }

    @Override
    public UserDto updateUser(UserDto userDto, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        user.setUsername(userDto.getUsername());
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        user.setMobileNumber(userDto.getMobileNumber());
        user.setDob(userDto.getDob());
        user.setGender(userDto.getGender());
        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        UserDto updatedUserDto = new UserDto();
        BeanUtils.copyProperties(updatedUser, updatedUserDto);
        return updatedUserDto;
    }

    @Override
    public String deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
        return "User with id " + id + " has been deleted success.";
    }

    @Override
    public LoginResponse loginUser(String usernameOrEmail, String password) {
        List<User> users = userRepository.findByUsernameOrEmail(usernameOrEmail.trim(), usernameOrEmail.trim());
        if (users.size() == 1) {
            User user = users.get(0);
            if (passwordEncoder.matches(password.trim(), user.getPassword())) {
                String token = tokenProvider.generateToken(user.getUsername());
                UserDto userDto = new UserDto();
                BeanUtils.copyProperties(user, userDto);
                return new LoginResponse(token, userDto);
            } else {
                throw new RuntimeException("Invalid credentials: Incorrect password.");
            }
        } else if (users.isEmpty()) {
            throw new RuntimeException("Invalid credentials: User not found.");
        } else {
            throw new RuntimeException("Invalid credentials: Ambiguous user. Multiple accounts found.");
        }
    }

    @Override
    public String forgotPassword(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Email cannot be empty.";
        }

        Optional<User> userOptional = userRepository.findFirstByEmailOrMobileNumber(email, email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String otp = generateOtp();
            user.setOtp(otp);
            userRepository.save(user);
            sendOtpToEmail(user.getEmail(), otp);
            return "OTP sent to " + user.getEmail();
        } else {
            return "User not found with email: " + email;
        }
    }

    @Override
    public String verifyOtp(String email, String otp) {
        if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
            return "Email and OTP cannot be empty.";
        }

        Optional<User> userOptional = userRepository.findFirstByEmailOrMobileNumber(email, email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (otp.equals(user.getOtp())) {
                user.setOtp(null); // Clear OTP after successful verification
                userRepository.save(user);
                return "OTP verified successfully.";
            } else {
                return "Invalid OTP.";
            }
        } else {
            return "User not found with email: " + email;
        }
    }

    @Override
    public String resetPassword(String email, String newPassword) {
        if (email == null || email.trim().isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
            return "Email and new password cannot be empty.";
        }

        Optional<User> userOptional = userRepository.findFirstByEmailOrMobileNumber(email, email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return "Password reset successfully.";
        } else {
            return "User not found with email: " + email;
        }
    }

    @Override
    public UserDto uploadImage(Long id, MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (file.isEmpty()) {
            throw new RuntimeException("Cannot upload empty file");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String fileName = id + "_" + UUID.randomUUID().toString() + fileExtension;

            String githubUrl = "https://api.github.com/repos/" + githubUsername + "/" + githubRepo + "/contents/user-images/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github+json");

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Upload user image " + fileName);
            body.put("content", Base64.getEncoder().encodeToString(file.getBytes()));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(githubUrl, HttpMethod.PUT, requestEntity, Map.class);

            Map<String, Object> content = (Map<String, Object>) response.getBody().get("content");
            String downloadUrl = (String) content.get("download_url");

            user.setImage(downloadUrl);
            User updatedUser = userRepository.save(user);
            UserDto userDto = new UserDto();
            BeanUtils.copyProperties(updatedUser, userDto);
            return userDto;
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            throw new RuntimeException("GitHub API Error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }

    @Override
    public byte[] getUserImage(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        String imageName = user.getImage();
        if (imageName == null || imageName.isEmpty()) {
            throw new RuntimeException("User has no image assigned.");
        }

        try {
            if (imageName.startsWith("http")) {
                RestTemplate restTemplate = new RestTemplate();
                if (imageName.contains("github") && githubToken != null && !githubToken.isEmpty()) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + githubToken);
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    ResponseEntity<byte[]> response = restTemplate.exchange(imageName, HttpMethod.GET, entity, byte[].class);
                    return response.getBody();
                }
                return restTemplate.getForObject(imageName, byte[].class);
            }

            // Fallback for local images
            String uploadDir = System.getProperty("user.dir") + "/uploads/user-images/";
            Path path = Paths.get(uploadDir + imageName);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the file. Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not fetch image. Error: " + e.getMessage());
        }
    }

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    private void sendOtpToEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your OTP for Password Reset");
        message.setText("Your OTP is: " + otp);
        mailSender.send(message);
    }
}
