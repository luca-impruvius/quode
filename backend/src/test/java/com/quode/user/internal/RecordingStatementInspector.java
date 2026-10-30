package com.quode.user.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

// Records every SQL statement Hibernate sends, so tests can check the real generated SQL.
public class RecordingStatementInspector implements StatementInspector {

  static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

  @Override
  public String inspect(String sql) {
    STATEMENTS.add(sql);
    return sql;
  }
}
