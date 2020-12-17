/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcEquationSystemCreationParameters {

    private final boolean voltageRemoteControl;

    private final boolean phaseControl;

    private final boolean transformerVoltageControl;

    private final boolean useBusPVLQ;

    public AcEquationSystemCreationParameters(boolean voltageRemoteControl, boolean phaseControl, boolean transformerVoltageControl, boolean useBusPVLQ) {
        this.voltageRemoteControl = voltageRemoteControl;
        this.phaseControl = phaseControl;
        this.transformerVoltageControl = transformerVoltageControl;
        this.useBusPVLQ = useBusPVLQ;
    }

    public boolean isVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public boolean isTransformerVoltageControl() {
        return transformerVoltageControl;
    }

    public boolean isUseBusPVLQ() {
        return useBusPVLQ;
    }
}
