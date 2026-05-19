package it.polimi.ingsw.view.GUI;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

import java.util.List;
import java.util.Set;

/**
 * View JavaFX. Implementazione non ancora scritta: i metodi
 * {@code display*} sono no-op e i {@code prompt*} tirano una
 * {@link UnsupportedOperationException}, cos\u00ec scegliere "gui"
 * all'avvio compila ma fallisce subito a runtime in modo chiaro.
 */
public class GuiView implements View {

    @Override
    public void displayConnected(String host, int port) {
        // not yet implemented
    }

    @Override
    public void displayInfo(String message) {
        // not yet implemented
    }

    @Override
    public void displayError(String message) {
        // not yet implemented
    }

    @Override
    public void refresh(ClientController state) {
        // not yet implemented
    }

    @Override
    public String promptNickname() {
        throw new UnsupportedOperationException("GuiView.promptNickname not implemented");
    }

    @Override
    public int promptPlayerCount() {
        throw new UnsupportedOperationException("GuiView.promptPlayerCount not implemented");
    }

    @Override
    public TotemColor promptColor(Set<TotemColor> available) {
        throw new UnsupportedOperationException("GuiView.promptColor not implemented");
    }

    @Override
    public int promptOfferingTile(int trackSize) {
        throw new UnsupportedOperationException("GuiView.promptOfferingTile not implemented");
    }

    @Override
    public List<Integer> promptCharacterUpperIds(int budget) {
        throw new UnsupportedOperationException("GuiView.promptCharacterUpperIds not implemented");
    }

    @Override
    public List<Integer> promptCharacterLowerIds(int budget) {
        throw new UnsupportedOperationException("GuiView.promptCharacterLowerIds not implemented");
    }

    @Override
    public List<Integer> promptBuildingUpperIds(int remaining) {
        throw new UnsupportedOperationException("GuiView.promptBuildingUpperIds not implemented");
    }

    @Override
    public List<Integer> promptBuildingLowerIds(int remaining) {
        throw new UnsupportedOperationException("GuiView.promptBuildingLowerIds not implemented");
    }

    @Override
    public View.ExtraPickChoice promptExtraCard() {
        throw new UnsupportedOperationException("GuiView.promptExtraCard not implemented");
    }
}
