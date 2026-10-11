# OpenAPI import — phase 2

POST /api/v1/impact/openapi with service, before, after and optional dependencies.
before/after are embedded OpenAPI 3.0.x JSON objects. No URLs are fetched.
The existing normalized /api/v1/impact endpoint remains available.

From repository root with the backend running:

```sh
curl -H "Content-Type: application/json" --data-binary @examples/openapi-change.json http://localhost:8080/api/v1/impact/openapi
```

Findings include code, operation, schema location and explanation. Location
segments escape slash and tilde as JSON Pointer segments. Service-level graph
traversal supplies potential consumers and suggested consumer contract tests.

## Compatibility direction

| Change | Request | Response |
| --- | --- | --- |
| Type change | HIGH, conservative | HIGH, conservative |
| Required property added | HIGH | LOW |
| Required property removed | LOW | HIGH |
| Nullable true -> false | HIGH | LOW |
| Nullable false -> true | LOW | HIGH |
| Enum narrowed | HIGH | LOW |
| Enum widened or removed | LOW | HIGH |
| Documented property removed | Allowed by default additionalProperties | HIGH, conservative |

Operations removed, bodies removed, newly required bodies and changed status
sets require review. New optional response fields are LOW, though strict
consumers may reject them. Unchanged normalized operations are NONE.
Metadata-only changes do not affect risk. Risk is an advisory classification,
not a guarantee that consumers are compatible.

## Supported subset and limits

- Explicit primitive, object and array schemas; nested properties/items.
- required, nullable and primitive enum values.
- application/json request bodies and response bodies; bodyless responses.
- Standalone local #/components/schemas references, with escaped tokens.
- At most 500 paths, schema depth 32, document depth 64 and 50,000 nodes.

Unsupported or malformed analyzed constructs return HTTP 400. No partial
report is returned. This includes OpenAPI 3.1, YAML, external/recursive
references, reference siblings, parameters, security, response headers,
composition, format, validation constraints, additionalProperties, readOnly
and writeOnly. Unreferenced components and document metadata are not analyzed.
This is a bounded importer, not a full OpenAPI validator. Input must already
be a valid OpenAPI document within the described subset.

Next: expand constraint/parameter support with directional rules, then persist
contract snapshots and reports in PostgreSQL.
