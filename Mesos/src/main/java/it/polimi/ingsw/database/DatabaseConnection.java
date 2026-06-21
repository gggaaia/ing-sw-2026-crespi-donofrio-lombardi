package it.polimi.ingsw.database;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    private static String url;
    private static String user;
    private static String password;

    // loading password and url from the config.properties file
    static {
        Properties prop = new Properties();

        try (FileInputStream fis = new FileInputStream("config.properties")) {
            prop.load(fis);
            url = prop.getProperty("db.url");
            user = prop.getProperty("db.user");
            password = prop.getProperty("db.password");
        } catch (IOException e) {
            System.err.println("[DB ERROR] Unable to find or read the 'config.properties' file.");
            System.err.println("Ensure it is located in the project root.");
        }
    }


    /**
     * Establishes and returns a new active connection to the Supabase PostgreSQL database.
     * <p>
     * This method loads the required JDBC driver and attempts to connect using the
     * hardcoded credentials. The caller is responsible for closing the returned connection.
     *
     * @return an active {@link Connection} object to the remote database.
     * @throws SQLException if a database access error occurs or the PostgreSQL driver is missing.
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            throw new SQLException("[DB] Driver PostgreSQL not found in the classpath", e);
        }
    }
}
