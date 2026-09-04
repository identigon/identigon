package org.identigon.incognito.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Handles database dialect-specific load mechanics, such as trigger isolation, batch rewriting,
 * insert syntax, and sequence resynchronisation.
 */
public interface DialectHandler {

  /**
   * Called before loading a table. Used to suppress foreign key enforcement and user triggers
   * (e.g., via session_replication_role or ALTER TABLE).
   *
   * @param targetConn the target connection performing the inserts
   * @param tableName the table about to be loaded
   * @throws SQLException if the suppression cannot be applied
   */
  void preLoadTable(Connection targetConn, String tableName) throws SQLException;

  /**
   * Returns the SQL snippet for INSERT.
   *
   * @param tableName the target table name
   * @param columns the list of columns to insert
   * @param hasIdentityPk whether the table has an identity primary key (needs OVERRIDING SYSTEM
   *     VALUE on Postgres)
   * @return the dialect-specific {@code INSERT} statement
   */
  String buildInsertSql(String tableName, List<String> columns, boolean hasIdentityPk);

  /**
   * Binds one column value onto a prepared {@code INSERT} statement, applying whatever
   * dialect-specific type coercion the bind itself needs. The default here is a plain {@code
   * setObject(index, value)} - correct ANSI SQL behaviour for any engine with no coercion quirk of
   * its own; a dialect that needs one (e.g. PostgreSQL binding a {@code String} as {@code
   * Types.OTHER} so it casts to the column's real type) overrides this instead of {@code
   * BulkDatabaseLoadStage} hardcoding one engine's rule for every engine.
   *
   * @param stmt the prepared {@code INSERT} statement being populated
   * @param index the 1-based JDBC parameter index
   * @param value the column value to bind, of any JDBC-representable type, including {@code null}
   * @throws SQLException if the bind itself fails
   */
  default void bindValue(PreparedStatement stmt, int index, Object value) throws SQLException {
    stmt.setObject(index, value);
  }

  /**
   * Called after loading a table. Used to restore foreign key enforcement and triggers.
   *
   * @param targetConn the target connection that performed the inserts
   * @param tableName the table that was loaded
   * @throws SQLException if enforcement cannot be restored
   */
  void postLoadTable(Connection targetConn, String tableName) throws SQLException;

  /**
   * Resynchronises the sequence for a table's primary key after data has been loaded.
   *
   * @param targetConn the target connection
   * @param tableName the loaded table
   * @param pkCol the identity/serial primary-key column whose sequence to resync
   * @throws SQLException if the resync fails
   */
  void resyncSequence(Connection targetConn, String tableName, String pkCol) throws SQLException;

  /**
   * Whether this dialect can suppress foreign-key enforcement on {@code targetConn} for the
   * placeholder inserts a cyclic-FK load performs (Pass 1). The owner-mode trigger fallback does
   * <em>not</em> disable FK enforcement, so cyclic loads need the privileged path (on PostgreSQL, a
   * superuser for {@code session_replication_role='replica'}). Returns {@code false} by default so
   * a dialect that can't guarantee it triggers a clear fail-fast rather than a confusing FK
   * violation mid-load.
   *
   * @param targetConn the target connection that would perform the placeholder inserts
   * @return {@code true} if FK enforcement can be suppressed on this connection
   * @throws SQLException if the capability cannot be probed
   */
  default boolean canDeferCyclicForeignKeys(Connection targetConn) throws SQLException {
    return false;
  }

  /**
   * Owner-mode degraded path (SPEC §9): when FK enforcement cannot be suppressed via a session
   * setting (no {@code SUPERUSER} for {@code session_replication_role}), capture and drop the
   * foreign-key constraints that reference any table in {@code cyclicParentTables}, so the cyclic
   * placeholder inserts (Pass 1) do not violate them. The caller recreates them once the clone is
   * consistent (after Pass 2). Returns an empty list and does nothing by default.
   *
   * @param targetConn the target connection (must own the tables to alter them)
   * @param cyclicParentTables the tables whose inbound FK constraints must be dropped
   * @return the dropped constraints, for later recreation
   * @throws SQLException if a constraint cannot be dropped (e.g. the role does not own the table)
   */
  default List<DroppedForeignKey> dropForeignKeysReferencing(
      Connection targetConn, java.util.Set<String> cyclicParentTables) throws SQLException {
    return List.of();
  }

  /**
   * Recreates foreign-key constraints previously dropped by {@link #dropForeignKeysReferencing}.
   * No-op by default.
   *
   * @param targetConn the target connection
   * @param dropped the constraints to recreate
   * @throws SQLException if a constraint cannot be recreated
   */
  default void recreateForeignKeys(Connection targetConn, List<DroppedForeignKey> dropped)
      throws SQLException {}

  /**
   * A foreign-key constraint captured before being dropped, so it can be recreated verbatim.
   *
   * @param tableName the table the constraint is on
   * @param constraintName the constraint's name
   * @param definition the constraint definition (e.g. {@code FOREIGN KEY (...) REFERENCES ...})
   */
  record DroppedForeignKey(String tableName, String constraintName, String definition) {}
}
