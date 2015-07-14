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
package com.phante.sarabandasaloon.network;

import com.phante.sarabandasaloon.entity.PushButtonStatus;
import com.phante.sarabandasaloon.entity.PushButton;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;

/**
 *
 * @author deltedes
 */
public class SarabandaController {
    // Numero dei pulsanti del sarabanda
    private final static int BUTTON_NUMBER = 4;

    // Stati del server udp
    public final static int SERVER_STARTED = 0;
    public final static int SERVER_UNKNOWN = 1;
    public final static int SERVER_STOPPED = 2;

    // Header standard del pacchetto Sarabanda
    final static String MESSAGE_HEADER = "SRBND-";

    // Comandi sarabanda validi
    final static String RESET_COMMAND = "RESET";
    final static String FULLRESET_COMMAND = "FULLRESET";
    final static String ERROR_COMMAND = "ERROR";
    final static String DEMO_COMMAND = "DEMO";
    final static String HWRESET_COMMAND = "X";
    final static String BUTTON_COMMAND = "B";

    private final String buttonCommandRegex;
    
    // Porta di invio, secondo la nuova modalità
    private int udpSendPort;
    // Porta di ascolto
    private int udpListePort;
    // Memorizzare l'indirizzo di broadcast, impostata di default come loopba per sicurezza
    private InetAddress broadcastAddress = InetAddress.getLoopbackAddress();

    // Server UDP per la comunicazione con il master
    private UDPServerService udpservice;
    // Imposta la modalità classica che lavora tutto sulla porta 8888
    private final ReadOnlyBooleanWrapper classicModeProperty = new ReadOnlyBooleanWrapper();
    // Identifica il funzionamento su solo localhost senza usare il broadcast
    private final ReadOnlyBooleanWrapper onlyLocalhostModeProperty = new ReadOnlyBooleanWrapper();
    // Memorizza lo stato del server
    private final ReadOnlyIntegerWrapper serverStatus = new ReadOnlyIntegerWrapper();
    // Memorizza l'ultimo messaggio arrivato
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();
    // Stato dei pulsanti
    private final ObservableList<PushButton> buttons = FXCollections.observableArrayList();

    /**
     * Inizializza lo stato del controller andando a creare il servizio che si
     * occupa della lettura dei pacchetti di rete e i pulsanti.
     */
    private SarabandaController() {
        setClassicMode(false);
        setLocalhostOnly(true);
        
        // Crea i pulsanti del sarabanda
        for (int i = 0; i < BUTTON_NUMBER; i++) {
            buttons.add(new PushButton());
        }

        // Espressione regolare per identificare un pacchetto pulsanti valido
        StringBuffer regex = new StringBuffer()
                .append("^")
                .append(MESSAGE_HEADER)
                .append(BUTTON_COMMAND)
                .append("[");
        for (PushButtonStatus status : PushButtonStatus.values()) {
            regex.append(status);
        }
        regex.append("]{")
                .append(buttons.size())
                .append("}");

        buttonCommandRegex = regex.toString();

    }

    public static SarabandaController getInstance() {
        return SarabandaControllerHolder.INSTANCE;
    }

    private static class SarabandaControllerHolder {

        private static final SarabandaController INSTANCE = new SarabandaController();
    }

