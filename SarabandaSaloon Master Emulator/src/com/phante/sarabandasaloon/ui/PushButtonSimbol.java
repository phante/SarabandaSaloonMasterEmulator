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
package com.phante.sarabandasaloon.ui;

import com.phante.sarabandasaloon.entity.PushButtonStatus;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 *
 * @author deltedes
 */
public class PushButtonSimbol extends StackPane {
    public static final String ENABLED = "M0 80 L20 100 L100 20 L80 0 Z";
    public static final String PRESSED = "M0 30 L 0 70 L30 100 L70 100 L100 70 L100 30 L70 0 L30 0 Z";
    public static final String ERROR = "M0 10 L40 50 L0 90 L10 100 L50 60 L90 100 L100 90 L60 50 L100 10 L90 0 L50 40 L10 0 Z";
    public static final String DISABLED = "M0 20 L0 40 L20 40 L20 60 L0 60 L0 80 L20 80 L20 100 L40 100 L40 80 L60 80 L60 100 L80 100 L80 80 L100 80 L100 60 L80 60 L80 40 L100 40 L100 20 L80 20 L80 0 L60 0 L60 20 L40 20 L40 0 L20 0 L20 20 Z";
    
    public Map<PushButtonStatus, SVGPath> simbols = new HashMap<>();
            
    public PushButtonSimbol () {
        super();
        
        for (PushButtonStatus status: PushButtonStatus.values()) {
            SVGPath simbol = new SVGPath();
            simbol.setFillRule(FillRule.NON_ZERO);
            simbol.setVisible(status == PushButtonStatus.ENABLED);
            simbol.setContent(ERROR);
            
            getChildren().add(simbol);
            simbols.put(status, simbol);
            
            heightProperty().addListener(listener -> {
                simbol.scaleXProperty().setValue(getHeight()/simbol.maxHeight(Double.MAX_VALUE));
            });
            
            widthProperty().addListener(listener -> {
                simbol.scaleYProperty().setValue(getWidth()/simbol.maxWidth(Double.MAX_VALUE));
            });
        }
        
        simbols.get(PushButtonStatus.ENABLED).setContent(ENABLED);
        simbols.get(PushButtonStatus.ENABLED).setFill(Color.BLUE);
        simbols.get(PushButtonStatus.PRESSED).setContent(PRESSED);
        simbols.get(PushButtonStatus.PRESSED).setFill(Color.GREEN);
        simbols.get(PushButtonStatus.ERROR).setContent(ERROR);
        simbols.get(PushButtonStatus.ERROR).setFill(Color.RED);
        simbols.get(PushButtonStatus.DISABLED).setContent(DISABLED);
        simbols.get(PushButtonStatus.DISABLED).setFill(Color.GREY);
    }
    
    public void setValue(PushButtonStatus newStatus) {
        for (PushButtonStatus status: PushButtonStatus.values()) {
            simbols.get(status).setVisible(status == newStatus);
        }
    }
}
