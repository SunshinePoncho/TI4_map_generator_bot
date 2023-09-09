package ti4.generator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import ti4.helpers.Storage;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TileHelper {

    private static Map<String, TileModel> allTiles = new HashMap<>();
    private static Map<String, PlanetModel> allPlanets = new HashMap<>();

    public static void init() {
        initPlanetsFromJson();
        initTilesFromJson();
    }

    public static Map<String, PlanetModel> getAllPlanets() {
        return allPlanets;
    }

    public static Map<String, TileModel> getAllTiles() {
        return allTiles;
    }

    public static void initPlanetsFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "planets" + File.separator;
        String storagePath = Storage.getStoragePath() + File.separator + "planets" + File.separator;
        List<File> files = new ArrayList<>();
        File[] storedFiles = new File(storagePath).listFiles();

        if(Optional.ofNullable(storedFiles).isPresent() && CollectionUtils.isNotEmpty(List.of(storedFiles))) {
            files.addAll(Stream.of(storedFiles)
                    .filter(file -> !file.isDirectory())
                    .toList());
        }
        files.addAll(Stream.of(new File(resourcePath).listFiles())
                .filter(file -> !file.isDirectory())
                .toList());

        files.forEach(file -> {
                    try {
                        PlanetModel planet = objectMapper.readValue(new FileInputStream(file), PlanetModel.class);
                        allPlanets.put(planet.getId(), planet);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

    }

    public static void initTilesFromJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "systems" + File.separator;
        String storagePath = Storage.getStoragePath() + File.separator + "systems" + File.separator;
        List<File> files = new ArrayList<>();
        File[] storedFiles = new File(storagePath).listFiles();

        if(Optional.ofNullable(storedFiles).isPresent() && CollectionUtils.isNotEmpty(List.of(storedFiles))) {
            files.addAll(Stream.of(storedFiles)
                    .filter(file -> !file.isDirectory())
                    .toList());
        }
        files.addAll(Stream.of(new File(resourcePath).listFiles())
                .filter(file -> !file.isDirectory())
                .toList());
        files.forEach(file -> {
            try {
                TileModel tile = objectMapper.readValue(new FileInputStream(file), TileModel.class);
                allTiles.put(tile.getId(), tile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void addNewTileToList(TileModel tile) {
        allTiles.put(tile.getId(), tile);
    }

    public static void addNewPlanetToList(PlanetModel planet) {
        allPlanets.put(planet.getId(), planet);
    }

    public static void exportAllPlanets() {
        ObjectMapper mapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "planets" + File.separator;
        allPlanets.values().forEach(planetModel -> {
            try {
                mapper.writeValue(new File(resourcePath + planetModel.getId() + ".json"), planetModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void exportAllTiles() {
        ObjectMapper mapper = new ObjectMapper();
        String resourcePath = Storage.getResourcePath() + File.separator + "systems" + File.separator;
        allTiles.values().forEach(tileModel -> {
            try {
                mapper.writeValue(new File(resourcePath + tileModel.getId() + ".json"), tileModel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
