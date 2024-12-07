import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

import org.mindrot.jbcrypt.BCrypt;

public class Main {
    private static final String DATABASE_URL = "jdbc:sqlite:car_inventory.db";

    public static void main(String[] args) {
        enableWALMode();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            clearScreen();
            System.out.println("Welcome to the Vehicle Inventory System!");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.print("Please select an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    login(scanner);
                    break;
                case "2":
                    register(scanner);
                    break;
                case "3":
                    System.out.println("Goodbye!");
                    scanner.close();
                    System.exit(0);
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void enableWALMode() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            conn.createStatement().execute("PRAGMA journal_mode=WAL;");
        } catch (Exception e) {
            System.out.println("An error occurred while enabling WAL mode: " + e.getMessage());
        }
    }

    private static void login(Scanner scanner) {
        clearScreen();
        System.out.println("=== Login ===");
        System.out.print("Enter your email: ");
        String email = scanner.nextLine();

        String password = getPassword("Enter your password: ");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT u.id, s.saltpw, a.id AS admin_id FROM Users u " +
                           "LEFT JOIN Saltpw s ON u.id = s.id " +
                           "LEFT JOIN Admin a ON u.id = a.id " +
                           "WHERE u.email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, email);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String hashedPassword = rs.getString("saltpw");
                        if (BCrypt.checkpw(password, hashedPassword)) {
                            int userId = rs.getInt("id");
                            boolean isAdmin = rs.getInt("admin_id") != 0;
                            clearScreen();
                            System.out.println("Login successful! Welcome back!");
                            if (isAdmin) {
                                adminMenu(scanner, userId);
                            } else {
                                userMenu(scanner, userId);
                            }
                        } else {
                            System.out.println("Invalid password.");
                        }
                    } else {
                        System.out.println("User not found.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    private static void register(Scanner scanner) {
        clearScreen();
        System.out.println("=== Register ===");
        System.out.print("Enter your email: ");
        String email = scanner.nextLine();

        System.out.print("Enter your first name: ");
        String firstName = scanner.nextLine();

        System.out.print("Enter your last name: ");
        String lastName = scanner.nextLine();

        String password = getPassword("Enter a password: ");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String salt = BCrypt.gensalt();
            String hashedPassword = BCrypt.hashpw(password, salt);

            String userQuery = "INSERT INTO Users (email, firstname, lastname) VALUES (?, ?, ?)";
            try (PreparedStatement userStmt = conn.prepareStatement(userQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
                userStmt.setString(1, email);
                userStmt.setString(2, firstName);
                userStmt.setString(3, lastName);

                int rowsInserted = userStmt.executeUpdate();
                if (rowsInserted > 0) {
                    try (ResultSet generatedKeys = userStmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int userId = generatedKeys.getInt(1);

                            String saltpwQuery = "INSERT INTO Saltpw (id, saltpw) VALUES (?, ?)";
                            try (PreparedStatement saltpwStmt = conn.prepareStatement(saltpwQuery)) {
                                saltpwStmt.setInt(1, userId);
                                saltpwStmt.setString(2, hashedPassword);
                                saltpwStmt.executeUpdate();
                            }

                            System.out.println("Registration successful! You can now log in.");
                        }
                    }
                } else {
                    System.out.println("Registration failed. Please try again.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    private static void userMenu(Scanner scanner, int userId) {
        while (true) {
            clearScreen();
            System.out.println("\n=== User Menu ===");
            System.out.println("1. View Vehicle Inventory");
            System.out.println("2. Logout");
            System.out.print("Please select an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    viewVehicleInventory(scanner, userId);
                    break;
                case "2":
                    System.out.println("Logged out successfully.");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void viewVehicleInventory(Scanner scanner, int userId) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT id, make, model, variant, price, mileage FROM Vehicles";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Vehicle Inventory ===");
                while (rs.next()) {
                    System.out.printf("ID: %d, Make: %s, Model: %s, Variant: %s, Price: %d, Mileage: %d\n",
                            rs.getInt("id"), rs.getString("make"), rs.getString("model"), rs.getString("variant"),
                            rs.getInt("price"), rs.getInt("mileage"));
                }

                System.out.println("\nOptions:");
                System.out.println("1. Go Back");
                System.out.println("2. See Details of a Vehicle");
                System.out.print("Please select an option: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1":
                        return;
                    case "2":
                        System.out.print("Enter the ID of the vehicle you want to see (or type 'back' to cancel): ");
                        String input = scanner.nextLine();
                        if (input.equalsIgnoreCase("back")) {
                            return;
                        }
                        int vehicleId = Integer.parseInt(input);
                        viewVehicleDetails(scanner, userId, vehicleId);
                        break;
                    default:
                        System.out.println("Invalid option. Returning to the previous menu.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while retrieving the vehicle inventory: " + e.getMessage());
        }
    }

    private static void viewVehicleDetails(Scanner scanner, int userId, int vehicleId) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT * FROM Vehicles WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, vehicleId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.printf("ID: %d\nMake: %s\nModel: %s\nVariant: %s\nRegistration: %s\nCategory: %s\nPrice: %d\nMileage: %d\nFuel Type: %s\nSize: %d\nEngine Position: %s\nPower: %d\nDrivetrain: %s\nTransmission: %s\nColour: %s\nInterior Colour: %s\nEngine Type: %s\nNotes: %s\n",
                                rs.getInt("id"), rs.getString("make"), rs.getString("model"), rs.getString("variant"),
                                rs.getString("registration"), rs.getString("category"), rs.getInt("price"),
                                rs.getInt("mileage"), rs.getString("fueltype"), rs.getInt("size"),
                                rs.getString("engineposition"), rs.getInt("power"), rs.getString("drivetrain"),
                                rs.getString("transmission"), rs.getString("colour"), rs.getString("interiorcolour"),
                                rs.getString("enginetype"), rs.getString("notes"));

                        System.out.println("\nWould you like to make an appointment to see this vehicle?");
                        System.out.print("Enter 'yes' to make an appointment, 'no' to go back: ");
                        String choice = scanner.nextLine().toLowerCase();

                        if (choice.equals("yes")) {
                            System.out.print("Enter appointment date (YYYY-MM-DD): ");
                            String appointmentDate = scanner.nextLine();
                            makeAppointment(conn, userId, vehicleId, appointmentDate);
                        } else {
                            System.out.println("Returning to the previous menu.");
                        }
                    } else {
                        System.out.println("Vehicle not found.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while retrieving vehicle details: " + e.getMessage());
        }
    }

    private static void makeAppointment(Connection conn, int userId, int vehicleId, String date) {
        try {
            String query = "INSERT INTO Appointments (car_id, user_id, date) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, vehicleId);
                stmt.setInt(2, userId);
                stmt.setString(3, date);

                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("Appointment created successfully!");
                } else {
                    System.out.println("Failed to create an appointment.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while creating the appointment: " + e.getMessage());
        }
    }

    private static void adminMenu(Scanner scanner, int adminId) {
        while (true) {
            clearScreen();
            System.out.println("\n=== Admin Menu ===");
            System.out.println("1. Add a Vehicle");
            System.out.println("2. Delete a Vehicle");
            System.out.println("3. Add a User");
            System.out.println("4. Make a User Admin");
            System.out.println("5. Delete a User");
            System.out.println("6. View Appointments");
            System.out.println("7. Delete an Appointment");
            System.out.println("8. Logout");
            System.out.print("Please select an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    addVehicle(scanner);
                    break;
                case "2":
                    deleteVehicle(scanner);
                    break;
                case "3":
                    addUser(scanner);
                    break;
                case "4":
                    makeUserAdmin(scanner);
                    break;
                case "5":
                    deleteUser(scanner);
                    break;
                case "6":
                    viewAppointments(scanner);
                    break;
                case "7":
                    deleteAppointment(scanner);
                    break;
                case "8":
                    System.out.println("Logged out successfully.");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void addVehicle(Scanner scanner) {
        System.out.println("=== Add a Vehicle ===");
        System.out.println("Please provide the following details:");

        System.out.print("Enter Make: ");
        String make = scanner.nextLine();

        System.out.print("Enter Model: ");
        String model = scanner.nextLine();

        System.out.print("Enter Variant: ");
        String variant = scanner.nextLine();

        System.out.print("Enter Registration Date (YYYY-MM-DD): ");
        String registration = scanner.nextLine();

        System.out.print("Enter Category: ");
        String category = scanner.nextLine();

        System.out.print("Enter Price: ");
        int price = Integer.parseInt(scanner.nextLine());

        System.out.print("Enter Mileage: ");
        int mileage = Integer.parseInt(scanner.nextLine());

        System.out.print("Enter Fuel Type: ");
        String fuelType = scanner.nextLine();

        System.out.print("Enter Size: ");
        int size = Integer.parseInt(scanner.nextLine());

        System.out.print("Enter Engine Position: ");
        String enginePosition = scanner.nextLine();

        System.out.print("Enter Power: ");
        int power = Integer.parseInt(scanner.nextLine());

        System.out.print("Enter Drivetrain: ");
        String drivetrain = scanner.nextLine();

        System.out.print("Enter Transmission: ");
        String transmission = scanner.nextLine();

        System.out.print("Enter Colour: ");
        String colour = scanner.nextLine();

        System.out.print("Enter Interior Colour: ");
        String interiorColour = scanner.nextLine();

        System.out.print("Enter Engine Type: ");
        String engineType = scanner.nextLine();

        System.out.print("Enter Notes: ");
        String notes = scanner.nextLine();

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "INSERT INTO Vehicles (make, model, variant, registration, category, price, mileage, fueltype, size, engineposition, power, drivetrain, transmission, colour, interiorcolour, enginetype, notes) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, make);
                stmt.setString(2, model);
                stmt.setString(3, variant);
                stmt.setString(4, registration);
                stmt.setString(5, category);
                stmt.setInt(6, price);
                stmt.setInt(7, mileage);
                stmt.setString(8, fuelType);
                stmt.setInt(9, size);
                stmt.setString(10, enginePosition);
                stmt.setInt(11, power);
                stmt.setString(12, drivetrain);
                stmt.setString(13, transmission);
                stmt.setString(14, colour);
                stmt.setString(15, interiorColour);
                stmt.setString(16, engineType);
                stmt.setString(17, notes);

                int rowsInserted = stmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("Vehicle added successfully!");
                } else {
                    System.out.println("Failed to add the vehicle.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while adding the vehicle: " + e.getMessage());
        }
    }

    private static void deleteVehicle(Scanner scanner) {
        System.out.println("=== Delete a Vehicle ===");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT id, make, model, variant FROM Vehicles";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nAvailable Vehicles:");
                while (rs.next()) {
                    System.out.printf("ID: %d, Make: %s, Model: %s, Variant: %s\n",
                            rs.getInt("id"), rs.getString("make"), rs.getString("model"), rs.getString("variant"));
                }
            }

            System.out.print("\nEnter the ID of the vehicle to delete (or type 'back' to cancel): ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("back")) {
                return;
            }
            int vehicleId = Integer.parseInt(input);

            String deleteQuery = "DELETE FROM Vehicles WHERE id = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.setInt(1, vehicleId);

                int rowsDeleted = deleteStmt.executeUpdate();
                if (rowsDeleted > 0) {
                    System.out.println("Vehicle deleted successfully!");
                } else {
                    System.out.println("Vehicle not found.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while deleting the vehicle: " + e.getMessage());
        }
    }

    private static void addUser(Scanner scanner) {
        System.out.println("=== Add a User ===");
        register(scanner);
    }

    private static void makeUserAdmin(Scanner scanner) {
        System.out.println("=== Make a User Admin ===");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT id, email FROM Users";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nAvailable Users:");
                while (rs.next()) {
                    System.out.printf("ID: %d, Email: %s\n", rs.getInt("id"), rs.getString("email"));
                }
            }

            System.out.print("\nEnter the ID of the user to promote to admin (or type 'back' to cancel): ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("back")) {
                return;
            }
            int userId = Integer.parseInt(input);

            String promoteQuery = "INSERT INTO Admin (id) VALUES (?)";
            try (PreparedStatement promoteStmt = conn.prepareStatement(promoteQuery)) {
                promoteStmt.setInt(1, userId);

                int rowsInserted = promoteStmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("User promoted to admin successfully!");
                } else {
                    System.out.println("Failed to promote user to admin.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while promoting the user: " + e.getMessage());
        }
    }

    private static void viewAppointments(Scanner scanner) {
        System.out.println("=== View Appointments ===");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT a.id, a.date, v.make, v.model, u.email " +
                           "FROM Appointments a " +
                           "JOIN Vehicles v ON a.car_id = v.id " +
                           "JOIN Users u ON a.user_id = u.id";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nAppointments:");
                while (rs.next()) {
                    System.out.printf("Appointment ID: %d, Date: %s, Vehicle: %s %s, User: %s\n",
                            rs.getInt("id"), rs.getString("date"), rs.getString("make"), rs.getString("model"),
                            rs.getString("email"));
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while retrieving appointments: " + e.getMessage());
        }
    }

    private static void deleteAppointment(Scanner scanner) {
        System.out.println("=== Delete an Appointment ===");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT a.id, a.date, v.make, v.model, u.email " +
                           "FROM Appointments a " +
                           "JOIN Vehicles v ON a.car_id = v.id " +
                           "JOIN Users u ON a.user_id = u.id";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nAppointments:");
                while (rs.next()) {
                    System.out.printf("ID: %d, Date: %s, Vehicle: %s %s, User: %s\n",
                            rs.getInt("id"), rs.getString("date"), rs.getString("make"), rs.getString("model"),
                            rs.getString("email"));
                }
            }

            System.out.print("\nEnter the ID of the appointment to delete (or type 'back' to cancel): ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("back")) {
                return;
            }
            int appointmentId = Integer.parseInt(input);

            String deleteQuery = "DELETE FROM Appointments WHERE id = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.setInt(1, appointmentId);

                int rowsDeleted = deleteStmt.executeUpdate();
                if (rowsDeleted > 0) {
                    System.out.println("Appointment deleted successfully!");
                } else {
                    System.out.println("Appointment not found.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while deleting the appointment: " + e.getMessage());
        }
    }

    private static void deleteUser(Scanner scanner) {
        System.out.println("=== Delete a User ===");

        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            String query = "SELECT id, email FROM Users";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nAvailable Users:");
                while (rs.next()) {
                    System.out.printf("ID: %d, Email: %s\n", rs.getInt("id"), rs.getString("email"));
                }
            }

            System.out.print("\nEnter the ID of the user to delete (or type 'back' to cancel): ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("back")) {
                return;
            }
            int userId = Integer.parseInt(input);

            String deleteQuery = "DELETE FROM Users WHERE id = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.setInt(1, userId);

                int rowsDeleted = deleteStmt.executeUpdate();
                if (rowsDeleted > 0) {
                    System.out.println("User deleted successfully!");
                } else {
                    System.out.println("User not found.");
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred while deleting the user: " + e.getMessage());
        }
    }

    // Clear screen for Linux, macOS, and Windows
    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("Error clearing the screen: " + e.getMessage());
        }
    }

    // Hide password input
    private static String getPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] passwordArray = console.readPassword(prompt);
            return new String(passwordArray);
        } else {
            // Fallback for IDEs that do not support Console
            Scanner scanner = new Scanner(System.in);
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }
}

