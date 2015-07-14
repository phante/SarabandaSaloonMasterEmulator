/*
 * Copyright 2015 deltedes.
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

import com.phante.sarabandasaloon.entity.PushButton;
import com.phante.sarabandasaloon.entity.PushButtonStatus;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;

/**
 *
 * @author deltedes
 */
public class SarabandaController {

    // Stati del server udp
    public final static int SERVER_STARTED = 0;
    public final static int SERVER_UNKNOWN = 1;
    public final static int SERVER_STOPPED = 2;

    // Numero dei pulsanti del sarabanda
    private final static int BUTTON_NUMBER = 4;

    protected final static int UDP_MASTER_PORT = 8888;
    protected final static int UDP_SLAVE_PORT = 8889;
    protected final static int UDP_SLAVE_CLASSIC_PORT = 8888;

    // Header standard del pacchetto Sarabanda
    final static String MESSAGE_HEADER = "SRBND-";

    // Comandi sarabanda validi
    final static String RESET_COMMAND = "RESET";
    final static String FULLRESET_COMMAND = "FULLRESET";
    final static String ERROR_COMMAND = "ERROR";
    final static String DEMO_COMMAND = "DEMO";
    final static String HWRESET_COMMAND = "X";
    final static String BUTTON_COMMAND = "B";

    final String buttonCommandRegex;

    // Porta di invio
    protected int udpSendPort;
    // Porta di ascolto
    protected int udpListenPort;
    // Memorizzare l'indirizzo di broadcast, impostata di default come loopback per sicurezza
    protected InetAddress broadcastAddress;

    // Server UDP per la comunicazione con il master
    protected UDPServerService udpservice;

    // Imposta la modalità classica
    protected final ReadOnlyBooleanWrapper classicModeProperty = new ReadOnlyBooleanWrapper();
    // Identifica il funzionamento su solo localhost senza usare il broadcast
    protected final ReadOnlyBooleanWrapper onlyLocalhostModeProperty = new ReadOnlyBooleanWrapper();

    // Memorizza lo stato del server
    protected final ReadOnlyIntegerWrapper serverStatus = new ReadOnlyIntegerWrapper();
    // Memorizza l'ultimo messaggio arrivato
    protected final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();
    // Stato dei pulsanti
    protected final ObservableList<PushButton> buttons = FXCollections.observableArrayList();

