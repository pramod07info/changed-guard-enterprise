package com.changeguard.dto;

public record AuthConfiguration(String authorizationEndpoint, String tokenEndpoint, String logoutEndpoint, String clientId) {}
