package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.StringTokenizer;

//Handles positions of map
public class PositionMapper {

    private static final Properties positionTileMap6Player = new Properties();
    private static final Properties planetPositions = new Properties();
    private static final Properties spaceTokenPositions = new Properties();

    public static void init() {
        readData("6player.properties", positionTileMap6Player, "Could not read position file");
        readData("planet.properties", planetPositions, "Could not read planet position file");
        readData("space_token.properties", spaceTokenPositions, "Could not read space token position file");
    }

    public static String getTilePlanetPositions(String tileID) {
        return (String) planetPositions.get(tileID);
    }

    public static ArrayList<Point> getSpaceTokenPositions(String tileID) {
        ArrayList<Point> points = new ArrayList<>();
        String value = (String) spaceTokenPositions.get(tileID);
        if (value == null){
            return points;
        }
        StringTokenizer tokenizer = new StringTokenizer(value, ";");
        while (tokenizer.hasMoreTokens()){
            try {
                String positionString = tokenizer.nextToken().replaceAll(" ", "");
                StringTokenizer position = new StringTokenizer(positionString, ",");
                if (position.countTokens() == 2) {
                    int x = Integer.parseInt(position.nextToken());
                    int y = Integer.parseInt(position.nextToken());
                    points.add(new Point(x, y));
                }
            } catch (Exception e) {
                LoggerHandler.log("Could not parse position", e);
            }
        }
        return points;
    }

    private static void readData(String fileName, Properties positionMap, String errorMessage) {
        String positionFile = ResourceHelper.getInstance().getPositionFile(fileName);
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                positionMap.load(input);
            } catch (IOException e) {
                LoggerHandler.log(errorMessage, e);
            }
        }
    }

    public static boolean isTilePositionValid(String position) {
        return positionTileMap6Player.getProperty(position) != null;
    }

    @CheckForNull
    public static Point getTilePosition(String position) {
        return getPosition(position, positionTileMap6Player);
    }

    private static Point getPosition(String position, Properties positionTileMap6Player) {
        String value = positionTileMap6Player.getProperty(position);
        return getPoint(value);
    }

    public static Point getPoint(String value) {
        if (value != null) {
            StringTokenizer tokenizer = new StringTokenizer(value, ",");
            try {
                int x = Integer.parseInt(tokenizer.nextToken());
                int y = Integer.parseInt(tokenizer.nextToken());
                return new Point(x, y);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse position coordinates", e);
            }
        }
        return null;
    }
}
