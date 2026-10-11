# Repository development conventions

- Use Java 21 and the existing Spring Boot Maven backend.
- Follow docs/backend-architecture.md for package responsibilities.
- Put HTTP endpoints in com.changeguard.controller, business rules in
  com.changeguard.service, transport records in com.changeguard.dto and
  error types/advice in com.changeguard.exception.
- Add com.changeguard.repository and com.changeguard.entity when persistence
  is implemented. Do not introduce placeholder repositories.
- Add a utility package only for shared stateless helpers with a concrete use.
- Use constructor injection and keep controllers thin.
- Use InvalidContractException for expected invalid contract input.
  Do not translate unexpected programming failures into HTTP 400.
- Match test packages to the tested layer and run backend Maven verify.
- Update docs/CHANGELOG.md for code changes. Update architecture documentation
  when boundaries or implementation status change.
- Keep credentials out of source and preserve existing API behavior unless
  a deliberate versioned change is documented.
