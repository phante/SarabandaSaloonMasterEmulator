/*
 * Copyright 2015 Elvis Del Tedesco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sarabandasaloon.master.emulator;

import com.phante.sarabandasaloon.entity.PushButton;
import com.phante.sarabandasaloon.entity.PushButtonStatus;
import com.phante.sarabandasaloon.network.SarabandaController;
import com.phante.sarabandasaloon.ui.PushButtonSimbol;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/**
 *
 * @author elvisdeltedesco
 */
public class RootController implements Initializable {

    @FXML
    private GridPane panel = new GridPane();

    @FXML
    private Label RXLabel = new Label();
    
    @FXML
    private CheckMenuItem modeSelection = new CheckMenuItem();
    @FXML
    private CheckMenuItem networkSelection = new CheckMenuItem();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        double maxSize = 100;

        // Inizializza i simboli per i singoli pulsanti
        int i = 0;
        for (PushButton button : SarabandaController.getInstance().getPushButton()) {
            // Crea il simbolo
            PushButtonSimbol simbol = new PushButtonSimbol();

            // Lo aggiunge al pannello
            panel.add(simbol, i++, 0);

            // Imposta le dimensioni
            simbol.setMaxSize(maxSize, maxSize);
            simbol.setMinSize(maxSize, maxSize);
            simbol.setPrefSize(maxSize, maxSize);

            // Aggiunge il listener sullo stato dei pulsanti
            button.valueProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
                Logger.getLogger(RootController.class.getName()).log(Level.INFO, "Un pulsante ha cambiato stato da {0} a {1}.", new Object[]{oldValue, newValue});

                // Al cambio dello stato del pulsante cambio il simbolo come feedback visivo di cosa succede sul palco
                simbol.setValue(PushButtonStatus.parse(newValue));
            });
        }
        
        SarabandaController sc = SarabandaController.getInstance();
        RXLabel.textProperty().bind(sc.messageProperty());
        
        modeSelection.selectedProperty().bind(sc.classicModeProperty());
        networkSelection.selectedProperty().bind(sc.onlyLocalhostModeProperty());
    }

    private void buttonManagement(int buttonId) {
        Logger.getLogger(RootController.class.getName()).log(Level.INFO, "Premuto il pulsante {0}", buttonId);
        PushButton button = SarabandaController.getInstance().getPushButton().get(buttonId - 1);
        
        if (button.getStatus() == PushButtonStatus.ENABLED) {
            // Pressione valida, invio il segnale alla rete
            button.setStatus(PushButtonStatus.PRESSED);
            SarabandaController.getInstance().sendPushButtonPressed(buttonId - 1);
        }
    }

    @FXML
    public void pushButton1() {
        buttonManagement(1);
    }

    @FXML
    public void pushButton2() {
        buttonManagement(2);
    }

    @FXML
    public void pushButton3() {
        buttonManagement(3);
    }

    @FXML
    public void pushButton4() {
        buttonManagement(4);
    }
    
    @FXML 
    public void handleQuit() {
        //Esci??
    }
    
    @FXML
    public void switchClassicMode() {
        SarabandaController.getInstance().setClassicMode(!modeSelection.isSelected());
    }
    
    @FXML
    public void switchNetworkMode() {
        SarabandaController.getInstance().setLocalhostOnly(!networkSelection.isSelected());
    }
}
