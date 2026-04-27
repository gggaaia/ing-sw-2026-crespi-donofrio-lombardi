package it.polimi.ingsw.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import it.polimi.ingsw.exceptions.DeckLoadException;
import it.polimi.ingsw.exceptions.EmptyDeckException;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.TribeCard;
import it.polimi.ingsw.model.card.building.Bonus25PP;
import it.polimi.ingsw.model.card.building.CharacterBonusBuilder;
import it.polimi.ingsw.model.card.building.CharacterBonusGatherer;
import it.polimi.ingsw.model.card.building.CharacterBonusHunter;
import it.polimi.ingsw.model.card.building.CharacterBonusInventor;
import it.polimi.ingsw.model.card.building.CharacterBonusPainter;
import it.polimi.ingsw.model.card.building.CharacterBonusShaman;
import it.polimi.ingsw.model.card.building.DoubleBuilderPP;
import it.polimi.ingsw.model.card.building.DrawUpperRow;
import it.polimi.ingsw.model.card.building.HuntBonus;
import it.polimi.ingsw.model.card.building.InventorPair;
import it.polimi.ingsw.model.card.building.PaintingFoodBonus;
import it.polimi.ingsw.model.card.building.RitualPPMultiplier;
import it.polimi.ingsw.model.card.building.RitualSave;
import it.polimi.ingsw.model.card.building.RitualStarBonus;
import it.polimi.ingsw.model.card.building.SixSet;
import it.polimi.ingsw.model.card.building.SustenanceBonusGatherer;
import it.polimi.ingsw.model.card.building.SustenanceBonusInventor;
import it.polimi.ingsw.model.card.building.SustenanceBonusPainter;
import it.polimi.ingsw.model.card.building.TotemFoodBonus;
import it.polimi.ingsw.model.card.building.UniqueSixSet;
import it.polimi.ingsw.model.card.character.Artist;
import it.polimi.ingsw.model.card.character.Builder;
import it.polimi.ingsw.model.card.character.Gatherer;
import it.polimi.ingsw.model.card.character.Hunter;
import it.polimi.ingsw.model.card.character.Inventor;
import it.polimi.ingsw.model.card.character.Shaman;
import it.polimi.ingsw.model.card.event.Hunting;
import it.polimi.ingsw.model.card.event.Paintings;
import it.polimi.ingsw.model.card.event.Ritual;
import it.polimi.ingsw.model.card.event.Sustenance;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Decks {
    private List<TribeCard> tribeDeck;
    private List<BuildingCard> buildingDeck;

    public Decks() {
        this.tribeDeck = new ArrayList<>();
        this.buildingDeck = new ArrayList<>();
    }

    /**
     * Populates both tribe and building decks with cards appropriate for the given player count.
     * <p>
     * This method loads cards from the {@code cards.json} resource file and filters them
     * based on their {@code minPlayers} requirement. Only cards that support the specified
     * number of players are included in the decks.
     * <p>
     * The cards are deserialized using Gson with polymorphic type adapters, allowing
     * automatic creation of the correct concrete subclass instances based on the
     * {@code type} field in the JSON.
     *
     * @param playersNumber the number of players (must be between 2 and 5 inclusive)
     * @throws DeckLoadException if the player count is invalid, the cards file cannot
     *                          be read, or JSON parsing fails
     */
    public void populateDecks(int playersNumber) throws DeckLoadException {
        // The game supports 2..5 players. cards.json contains no cards with
        // minPlayers < 2 or > 5, so any value outside this range either yields
        // an empty deck (too low) or behaves identically to 5 (too high).
        // Reject those inputs explicitly so the caller gets a clear error
        // instead of silently broken decks.
        if (playersNumber < 2 || playersNumber > 5) {
            throw new DeckLoadException("Invalid player count: " + playersNumber
                    + " (must be between 2 and 5)");
        }

        // 1. Polymorphic adapter for TribeCard.
        //
        // The "type" field in cards.json selects which concrete subclass Gson
        // should instantiate. RuntimeTypeAdapterFactory only accepts concrete
        // (non-abstract) classes, so the abstract intermediates CharacterCard
        // and EventCard are NOT registered here.
        //
        // The real hierarchy is:
        //   TribeCard (abstract)
        //     |- CharacterCard (abstract) -> Hunter, Artist, Gatherer,
        //     |                              Shaman, Builder, Inventor
        //     \- EventCard (abstract)     -> Hunting, Ritual, Sustenance,
        //                                    Paintings
        //
        // Every leaf is registered directly under TribeCard because that is
        // the declared element type of the deck list (List<TribeCard>); Gson
        // does not require to mirror the intermediate abstract layers.
        RuntimeTypeAdapterFactory<TribeCard> tribeAdapter = RuntimeTypeAdapterFactory
                .of(TribeCard.class, "type")
                // CharacterCard subclasses
                .registerSubtype(Hunter.class, "Hunter")
                .registerSubtype(Artist.class, "Artist")
                .registerSubtype(Gatherer.class, "Gatherer")
                .registerSubtype(Shaman.class, "Shaman")
                .registerSubtype(Builder.class, "Builder")
                .registerSubtype(Inventor.class, "Inventor")
                // EventCard subclasses
                .registerSubtype(Hunting.class, "Hunting")
                .registerSubtype(Ritual.class, "Ritual")
                .registerSubtype(Sustenance.class, "Sustenance")
                .registerSubtype(Paintings.class, "Paintings");

        // 2. Polymorphic adapter for BuildingCard.
        //
        // Same as above: BuildingCard has two abstract intermediates that
        // group cards by *when* their effect triggers, plus a few concrete
        // cards that extend BuildingCard directly:
        //
        //   BuildingCard (abstract)
        //     |- (direct leaves)            -> TotemFoodBonus, SixSet,
        //     |                                InventorPair, DrawUpperRow
        //     |- EventActivatedBuilding (abstract)
        //     |    -> HuntBonus, RitualSave, RitualPPMultiplier,
        //     |       RitualStarBonus, PaintingFoodBonus,
        //     |       SustenanceBonusGatherer, SustenanceBonusInventor,
        //     |       SustenanceBonusPainter
        //     \- EndOfMatchBuilding (abstract)
        //          |- Bonus25PP, DoubleBuilderPP, UniqueSixSet
        //          \- CharacterBonus* family (no intermediate abstract class):
        //             CharacterBonusGatherer, CharacterBonusInventor,
        //             CharacterBonusHunter, CharacterBonusBuilder,
        //             CharacterBonusPainter, CharacterBonusShaman
        //
        // Only the concrete leaves are registered.
        RuntimeTypeAdapterFactory<BuildingCard> buildingAdapter = RuntimeTypeAdapterFactory
                .of(BuildingCard.class, "type")
                // Direct BuildingCard subclasses
                .registerSubtype(TotemFoodBonus.class, "TotemFoodBonus")
                .registerSubtype(SixSet.class, "SixSet")
                .registerSubtype(InventorPair.class, "InventorPair")
                .registerSubtype(DrawUpperRow.class, "DrawUpperRow")
                // EventActivatedBuilding subclasses
                .registerSubtype(HuntBonus.class, "HuntBonus")
                .registerSubtype(RitualSave.class, "RitualSave")
                .registerSubtype(RitualPPMultiplier.class, "RitualPPMultiplier")
                .registerSubtype(RitualStarBonus.class, "RitualStarBonus")
                .registerSubtype(PaintingFoodBonus.class, "PaintingFoodBonus")
                .registerSubtype(SustenanceBonusGatherer.class, "SustenanceBonusGatherer")
                .registerSubtype(SustenanceBonusInventor.class, "SustenanceBonusInventor")
                .registerSubtype(SustenanceBonusPainter.class, "SustenanceBonusPainter")
                // EndOfMatchBuilding subclasses - general bonuses
                .registerSubtype(Bonus25PP.class, "Bonus25PP")
                .registerSubtype(DoubleBuilderPP.class, "DoubleBuilderPP")
                .registerSubtype(UniqueSixSet.class, "UniqueSixSet")
                // EndOfMatchBuilding subclasses - CharacterBonus* family
                .registerSubtype(CharacterBonusGatherer.class, "CharacterBonusGatherer")
                .registerSubtype(CharacterBonusInventor.class, "CharacterBonusInventor")
                .registerSubtype(CharacterBonusHunter.class, "CharacterBonusHunter")
                .registerSubtype(CharacterBonusBuilder.class, "CharacterBonusBuilder")
                .registerSubtype(CharacterBonusPainter.class, "CharacterBonusPainter")
                .registerSubtype(CharacterBonusShaman.class, "CharacterBonusShaman");

        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(tribeAdapter)
                .registerTypeAdapterFactory(buildingAdapter)
                .create();

        // Load cards.json from the classpath so it works whether the project is
        // run from the IDE, from the command line, or packaged as a JAR.
        InputStream in = Decks.class.getResourceAsStream("/cards.json");
        if (in == null) {
            throw new DeckLoadException("cards.json not found on classpath");
        }

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            DeckData data = gson.fromJson(reader, DeckData.class);

            // Filter by player count: a card is in the deck only if its minPlayers
            // requirement is satisfied by the current game size.
            if (data != null && data.tribeCards != null) {
                this.tribeDeck = data.tribeCards.stream()
                        .filter(card -> card.getMinPlayers() <= playersNumber)
                        .collect(Collectors.toCollection(ArrayList::new));
            }

            if (data != null && data.buildingCards != null) {
                this.buildingDeck = data.buildingCards.stream()
                        .filter(card -> card.getMinPlayers() <= playersNumber)
                        .collect(Collectors.toCollection(ArrayList::new));
            }

            System.out.println("Decks populated successfully: "
                    + this.tribeDeck.size() + " tribe cards, "
                    + this.buildingDeck.size() + " building cards.");
        } catch (IOException e) {
            throw new DeckLoadException("Error reading cards.json: " + e.getMessage(), e);
        }
    }

    public void shuffleDecks() {
        Collections.shuffle(this.tribeDeck);
        this.tribeDeck.sort(Comparator.comparing(Card::getEra));

        Collections.shuffle(this.buildingDeck);
        this.buildingDeck.sort(Comparator.comparing(Card::getEra));
    }

    public Card drawTribe() throws EmptyDeckException {
        if (this.tribeDeck.isEmpty()) {
            throw new EmptyDeckException("The tribe deck is empty");
        }
        return this.tribeDeck.removeFirst();
    }

    public Card drawBuilding() throws EmptyDeckException {
        if (this.buildingDeck.isEmpty()) {
            throw new EmptyDeckException("The building deck is empty");
        }
        return this.buildingDeck.removeFirst();
    }

    /**
     * Plain DTO mirroring the top-level structure of cards.json.
     * Field names must match the JSON keys exactly.
     */
    private static class DeckData {
        List<TribeCard> tribeCards;
        List<BuildingCard> buildingCards;
    }
}
