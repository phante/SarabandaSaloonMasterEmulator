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
    
    final String buttonCommandRegex;

    // Porta di invio
    private final static int UDP_SENDPORT = 8889;
    // Porta di ascolto
    private final static int UDP_RECEIVEPORT = 8888;

    // Memorizzare l'indirizzo di broadcast
    private InetAddress broadcastAddress;

    // Server UDP per la comunicazione con il master
    private UDPServerService udpservice;
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
        // Imposta l'indirizzo di broascast
        //broadcastAddress = InetAddress.getByName("255.255.255.255");
        broadcastAddress = InetAddress.getLoopbackAddress(); // TEST per non spammare sulla rete
        
        // Inizializza il Service UDP
        initUDPService(UDP_RECEIVEPORT);

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
        for (PushButtonStatus status: PushButtonStatus.values()) {
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
     */
    private void initUDPService(int port) {
        try {


            InetAddress localAddress = InetAddress.getLocalHost();

            // Creao il servizio
            udpservice = new UDPServerService(port);

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
            Logger.getLogger(SarabandaController.class.getName()).log(Level.INFO, "Il messaggio {0} indica un cambio di stato dei pulsanti", message);
            String button = message.substring(message.length() - 4);
            for (int i = 0; buttons.size() > i; i++) {
                PushButtonStatus status = PushButtonStatus.parse(button.substring(i, i + 1));
                buttons.get(i).setStatus(status);
            }
        }
    }

    /**
     * Avvia il servizio UDP
     */
    public void startServer() {
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
        if (udpservice.isRunning()) {
            // Invia al servizio il comando di spegnersi
            udpservice.cancel();

            // Invia un pacchetto UDP generico per andare a far uscire il server dallo stato di listen
            // necessario in quanto la lettura del socket è bloccante
            sendSarabandaMessage("");
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

    /**
     * Invia un comando di disabilitazione dei pulsanti, utile per evitare pressioni fuori sequenza
     * in una fase di gioco fermo
     * 
     */
    public void enableAllPushButton() {
        StringBuffer _message = new StringBuffer().append(SarabandaController.BUTTON_COMMAND);
        
        buttons.stream().forEach((_item) -> {
            _message.append(_item.getStatus() == PushButtonStatus.DISABLED? PushButtonStatus.ENABLED: _item.getStatus());
        });
                
        sendSarabandaMessage(_message.toString());
    }
    
    /**
     * Invia un comando di disabilitazione dei pulsanti, utile per evitare pressioni fuori sequenza
     * in una fase di gioco fermo
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
            _message.append(_item.getStatus() == PushButtonStatus.PRESSED? _item.getStatus(): PushButtonStatus.ERROR);
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
                UDP_SENDPORT,
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
