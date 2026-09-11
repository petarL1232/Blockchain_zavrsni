import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager implements AutoCloseable {

    private final Connection connection;

    public DatabaseManager(String databasePath) throws SQLException, IOException {

        Path path = Path.of(databasePath);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    public void initSchema() throws SQLException, IOException {

        String schema = Files.readString(Path.of("data", "Blockchain.sql"));

        try (Statement statement = connection.createStatement()) {

            String[] commands = schema.split(";");
            for (String command : commands) {
                if (!command.isBlank()) {
                    statement.execute(command);
                }
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}