package ti4.draft.items;

import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.draft.DraftItem;
import ti4.generator.TileHelper;
import ti4.helpers.Emojis;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel;
import ti4.model.TechSpecialtyModel;

public class RedTileDraftItem extends DraftItem {
    public RedTileDraftItem(String itemId) {
        super(Category.REDTILE, itemId);
    }

    @Override
    public String getItemName() {
        return TileHelper.getTile(ItemId).getName();
    }

    @Override
    public MessageEmbed getItemCard() {
        return TileHelper.getTile(ItemId).getHelpMessageEmbed(false);
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        sb.append(planetTypeEmoji(planet.getPlanetType()));
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append("/").append(Emojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            for (var spec : planet.getTechSpecialties()) {
                sb.append("/").append(techSpecEmoji(spec));
            }
        }
        sb.append(") ");
    }

    private String planetTypeEmoji(PlanetTypeModel.PlanetType type){
        switch (type) {

            case CULTURAL -> {
                return Emojis.Cultural;
            }
            case HAZARDOUS -> {
                return Emojis.Hazardous;
            }
            case INDUSTRIAL -> {
                return Emojis.Industrial;
            }
        }
        return Emojis.GoodDog;
    }

    private String techSpecEmoji(TechSpecialtyModel.TechSpecialty type){
        switch (type) {

            case BIOTIC -> {
                return Emojis.BioticTech;
            }
            case CYBERNETIC -> {
                return Emojis.CyberneticTech;
            }
            case PROPULSION -> {
                return Emojis.PropulsionTech;
            }
            case WARFARE -> {
                return Emojis.WarfareTech;
            }
        }
        return Emojis.GoodDog;
    }

    @Override
    public String getItemEmoji() {
        return Emojis.Supernova;
    }
}
