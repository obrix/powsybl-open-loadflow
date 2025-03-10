/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.network.AbstractLfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfSwitch extends AbstractLfBranch {

    private final Switch aSwitch;

    public LfSwitch(LfNetwork network, LfBus bus1, LfBus bus2, Switch aSwitch) {
        super(network, bus1, bus2, new SimplePiModel());
        this.aSwitch = Objects.requireNonNull(aSwitch);
    }

    @Override
    public String getId() {
        return aSwitch.getId();
    }

    @Override
    public boolean hasPhaseControlCapability() {
        return false;
    }

    @Override
    public void setP1(Evaluable p1) {
        // nothing to do
    }

    @Override
    public Evaluable getP1() {
        return NAN;
    }

    @Override
    public void setP2(Evaluable p2) {
        // nothing to do
    }

    @Override
    public Evaluable getP2() {
        return NAN;
    }

    @Override
    public void setQ1(Evaluable q1) {
        // nothing to do
    }

    @Override
    public Evaluable getQ1() {
        return NAN;
    }

    @Override
    public void setQ2(Evaluable q2) {
        // nothing to do
    }

    @Override
    public Evaluable getQ2() {
        return NAN;
    }

    @Override
    public void setI1(Evaluable i1) {
        // nothing to do
    }

    @Override
    public Evaluable getI1() {
        return NAN;
    }

    @Override
    public void setI2(Evaluable i2) {
        // nothing to do
    }

    @Override
    public Evaluable getI2() {
        return NAN;
    }

    @Override
    public List<LfLimit> getLimits1() {
        return Collections.emptyList();
    }

    @Override
    public List<LfLimit> getLimits2() {
        return Collections.emptyList();
    }

    @Override
    public void updateState(boolean phaseShifterRegulationOn, boolean isTransformerVoltageControlOn) {
        // nothing to do
    }
}
