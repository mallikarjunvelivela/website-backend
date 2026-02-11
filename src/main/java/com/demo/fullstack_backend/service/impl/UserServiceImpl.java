package com.demo.fullstack_backend.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
            // Define the directory where images will be stored
            String uploadDir = System.getProperty("user.dir") + "/uploads/user-images/";
            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String fileName = id + "_" + UUID.randomUUID().toString() + fileExtension;

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            user.setImage(fileName);
            User updatedUser = userRepository.save(user);
            UserDto userDto = new UserDto();
            BeanUtils.copyProperties(updatedUser, userDto);
            return userDto;
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

        String uploadDir = System.getProperty("user.dir") + "/uploads/user-images/";
        Path path = Paths.get(uploadDir + imageName);

        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the file. Error: " + e.getMessage());
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
