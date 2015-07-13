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
package com.phante.sarabandasaloon.entity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author deltedes
 */
public enum PushButtonStatus {
    ENABLED("-"),
    PRESSED("O"),
    ERROR("X"),
    DISABLED("#");
    
    private final String name;       

    private PushButtonStatus(String s) {
        name = s;
    }
    
    @Override
    public String toString(){
       return name;
    }
    
    public static PushButtonStatus parse(String value) {
        for (PushButtonStatus status: PushButtonStatus.values()) {
            //Logger.getLogger(PushButtonStatus.class.getName()).log(Level.INFO, "Confronto {0} con {1}", new Object[]{value, status.toString()});
            if (value.equals(status.toString())) return status;
        }
        return ENABLED;
    }
}
