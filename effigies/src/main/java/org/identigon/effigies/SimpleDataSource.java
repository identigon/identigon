package org.identigon.effigies;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

class SimpleDataSource implements DataSource {
  private final String url;
  private final String user;
  private final String password;

  SimpleDataSource(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(url, connectionProperties(url, user, password));
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return DriverManager.getConnection(url, connectionProperties(url, username, password));
  }

  /**
   * The driver properties for a connection. For PostgreSQL this turns off {@code
   * logServerErrorDetail}: by default the driver copies the server's error detail into exception
   * messages, and that detail quotes row values ({@code Key (email)=(...) already exists}, {@code
   * Failing row contains (...)}) - which this CLI would then print (incognito SPEC §7.3).
   */
  static Properties connectionProperties(String url, String user, String password) {
    Properties props = new Properties();
    if (user != null) {
      props.setProperty("user", user);
    }
    if (password != null) {
      props.setProperty("password", password);
    }
    if (url.startsWith("jdbc:postgresql:")) {
      props.setProperty("logServerErrorDetail", "false");
    }
    return props;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException("Not a wrapper");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return null;
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {}

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {}

  @Override
  public int getLoginTimeout() throws SQLException {
    return 0;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }
}
