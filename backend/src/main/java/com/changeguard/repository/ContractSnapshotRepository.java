package com.changeguard.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.changeguard.entity.ContractSnapshot;

@Repository
public class ContractSnapshotRepository {
    private final JdbcTemplate jdbc;
    public ContractSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(ContractSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO contract_snapshot (id, service, created_at, document)
                VALUES (?, ?, ?, CAST(? AS jsonb))
                """, snapshot.id(), snapshot.service(), OffsetDateTime.ofInstant(snapshot.createdAt(), ZoneOffset.UTC),
                snapshot.documentJson());
    }

    public Optional<ContractSnapshot> findById(UUID id) {
        return jdbc.query("SELECT id, service, created_at, document::text FROM contract_snapshot WHERE id = ?",
                (rs, row) -> new ContractSnapshot(rs.getObject("id", UUID.class), rs.getString("service"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("document")), id)
                .stream().findFirst();
    }
}
