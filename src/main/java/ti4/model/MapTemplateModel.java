package ti4.model;

import java.util.List;

import lombok.Data;

@Data
public class MapTemplateModel implements ModelInterface {
    @Data
    public static class MapTemplateTile {
        // This field is for when you want a specific tile on the map, in the same position every time
        // Such as Mecatol Rex in the middle, or hyperlanes for a 3,4,5,7,8 player game, etc.
        String staticTileId;
        Boolean custodians; //add custodian to this tile (presently only works with MR)

        // These three fields control if a particular tile is a placeholder for a milty draft tile.
        Integer playerNumber;
        Integer miltyTileIndex;
        Boolean home;

        // This is the position the tile should be on the map
        String pos;
    }

    String alias;
    String author;
    String descr;
    Integer playerCount;
    Integer tilesPerPlayer;
    boolean toroidal;
    // MECATOL REX IS NOT INCLUDED BY DEFAULT
    List<MapTemplateTile> templateTiles;

    public boolean isValid() {
        return true;
    }

    public String autoCompleteString() {
        return getAlias() + ": " + getPlayerCount() + " player map by " + getAuthor();
    }
}