/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.PerUnit;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfVscConverterStationImpl extends AbstractLfGenerator {

    private final VscConverterStation station;

    private final double targetQ;

    private LfVscConverterStationImpl(VscConverterStation station, boolean breakers, LfNetworkLoadingReport report) {
        super(getHvdcLineTargetP(station));
        this.station = station;

        boolean outOfService = HvdcConverterStations.isOutOfService(station.getHvdcLine());

        // local control only
        if (!outOfService && station.isVoltageRegulatorOn()) {
            setVoltageControl(station.getVoltageSetpoint(), station.getTerminal(), breakers, report);
        }

        targetQ =  outOfService ? 0 : station.getReactivePowerSetpoint() / PerUnit.SB;
    }

    public static LfVscConverterStationImpl create(VscConverterStation station, boolean breakers, LfNetworkLoadingReport report) {
        Objects.requireNonNull(station);
        return new LfVscConverterStationImpl(station, breakers, report);
    }

    private static double getHvdcLineTargetP(VscConverterStation vscCs) {
        HvdcLine line = vscCs.getHvdcLine();
        if (HvdcConverterStations.isOutOfService(line)) {
            return 0;
        } else {
            // The active power setpoint is always positive.
            // If the converter station is at side 1 and is rectifier, targetP should be negative.
            // If the converter station is at side 1 and is inverter, targetP should be positive.
            // If the converter station is at side 2 and is rectifier, targetP should be negative.
            // If the converter station is at side 2 and is inverter, targetP should be positive.
            boolean isConverterStationRectifier = HvdcConverterStations.isRectifier(vscCs);
            return (isConverterStationRectifier ? -1 : 1) * line.getActivePowerSetpoint() * (1 + (isConverterStationRectifier ? 1 : -1) * vscCs.getLossFactor() / 100);
        }
    }

    @Override
    public String getId() {
        return station.getId();
    }

    @Override
    public double getTargetQ() {
        return targetQ;
    }

    @Override
    public double getMinP() {
        return -station.getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return station.getHvdcLine().getMaxP() / PerUnit.SB;
    }

    @Override
    public boolean isParticipating() {
        return false;
    }

    @Override
    public double getParticipationFactor() {
        return 0;
    }

    @Override
    protected Optional<ReactiveLimits> getReactiveLimits() {
        return Optional.of(station.getReactiveLimits());
    }

    @Override
    public void updateState() {
        if (HvdcConverterStations.isOutOfService(station.getHvdcLine())) {
            station.getTerminal()
                    .setP(0)
                    .setQ(0);
        } else {
            station.getTerminal()
                    .setP(-targetP)
                    .setQ(Double.isNaN(calculatedQ) ? -station.getReactivePowerSetpoint() : -calculatedQ);
        }
    }
}
