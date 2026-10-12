package com.changeguard.controller;

import com.changeguard.dto.*;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.service.DatabaseAnalysisHistoryService;
import com.changeguard.service.DatabaseSchemaService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
public class DatabaseSchemaController {
  private static final int LIMIT = 2 * 1048576 + 65536;
  private final DatabaseSchemaService service;
  private final DatabaseAnalysisHistoryService history;
  private final JsonMapper json;

  public DatabaseSchemaController(
      DatabaseSchemaService service, DatabaseAnalysisHistoryService history, JsonMapper json) {
    this.service = service;
    this.history = history;
    this.json = json;
  }

  @PostMapping(value = "/impact/database", consumes = "application/json")
  public DatabaseSchemaReport preview(HttpServletRequest request) throws IOException {
    return service.analyze(body(request));
  }

  @PostMapping(value = "/database-analyses", consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public SavedDatabaseAnalysis save(HttpServletRequest request) throws IOException {
    return history.create(body(request));
  }

  @GetMapping("/database-analyses/{id}")
  public SavedDatabaseAnalysis get(@PathVariable UUID id) {
    return history.get(id);
  }

  @GetMapping("/database-analyses")
  public List<AnalysisSummary> list(
      @RequestParam String service,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return history.list(service, limit, offset);
  }

  private DatabaseSchemaRequest body(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > LIMIT)
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Schema request exceeds supported size");
    byte[] bytes = request.getInputStream().readNBytes(LIMIT + 1);
    if (bytes.length > LIMIT)
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Schema request exceeds supported size");
    try {
      return json.readerFor(DatabaseSchemaRequest.class)
          .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readValue(bytes);
    } catch (tools.jackson.core.JacksonException e) {
      throw new InvalidContractException(
          "Body must be a valid schema request using only service, before, after and dependencies");
    }
  }
}
