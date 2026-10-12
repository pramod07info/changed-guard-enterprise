package com.changeguard.dto;

/** Effective capabilities; never secrets or operator credentials. */
public record FeatureManifest(
    boolean normalizedAnalysis,
    boolean openApiAnalysis,
    boolean analysisHistory,
    boolean catalog,
    boolean baselines,
    boolean databaseAnalysis,
    boolean databaseHistory,
    boolean dependencyAnalysis,
    boolean testRecommendations,
    boolean githubPr,
    boolean webhooks,
    boolean githubChecks,
    boolean metrics) {}
