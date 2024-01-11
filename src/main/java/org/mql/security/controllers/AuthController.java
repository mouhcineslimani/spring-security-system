package org.mql.security.controllers;

import org.mql.security.controllers.dto.AuthenticationResponse;
import org.mql.security.controllers.dto.AuthentificationRequest;
import org.mql.security.controllers.dto.RegisterRequest;
import org.mql.security.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthentificationRequest request) {
        System.out.println(">>>>" + request.toString());
        return ResponseEntity.ok(authService.authenticate(request));

    }
}
