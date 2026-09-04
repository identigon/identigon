package org.identigon.effigies;

import java.sql.JDBCType;
import org.identigon.incognito.engine.SchemaInspector;

/**
 * Formats a column's discovered metadata (type, PK, FK) as the short human-readable annotation both
 * {@code discover} and {@code scaffold} print alongside it -- shared so the two commands can't
 * drift into describing the same column differently.
 */
final class ColumnMetadataFormatter {

  private ColumnMetadataFormatter() {}

  static String format(SchemaInspector.TableMetadata table, String col) {
    StringBuilder md = new StringBuilder(48);
    Integer typeCode = table.columnTypes().get(col);
    if (typeCode != null) {
      try {
        md.append("type: ").append(JDBCType.valueOf(typeCode).getName());
      } catch (IllegalArgumentException e) {
        md.append("type: ").append(typeCode);
      }
    }
    if (table.primaryKeyColumns().contains(col)) {
      md.append(", pk");
    }
    if (table.foreignKeys().containsKey(col)) {
      md.append(", fk -> ").append(table.foreignKeys().get(col));
    }
    return md.toString();
  }
}
