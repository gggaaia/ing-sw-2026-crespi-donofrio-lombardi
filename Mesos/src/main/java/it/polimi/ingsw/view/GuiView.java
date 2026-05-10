package it.polimi.ingsw.view;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;

/**
 * JavaFX-based View. Bodies are stubs until step 10. For now the
 * methods either no-op (display) or throw (prompt) so picking "gui"
 * at startup compiles but is clearly not usable.
 */
public class GuiView implements View {
    public GuiView() {
        super();
    }

    @Override
    public void displayConnected(String host, int port) {
        // step 10: open the main JavaFX window with the connection banner
    }

    @Override
    public String promptNickname() {
        throw new UnsupportedOperationException("GuiView.promptNickname: see step 10");
    }

    @Override
    public void displayInfo(String message) {
        // step 10
    }

    @Override
    public void displayError(String message) {
        // step 10
    }

    @Override
    public void displayActionResult(ActionResultDto result) {
        // step 10
    }

    @Override
    public void displayState(GameStateDto state) {
        // step 10
    }

    @Override
    public int promptInt(String prompt) {
        throw new UnsupportedOperationException("GuiView.promptInt: see step 10 GUI work");
    }

    @Override
    public String promptLine(String prompt) {
        throw new UnsupportedOperationException("GuiView.promptLine: see step 10 GUI work");
    }
}
