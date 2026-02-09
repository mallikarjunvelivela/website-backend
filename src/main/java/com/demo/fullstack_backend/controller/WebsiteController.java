package com.demo.fullstack_backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.demo.fullstack_backend.dto.WebsiteDto;
import com.demo.fullstack_backend.model.Website;
import com.demo.fullstack_backend.repository.WebsiteRepository;

@RestController
public class WebsiteController {

    @Autowired
    private WebsiteRepository websiteRepository;

    @GetMapping("/")
    public String home() {
        return "Welcome to the Fullstack Backend API!";
    }

    @PostMapping("/website")
    public WebsiteDto createWebsite(@RequestBody WebsiteDto newWebsiteDto) {
        Website website = new Website();
        BeanUtils.copyProperties(newWebsiteDto, website);
        website.setCreatedAt(LocalDateTime.now());
        website.setUpdatedAt(LocalDateTime.now());
        Website savedWebsite = websiteRepository.save(website);
        WebsiteDto savedWebsiteDto = new WebsiteDto();
        BeanUtils.copyProperties(savedWebsite, savedWebsiteDto);
        return savedWebsiteDto;
    }

    @GetMapping("/websites")
    public List<WebsiteDto> getAllWebsites() {
        return websiteRepository.findAll().stream().map(website -> {
            WebsiteDto websiteDto = new WebsiteDto();
            BeanUtils.copyProperties(website, websiteDto);
            return websiteDto;
        }).collect(Collectors.toList());
    }

    @GetMapping("/website/{id}")
    public WebsiteDto getWebsiteById(@PathVariable Long id) {
        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found with id: " + id));
        WebsiteDto websiteDto = new WebsiteDto();
        BeanUtils.copyProperties(website, websiteDto);
        return websiteDto;
    }

    @PutMapping("/website/{id}")
    public WebsiteDto updateWebsite(@RequestBody WebsiteDto newWebsiteDto, @PathVariable Long id) {
        Website website = websiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found with id: " + id));

        website.setName(newWebsiteDto.getName());
        website.setLogo(newWebsiteDto.getLogo());
        website.setPrimaryColor(newWebsiteDto.getPrimaryColor());
        website.setSecondaryColor(newWebsiteDto.getSecondaryColor());
        website.setActive(newWebsiteDto.isActive());
        website.setUpdatedAt(LocalDateTime.now());
        
        Website updatedWebsite = websiteRepository.save(website);
        WebsiteDto updatedWebsiteDto = new WebsiteDto();
        BeanUtils.copyProperties(updatedWebsite, updatedWebsiteDto);
        return updatedWebsiteDto;
    }

    @DeleteMapping("/website/{id}")
    public String deleteWebsite(@PathVariable Long id) {
        if (!websiteRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website not found with id: " + id);
        }
        websiteRepository.deleteById(id);
        return "Website with id " + id + " has been deleted successfully.";
    }
}
