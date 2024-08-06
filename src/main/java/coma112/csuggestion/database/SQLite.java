package coma112.csuggestion.database;

import coma112.csuggestion.CSuggestion;
import coma112.csuggestion.events.SuggestionCreatedEvent;
import coma112.csuggestion.events.SuggestionForwardedEvent;
import coma112.csuggestion.managers.Suggestion;
import coma112.csuggestion.utils.SuggestionLogger;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SQLite extends AbstractDatabase {
    private final Connection connection;

    public SQLite() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        File dataFolder = new File(CSuggestion.getInstance().getDataFolder(), "suggestions.db");
        String url = "jdbc:sqlite:" + dataFolder;
        connection = DriverManager.getConnection(url);
    }

    @Override
    public boolean isConnected() {
        return connection != null;
    }

    @Override
    public void disconnect() {
        if (isConnected()) {
            try {
                connection.close();
            } catch (SQLException exception) {
                SuggestionLogger.error(exception.getMessage());
            }
        }
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS suggestions (ID INTEGER PRIMARY KEY, PLAYER VARCHAR(255) NOT NULL, SUGGESTION_TEXT VARCHAR(255) NOT NULL)";

        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            preparedStatement.execute();
        } catch (SQLException exception) {
            SuggestionLogger.error(exception.getMessage());
        }
    }

    @Override
    public void createSuggestion(@NotNull Player player, @NotNull String suggestionText) {
        String query = "INSERT INTO suggestions (PLAYER, SUGGESTION_TEXT) VALUES (?, ?)";

        try {
            try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
                preparedStatement.setString(1, player.getName());
                preparedStatement.setString(2, suggestionText);
                preparedStatement.executeUpdate();
                CSuggestion.getInstance().getServer().getPluginManager().callEvent(new SuggestionCreatedEvent(player, suggestionText));
            }
        } catch (SQLException exception) {
            SuggestionLogger.error(exception.getMessage());
        }
    }

    @Override
    public List<Suggestion> getSuggestions() {
        List<Suggestion> suggestions = new ArrayList<>();
        String query = "SELECT * FROM suggestions";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String player = resultSet.getString("PLAYER");
                String suggestion = resultSet.getString("SUGGESTION_TEXT");
                suggestions.add(new Suggestion(id, player, suggestion));
            }
        } catch (SQLException exception) {
            SuggestionLogger.error(exception.getMessage());
        }

        return suggestions;
    }

    @Override
    public String getPlayer(@NotNull Suggestion suggestion) {
        String query = "SELECT PLAYER FROM suggestions WHERE ID = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setInt(1, suggestion.id());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) return resultSet.getString("PLAYER");
        } catch (SQLException exception) {
            SuggestionLogger.error(exception.getMessage());
        }

        return "";
    }

    @Override
    public void deleteSuggestion(int id) {
        String selectQuery = "SELECT * FROM suggestions WHERE ID = ?";
        String deleteQuery = "DELETE FROM suggestions WHERE ID = ?";

        try (PreparedStatement selectStatement = getConnection().prepareStatement(selectQuery)) {
            selectStatement.setInt(1, id);
            ResultSet resultSet = selectStatement.executeQuery();

            if (resultSet.next()) {
                Player player = Bukkit.getPlayerExact(resultSet.getString("PLAYER"));
                String suggestion = resultSet.getString("SUGGESTION_TEXT");

                CSuggestion.getInstance().getServer().getPluginManager().callEvent(new SuggestionForwardedEvent(player, suggestion));
            }

            try (PreparedStatement deleteStatement = getConnection().prepareStatement(deleteQuery)) {
                deleteStatement.setInt(1, id);
                deleteStatement.executeUpdate();
            }

        } catch (SQLException exception) {
            SuggestionLogger.error(exception.getMessage());
        }
    }

    @Override
    public void reconnect() {
        try {
            if (getConnection() != null && !getConnection().isClosed()) getConnection().close();
            new SQLite();
        } catch (SQLException | ClassNotFoundException exception) {
            SuggestionLogger.error(exception.getMessage());
        }
    }
}