    /**
     * Inizializza lo stato del controller andando a creare il servizio che si
     * occupa della lettura dei pacchetti di rete e i pulsanti.
     */
    protected SarabandaController() {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Inizializzo il controller");

        // Imposta la modalità di funzionamento a nuovo
        udpSendPort = UDP_SLAVE_PORT;
        udpListenPort = UDP_MASTER_PORT;
        classicModeProperty.setValue(Boolean.FALSE);

        // Imposta la modalità solo localhost
        broadcastAddress = InetAddress.getLoopbackAddress();
        onlyLocalhostModeProperty.setValue(Boolean.TRUE);

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

    /**
     * Inizializza il service UDP per la ricezione dei pacchetti di rete
     */
    protected void initUDPService() {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Inizializzo il processo listener su {0}", udpSendPort);
        try {
            InetAddress localAddress = InetAddress.getLocalHost();

            // Creo il servizio
            udpservice = new UDPServerService(udpListenPort);

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

    /**
     *
     * @param classicMode
     */
    public void setClassicMode(boolean classicMode) {
        classicModeProperty.setValue(classicMode);
        if (classicMode) {
            udpSendPort = UDP_SLAVE_CLASSIC_PORT;
            udpListenPort = UDP_MASTER_PORT;
        } else {
            udpSendPort = UDP_SLAVE_PORT;
            udpListenPort = UDP_MASTER_PORT;
        }

        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Cambio della modalità con invio alla porta {0}", udpSendPort);
    }

    /**
     *
     * @param localhostOnly
     */
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

    /**
     * Effettua il parsing dei messaggi.
     *
     * @param message
     */
    private void parseMessage(String message) {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        
        // Verifico se il messaggio ricevuto rispetta le condizioni 
        parseButtonMessage(message);
        parseErrorMessage(message);
        parseResetMessage(message);
        parseFullResetMessage(message);
        
        // Invio lo stato dei pulsanti
        sendPushButtonStatus();
    }

    /**
     * 
     * @param message 
     */
    protected void parseButtonMessage(String message) {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        if (message.matches(buttonCommandRegex)) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un cambio di stato dei pulsanti secondo {1}", new Object[]{message, buttonCommandRegex});
            String button = message.substring(message.length() - 4);
            for (int i = 0; buttons.size() > i; i++) {
                PushButtonStatus status = PushButtonStatus.parse(button.substring(i, i + 1));
                buttons.get(i).setStatus(status);
            }
        }
    }

    /**
     * 
     * @param message 
     */
    protected void parseFullResetMessage(String message) {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        if (message.matches("SRBND-F.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un full reset secondo {1}", new Object[]{message, "SRBND-F.*"});
            buttons.stream().forEach((button) -> {
                button.setStatus(PushButtonStatus.ENABLED);
            });
        }
    }

    /**
     * 
     * @param message 
     */
    protected void parseResetMessage(String message) {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        if (message.matches("SRBND-R.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un reste secondo {1}", new Object[]{message, "SRBND-R.*"});
            buttons.stream().forEach((button) -> {
                if (button.getStatus() == PushButtonStatus.PRESSED) {
                    button.setStatus(PushButtonStatus.ENABLED);
                }
            });
        }
    }

    /**
     * 
     * @param message 
     */
    protected void parseErrorMessage(String message) {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Effettuo il parsing del messaggio {0}", message);
        if (message.matches("SRBND-E.*")) {
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un errore secondo {1}", new Object[]{message, "SRBND-E.*"});
            buttons.stream().forEach((button) -> {
                if (button.getStatus() == PushButtonStatus.PRESSED) {
                    button.setStatus(PushButtonStatus.ERROR);
                }
            });
        }
    }

    /*
     * Avvia il servizio UDP
     */
    public void startServer() {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Avvio il server");
        // Se non 
        if (udpservice == null) {
            this.initUDPService();
        }
        
        // Ripulisce lo stato del task
        if ((udpservice.getState() == Worker.State.CANCELLED) || (udpservice.getState() == Worker.State.FAILED)) {
            udpservice.reset();
        }

        // Se il server è pronto effettua lo start del service
        if (udpservice.getState() == Worker.State.READY) {
            udpservice.start();
        }
    }

    /**
     * Disattiva il servizio UDP invocando la cancellazione del servizio e
     * spedento un pacchetto per bypassare il fatto che la lettura del socket è
     * bloccante
     */
    public void stopServer() {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Spengo il server");
        if (udpservice != null) {
            if (udpservice.isRunning()) {
                // Invia al servizio il comando di spegnersi
                udpservice.cancel();

                // Invia un pacchetto UDP generico a se stesso per andare a far 
                // uscire il server dallo stato di listen necessario in quanto 
                // la lettura del socket è bloccante
                sendPacket(new StringBuilder()
                        .append(SarabandaController.MESSAGE_HEADER)
                        .append(message)
                        .toString(),
                        udpListenPort,
                        broadcastAddress);
            }
        }
    }

    /**
     * 
     */
    public void sendSarabandaReset() {
        sendSarabandaMessage(SarabandaController.RESET_COMMAND);
    }

    /**
     * 
     */
    public void sendSarabandaFullReset() {
        sendSarabandaMessage(SarabandaController.FULLRESET_COMMAND);
    }

    /**
     * 
     */
    public void sendSarabandaError() {
        sendSarabandaMessage(SarabandaController.ERROR_COMMAND);
    }

    /**
     * 
     */
    public void sendSarabandaDemo() {
        sendSarabandaMessage(SarabandaController.DEMO_COMMAND);
    }

    /**
     * 
     */
    public void sendSarabandaMasterPhysicalReset() {
        sendSarabandaMessage(SarabandaController.HWRESET_COMMAND);
    }
    
    /**
     * 
     */
    public void sendPushButtonStatus() {
        Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Invio lo stato dei pulsanti");
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);

        buttons.stream().forEach((button) -> {
            _message.append(button.getStatus());
        });

        sendSarabandaMessage(_message.toString());
    }

    /**
     * 
     * @param buttonId 
     */
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

    /**
     * 
     * @return 
     */
    public ReadOnlyBooleanProperty classicModeProperty() {
        return classicModeProperty.getReadOnlyProperty();
    }

    /**
     * 
     * @return 
     */
    public ReadOnlyBooleanProperty onlyLocalhostModeProperty() {
        return onlyLocalhostModeProperty.getReadOnlyProperty();
    }
}
