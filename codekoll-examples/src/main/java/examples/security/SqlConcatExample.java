package examples.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Example for rule {@code CK-SQL-CONCAT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy(Statement, String)} builds a query by
 * concatenating {@code userId} into the SQL string.
 *
 * <p><b>What happens at runtime:</b> the variable becomes part of the STATEMENT. A userId
 * of {@code "1 OR 1=1"} returns every row; {@code "1; DROP TABLE users; --"} does what it
 * says. This is SQL injection — still the top web-application vulnerability, one
 * user-controlled string away.
 *
 * <p><b>How to fix it:</b> bind parameters, as {@link #fixed(Connection, String)} does —
 * the value can never become SQL.
 */
public class SqlConcatExample {

  public ResultSet buggy(Statement statement, String userId) throws SQLException {
    return statement.executeQuery("SELECT * FROM users WHERE id = " + userId); // :: CK-SQL-CONCAT
  }

  public ResultSet fixed(Connection connection, String userId) throws SQLException {
    PreparedStatement statement =
        connection.prepareStatement("SELECT * FROM users WHERE id = ?");
    statement.setString(1, userId);
    return statement.executeQuery();
  }
}