    /**
     * Inizializza il service UDP per la gestione della comunicazione di rete
     * @param port
     */
    private void initUDPService() {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();

            // Creo il servizio
            udpservice = new UDPServerService(udpSendPort);

            // Listener per identificare l'arrivio di un nuovo pacchetto
            udpservice.packetProperty().addListener(((observableValue, oldValue, newValue) -> {
                // Ignoro i messaggi che arrivano da me stesso
                //if (localAddress.getHostAddress().equals(udpservice.senderProperty().getValue())) 
                {
                    // Effettua il parsing del messaggio il messaggio, uso il runLater 
                    // per disaccoppiare i thread e consentire la modifica della UI
                    // dal thread principale
                    Platform.runLater(() -> {
                        message.setValue(newValue);
                        parseMessage(newValue);
                    });
                }
            }));
        } catch (UnknownHostException ex) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Idetifico lo stato del server
        udpservice.setOnRunning(value -> {
            serverStatus.setValue(SERVER_STARTED);
        });
        udpservice.setOnScheduled(value -> {
            serverStatus.setValue(SERVER_UNKNOWN);
        });
        udpservice.setOnReady(value -> {
            serverStatus.setValue(SERVER_STOPPED);
        });
        udpservice.setOnCancelled(value -> {
            serverStatus.setValue(SERVER_STOPPED);
        });
        udpservice.setOnFailed(value -> {
            serverStatus.setValue(SERVER_STOPPED);
        });
    }
    
    public void setClassicMode(boolean classicMode) {
        classicModeProperty.setValue(classicMode);
        if (classicMode) {
            udpSendPort = 8888;
        } else {
            udpSendPort = 8889;
        }
        
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Cambio della modalità con invio alla porta {0}", udpSendPort);
    }
    
    public void setLocalhostOnly(boolean localhostOnly) {
        onlyLocalhostModeProperty.setValue(localhostOnly);
        
        // Imposta l'indirizzo di broascast
        if (localhostOnly) {
            broadcastAddress = InetAddress.getLoopbackAddress();
        } else {
            try {
                broadcastAddress = InetAddress.getByName("255.255.255.255");
            } catch (UnknownHostException ex) {
                Logger.getLogger(SarabandaController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Cambio della modalità con invio su indirizzo {0}", broadcastAddress.getHostAddress());
    }
    
    public ReadOnlyBooleanProperty classicModeProperty() {
        return classicModeProperty.getReadOnlyProperty();
    }
    
    public ReadOnlyBooleanProperty onlyLocalhostModeProperty() {
        return onlyLocalhostModeProperty.getReadOnlyProperty();
    }
    

    /**
     * Effettua il parsing dei messaggi. Per definizione il software è uno slave
     * e quindi va a gestire solo ed esclusivamente i messaggi di tipo B in
     * quanto quelli normalmente inviati dal Master. I messaggi ERROR, FULLRESET
     * e RESET sono messaggi inviati dagli slave per comandare lo stato del
     * master.
     *
     * @param message
     */
    private void parseMessage(String message) {
        // Verifico il comando
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        if (message.matches(buttonCommandRegex)) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un cambio di stato dei pulsanti secondo {1}", new Object[]{message, buttonCommandRegex});
            String button = message.substring(message.length() - 4);
            for (int i = 0; buttons.size() > i; i++) {
                PushButtonStatus status = PushButtonStatus.parse(button.substring(i, i + 1));
                buttons.get(i).setStatus(status);
            }
        }
        
        if (message.matches("SRBND-E.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un errore secondo {1}", new Object[]{message, "SRBND-E.*"});
            buttons.stream().forEach((button) -> {
                if (button.getStatus() == PushButtonStatus.PRESSED) button.setStatus(PushButtonStatus.ERROR);
            });
        }
        
        if (message.matches("SRBND-F.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un full reset secondo {1}", new Object[]{message, "SRBND-F.*"});
            buttons.stream().forEach((button) -> {
                button.setStatus(PushButtonStatus.ENABLED);
            });
        }
        
        if (message.matches("SRBND-R.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un reste secondo {1}", new Object[]{message, "SRBND-R.*"});
            buttons.stream().forEach((button) -> {
                if (button.getStatus() == PushButtonStatus.PRESSED) button.setStatus(PushButtonStatus.ENABLED);
            });
        }
    }

     /*
     * Avvia il servizio UDP
     */
    public void startServer() {
        if (udpservice != null) {
            // Ripulisce lo stato del task
            if ((udpservice.getState() == Worker.State.CANCELLED) || (udpservice.getState() == Worker.State.FAILED)) {
                udpservice.reset();
            }

            // Se il server è pronto effettua lo start del service
            if (udpservice.getState() == Worker.State.READY) {
                udpservice.start();
            }
        }
    }

    /**
     * Disattiva il servizio UDP invocando la cancellazione del servizio e
     * spedento un pacchetto per bypassare il fatto che la lettura del socket è
     * bloccante
     */
    public void stopServer() {
        if (udpservice != null) {
            if (udpservice.isRunning()) {
                // Invia al servizio il comando di spegnersi
                udpservice.cancel();

                // Invia un pacchetto UDP generico per andare a far uscire il server dallo stato di listen
                // necessario in quanto la lettura del socket è bloccante
                sendSarabandaMessage("");
            }
        }
    }
    
    

    public void sendSarabandaReset() {
        sendSarabandaMessage(SarabandaController.RESET_COMMAND);
    }

    public void sendSarabandaFullReset() {
        sendSarabandaMessage(SarabandaController.FULLRESET_COMMAND);
    }

    public void sendSarabandaError() {
        sendSarabandaMessage(SarabandaController.ERROR_COMMAND);
    }

    public void sendSarabandaDemo() {
        sendSarabandaMessage(SarabandaController.DEMO_COMMAND);
    }

    public void sendSarabandaMasterPhysicalReset() {
        sendSarabandaMessage(SarabandaController.HWRESET_COMMAND);
    }

    public void sendPushButtonPressed(int buttonId) {
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);

        for (int i = 0; i < buttons.size(); i++) {
            if (i == buttonId) {
                _message.append(PushButtonStatus.PRESSED);
            } else {
                _message.append(buttons.get(i).getStatus());
            }
        }

        sendSarabandaMessage(_message.toString());
    }

    /**
     * Invia un comando di disabilitazione dei pulsanti, utile per evitare
     * pressioni fuori sequenza in una fase di gioco fermo
     *
     */
    public void enableAllPushButton() {
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);

        buttons.stream().forEach((_item) -> {
            _message.append(_item.getStatus() == PushButtonStatus.DISABLED ? PushButtonStatus.ENABLED : _item.getStatus());
        });

        sendSarabandaMessage(_message.toString());
    }

    /**
     * Invia un comando di disabilitazione dei pulsanti, utile per evitare
     * pressioni fuori sequenza in una fase di gioco fermo
     *
     */
    public void disableAllPushButton() {
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);

        buttons.stream().forEach((_item) -> {
            _message.append(_item.getStatus() == PushButtonStatus.ENABLED ? PushButtonStatus.DISABLED : _item.getStatus());
        });

        sendSarabandaMessage(_message.toString());
    }

    /**
     * Manda in errore tutti i pulsanti non premuti
     *
     */
    public void errorUnpressedPushButton() {
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);

        buttons.stream().forEach((_item) -> {
            _message.append(_item.getStatus() == PushButtonStatus.PRESSED ? _item.getStatus() : PushButtonStatus.ERROR);
        });

        sendSarabandaMessage(_message.toString());
    }

    /**
     * Invia un messaggio Sarabanda
     *
     * @param message
     */
    public void sendSarabandaMessage(String message) {
        sendPacket(new StringBuilder()
                .append(SarabandaController.MESSAGE_HEADER)
                .append(message)
                .toString(),
                udpSendPort,
                broadcastAddress);
    }

    /**
     * Invia un messaggio all'indirizzo specificato
     *
     * @param message
     * @param port
     * @param destination
     */
    public void sendPacket(String message, int port, InetAddress destination) {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);

            DatagramPacket sendPacket = new DatagramPacket(
                    message.getBytes(),
                    message.getBytes().length,
                    destination,
                    port
            );
            socket.send(sendPacket);
        } catch (SocketException ex) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     *
     * @return
     */
    public ReadOnlyIntegerProperty serverStatusProperty() {
        return serverStatus.getReadOnlyProperty();
    }

    /**
     *
     * @return
     */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /**
     *
     * @return
     */
    public ObservableList<PushButton> getPushButton() {
        return buttons;
    }

}
