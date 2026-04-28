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
import it.polimi.ingsw.model.enums.Era;
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
import java.util.List;
import java.util.Map;
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

    /**
     * Shuffles both decks while preserving the era-based draw order required
     * by the game.
     * <p>
     * Cards are drawn from the top of each deck (i.e. via
     * {@link #drawTribe()} / {@link #drawBuilding()}, which remove the first
     * element), so the list order directly determines the order in which
     * cards enter play. Eras must be exhausted in sequence: every era 1 card
     * is drawn before any era 2 card, and every era 2 card before any era 3
     * card. Within a single era, however, the order must be random.
     * <p>
     * For the tribe deck there is an additional constraint: the two
     * "final event" cards (the {@link Ritual} and {@link Sustenance}
     * instances whose {@code isFinalEvent} flag is {@code true}, both of
     * which are era 3) must be the last cards drawn from the deck, after
     * every other era 3 card.
     * <p>
     * The algorithm is the same for both decks: partition the cards into
     * one list per era, shuffle each list independently, and then
     * concatenate them in era order. The tribe deck uses an extra
     * {@code tribeFinalEvents} bucket that is appended after the era 3
     * cards so the final events are always drawn last (they are still
     * shuffled among themselves so their relative order is random).
     * <p>
     * Both {@link #tribeDeck} and {@link #buildingDeck} are reassigned to
     * fresh {@link ArrayList} instances; any previously held references
     * to the old lists become stale.
     */
    public void shuffleDecks() {
        List<TribeCard> tribeEra1 = new ArrayList<>();
        List<TribeCard> tribeEra2 = new ArrayList<>();
        List<TribeCard> tribeEra3 = new ArrayList<>();
        List<TribeCard> tribeFinalEvents = new ArrayList<>();
        for (TribeCard card : this.tribeDeck) {
            // Final-event cards (only on Sustenance/Ritual, always era 3) must
            // be drawn last, so they go to a dedicated list regardless of era.
            if ((card instanceof Ritual r && r.isFinalEvent())
                    || (card instanceof Sustenance s && s.isFinalEvent())) {
                tribeFinalEvents.add(card);
                continue;
            }
            switch (card.getEra()) {
                case ERA_1 -> tribeEra1.add(card);
                case ERA_2 -> tribeEra2.add(card);
                case ERA_3 -> tribeEra3.add(card);
            }
        }
        Collections.shuffle(tribeEra1);
        Collections.shuffle(tribeEra2);
        Collections.shuffle(tribeEra3);
        Collections.shuffle(tribeFinalEvents);
        this.tribeDeck = new ArrayList<>(
                tribeEra1.size() + tribeEra2.size() + tribeEra3.size() + tribeFinalEvents.size());
        this.tribeDeck.addAll(tribeEra1);
        this.tribeDeck.addAll(tribeEra2);
        this.tribeDeck.addAll(tribeEra3);
        this.tribeDeck.addAll(tribeFinalEvents);

        List<BuildingCard> buildingEra1 = new ArrayList<>();
        List<BuildingCard> buildingEra2 = new ArrayList<>();
        List<BuildingCard> buildingEra3 = new ArrayList<>();
        for (BuildingCard card : this.buildingDeck) {
            switch (card.getEra()) {
                case ERA_1 -> buildingEra1.add(card);
                case ERA_2 -> buildingEra2.add(card);
                case ERA_3 -> buildingEra3.add(card);
            }
        }
        Collections.shuffle(buildingEra1);
        Collections.shuffle(buildingEra2);
        Collections.shuffle(buildingEra3);
        this.buildingDeck = new ArrayList<>(buildingEra1.size() + buildingEra2.size() + buildingEra3.size());
        this.buildingDeck.addAll(buildingEra1);
        this.buildingDeck.addAll(buildingEra2);
        this.buildingDeck.addAll(buildingEra3);
    }

    /**
     * Draws and removes the first card from the tribe deck.
     * <p>
     * If the tribe deck is empty, an {@link EmptyDeckException} is thrown.
     *
     * @return the {@link Card} removed from the top of the tribe deck
     * @throws EmptyDeckException if the tribe deck is empty
     */
    public Card drawTribe() throws EmptyDeckException {
        if (this.tribeDeck.isEmpty()) {
            throw new EmptyDeckException("The tribe deck is empty");
        }
        return this.tribeDeck.removeFirst();
    }

    /**
     * Draws and removes the first card from the building deck.
     * <p>
     * If the building deck is empty, an {@link EmptyDeckException} is thrown.
     *
     * @return the {@link Card} removed from the top of the building deck
     * @throws EmptyDeckException if the building deck is empty
     */
    public Card drawBuilding() throws EmptyDeckException {
        if (this.buildingDeck.isEmpty()) {
            throw new EmptyDeckException("The building deck is empty");
        }
        return this.buildingDeck.removeFirst();
    }

    /**
     * Plain DTO used only by Gson to deserialize {@code cards.json}.
     * <p>
     * The JSON has two top-level arrays ({@code tribeCards} and
     * {@code buildingCards}); this class mirrors that shape so a single
     * {@code gson.fromJson(reader, DeckData.class)} call populates both
     * fields at once. Field names must match the JSON keys exactly.
     * <p>
     * Declared {@code static} so Gson can instantiate it via its no-arg
     * constructor without needing an enclosing {@link Decks} instance.
     */
    private static class DeckData {
        List<TribeCard> tribeCards;
        List<BuildingCard> buildingCards;
    }
}
