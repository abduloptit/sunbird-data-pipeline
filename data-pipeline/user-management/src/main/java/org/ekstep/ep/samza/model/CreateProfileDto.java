package org.ekstep.ep.samza.model;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.sql.*;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Map;

public class CreateProfileDto implements IModel {
    public static final String UID = "uid";
    public static final String HANDLE = "handle";
    public static final String GENDER = "gender";
    public static final String LANGUAGE = "language";
    public static final String AGE = "age";
    public static final String STANDARD = "standard";
    public static final String DAY = "day";
    public static final String MONTH = "month";
    private final String IS_GROUP_USER = "is_group_user";
    private String uid;
    private String handle;
    private String gender;
    private Integer yearOfBirth;
    private Integer age;
    private Integer standard;
    private String language;
    private Integer day;
    private Integer month;
    private Boolean isGroupUser;

    private Timestamp createdAt;

    private Timestamp updatedAt;
    private boolean isInserted = false;
    private DataSource dataSource;


    public CreateProfileDto(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void process(Event event) throws SQLException, ParseException {
        Map<String, Object> EKS = event.getEks();
        java.util.Date timeOfEvent = event.getTs();
        parseData(EKS, timeOfEvent);

        if (!isLearnerExist((String) EKS.get(UID))) {
            createLearner(event);
        }
        saveData();
    }

    private void parseData(Map<String, Object> EKS, java.util.Date timeOfEvent) throws ParseException {
        uid = (String) EKS.get(UID);
        validateEmptyString(UID, uid);

        handle = (String) EKS.get(HANDLE);
        validateEmptyString(HANDLE, handle);

        gender = (String) EKS.get(GENDER);

        age = getIntegerValueFromDouble(EKS, AGE);
        yearOfBirth = getYear(EKS, timeOfEvent);
        standard = getIntegerValueFromDouble(EKS, STANDARD);
        language = (String) EKS.get(LANGUAGE);
        day = getIntegerValueFromDouble(EKS, DAY);
        month = getIntegerValueFromDouble(EKS, MONTH);

        isGroupUser = getBoolean(EKS, IS_GROUP_USER);

        java.util.Date date = new java.util.Date();
        createdAt = new Timestamp(date.getTime());
        updatedAt = new Timestamp(date.getTime());
    }

    private Boolean getBoolean(Map<String, Object> EKS, String key) {
        Object value = EKS.get(key);
        return value == null ? false : (Boolean) value;
    }

    private void validateEmptyString(String name, String value) throws ParseException {
        if (value == null || value.isEmpty()) throw new ParseException(String.format("%s can't be blank", name), 1);
    }

    private Integer getIntegerValueFromDouble(Map<String, Object> EKS, String name) {
        if (!EKS.containsKey(name)) {
            return null;
        }
        return ((Double) EKS.get(name)).intValue() != -1
                ? (Integer) ((Double) EKS.get(name)).intValue()
                : null;
    }

    private void saveData() throws SQLException, ParseException {
        PreparedStatement preparedStmt = null;
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
            String query = " insert into profile (uid, handle, year_of_birth, gender, age, standard, language, day, month, is_group_user, created_at, updated_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            preparedStmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

            preparedStmt.setString(1, uid);
            preparedStmt.setString(2, handle);
            setIntegerValues(preparedStmt, 3, yearOfBirth);
            preparedStmt.setString(4, gender);
            setIntegerValues(preparedStmt, 5, age);
            setIntegerValues(preparedStmt, 6, standard);
            preparedStmt.setString(7, language);
            setIntegerValues(preparedStmt, 8, day);
            setIntegerValues(preparedStmt, 9, month);
            preparedStmt.setBoolean(10, isGroupUser);
            preparedStmt.setTimestamp(11, createdAt);
            preparedStmt.setTimestamp(12, updatedAt);

            int affectedRows = preparedStmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating Profile failed, no rows affected.");
            }

            ResultSet generatedKeys = preparedStmt.getGeneratedKeys();

            if (generatedKeys.next()) {
                this.setIsInserted();
            } else {
                throw new SQLException("Creating Profile failed, no ID obtained.");
            }

        } catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace(new PrintStream(System.err));
        } finally {
            if (preparedStmt != null)
                preparedStmt.close();
            if (connection != null)
                connection.close();
        }
    }

    private void setIntegerValues(PreparedStatement preparedStmt, int index, Integer value) throws SQLException {
        if (value != null)
            preparedStmt.setInt(index, value);
        else
            preparedStmt.setNull(index, Types.INTEGER);
    }

    private void createLearner(Event event) throws SQLException, ParseException {
        CreateLearnerDto learnerDto = new CreateLearnerDto(dataSource);
        learnerDto.process(event);
    }

    public boolean isLearnerExist(String uid) throws SQLException {
        boolean flag = false;
        PreparedStatement preparedStmt = null;
        Connection connection;
        connection = dataSource.getConnection();
        ResultSet resultSet = null;

        try {
            String query = String.format("select %s from learner where %s = ?", UID, UID);
            preparedStmt = connection.prepareStatement(query);
            preparedStmt.setString(1, uid);

            resultSet = preparedStmt.executeQuery();

            if (resultSet.first()) {
                flag = true;
            }

        } catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace(new PrintStream(System.err));
        } finally {
            if (preparedStmt != null)
                preparedStmt.close();
            if (connection != null)
                connection.close();
            if (resultSet != null)
                resultSet.close();
        }
        return flag;
    }

    private Integer getYear(Map<String, Object> EKS, java.util.Date timeOfEvent) throws ParseException {
        if (!EKS.containsKey(AGE)) {
            return null;
        }
        Integer ageInEvent = ((Double) EKS.get(AGE)).intValue();
        if (ageInEvent != -1) {
            Calendar timeOfEventFromCalendar = Calendar.getInstance();
            timeOfEventFromCalendar.setTime(timeOfEvent);
            return timeOfEventFromCalendar.get(Calendar.YEAR) - ageInEvent;
        }
        return null;
    }

    @Override
    public void setIsInserted() {
        this.isInserted = true;
    }

    @Override
    public boolean getIsInserted() {
        return this.isInserted;
    }

}
