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

import static com.phante.sarabandasaloon.network.SarabandaController.UDP_MASTER_PORT;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author deltedes
 */
public class SarabandaMasterController extends SarabandaController{

    /**
     * Inizializza lo stato del controller andando a creare il servizio che si
     * occupa della lettura dei pacchetti di rete e i pulsanti.
     */
    private SarabandaMasterController() {
        super();
        
        // Imposta la modalità di funzionamento per il master a nuovo
        udpSendPort = UDP_SLAVE_PORT;
        udpListenPort = UDP_MASTER_PORT;
        classicModeProperty.setValue(Boolean.FALSE);
        
        Logger.getLogger(SarabandaMasterController.class.getName()).log(Level.INFO, "Impostazione del master in ascolto su porta {0} con invio su porta {1}", new Object[]{udpListenPort, udpSendPort});
        Logger.getLogger(SarabandaMasterController.class.getName()).log(Level.INFO, "Impostazione del master con invio messaggi verso ip {0}", broadcastAddress.getHostAddress());
    }

    public static SarabandaMasterController getInstance() {
        return SarabandaMasterControllerHolder.INSTANCE;
    }

    private static class SarabandaMasterControllerHolder {
        private static final SarabandaMasterController INSTANCE = new SarabandaMasterController();
    }
}
