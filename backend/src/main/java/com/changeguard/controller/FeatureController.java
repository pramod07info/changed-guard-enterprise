package com.changeguard.controller;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.FeatureManifest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeatureController {
  private final FeatureFlags flags;

  public FeatureController(FeatureFlags flags) {
    this.flags = flags;
  }

  @GetMapping("/api/v1/features")
  public FeatureManifest features() {
    return flags.manifest();
  }
}
