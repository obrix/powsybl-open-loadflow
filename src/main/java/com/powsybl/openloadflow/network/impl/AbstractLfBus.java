/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBus implements LfBus {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBus.class);

    private static final double POWER_EPSILON_SI = 1e-4;
    private static final double Q_DISPATCH_EPSILON = 1e-3;

    private int num = -1;

    protected boolean slack = false;

    protected double v;

    protected double angle;

    protected double calculatedQ = Double.NaN;

    protected boolean voltageControlCapability = false;

    protected boolean voltageControlEnabled = false;

    protected int voltageControlSwitchOffCount = 0;

    protected double initialLoadTargetP = 0;

    protected double loadTargetP = 0;

    protected double fixedLoadTargetP = 0;

    protected int positiveLoadCount = 0;

    protected double loadTargetQ = 0;

    protected double generationTargetQ = 0;

    protected final List<LfGenerator> generators = new ArrayList<>();

    protected final List<LfShunt> shunts = new ArrayList<>();

    protected final List<Load> loads = new ArrayList<>();

    protected final List<Battery> batteries = new ArrayList<>();

    protected final List<LccConverterStation> lccCss = new ArrayList<>();

    protected final List<LfBranch> branches = new ArrayList<>();

    private VoltageControl voltageControl;

    protected AbstractLfBus(double v, double angle) {
        this.v = v;
        this.angle = angle;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public boolean isSlack() {
        return slack;
    }

    @Override
    public void setSlack(boolean slack) {
        this.slack = slack;
    }

    @Override
    public double getTargetP() {
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        return getGenerationTargetQ() - getLoadTargetQ();
    }

    @Override
    public boolean hasVoltageControllerCapability() {
        return voltageControlCapability;
    }

    @Override
    public VoltageControl getVoltageControl() {
        return voltageControl;
    }

    @Override
    public void setVoltageControl(VoltageControl voltageControl) {
        this.voltageControl = voltageControl;
    }

    @Override
    public boolean isVoltageControlled() {
        return voltageControl != null && voltageControl.getControlledBus() == this;
    }

    @Override
    public boolean isVoltageController() {
        return voltageControlEnabled;
    }

    @Override
    public void setVoltageControl(boolean voltageControl) {
        if (this.voltageControlEnabled && !voltageControl) {
            voltageControlSwitchOffCount++;
        }
        this.voltageControlEnabled = voltageControl;
    }

    @Override
    public int getVoltageControlSwitchOffCount() {
        return voltageControlSwitchOffCount;
    }

    @Override
    public void setVoltageControlSwitchOffCount(int voltageControlSwitchOffCount) {
        this.voltageControlSwitchOffCount = voltageControlSwitchOffCount;
    }

    void addLoad(Load load) {
        loads.add(load);
        initialLoadTargetP += load.getP0();
        loadTargetP += load.getP0();
        LoadDetail loadDetail = load.getExtension(LoadDetail.class);
        if (loadDetail != null) {
            fixedLoadTargetP = loadDetail.getFixedActivePower();
        }
        loadTargetQ += load.getQ0();
        if (load.getP0() >= 0) {
            positiveLoadCount++;
        }
    }

    void addBattery(Battery battery) {
        batteries.add(battery);
        initialLoadTargetP += battery.getP0();
        loadTargetP += battery.getP0();
        loadTargetQ += battery.getQ0();
    }

    void addLccConverterStation(LccConverterStation lccCs) {
        lccCss.add(lccCs);
        HvdcLine line = lccCs.getHvdcLine();
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        boolean isConverterStationRectifier = HvdcConverterStations.isRectifier(lccCs);
        double p = (isConverterStationRectifier ? 1 : -1) * line.getActivePowerSetpoint() *
                (1 + (isConverterStationRectifier ? 1 : -1) * lccCs.getLossFactor() / 100); // A LCC station has active losses.
        double q = Math.abs(p * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
        loadTargetP += p;
        loadTargetQ += q;
    }

    private void add(LfGenerator generator, boolean voltageControl, double targetQ,
                     LfNetworkLoadingReport report) {
        generators.add(generator);
        double maxRangeQ = generator.getMaxRangeQ();
        boolean discardGenerator = false;
        if (voltageControl && maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
            LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small",
                    generator.getId(), maxRangeQ);
            report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall++;
            discardGenerator = true;
        }
        if (voltageControl && Math.abs(generator.getTargetP()) < POWER_EPSILON_SI && generator.getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)",
                    generator.getId(), generator.getTargetP(), generator.getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            discardGenerator = true;
        }
        if (voltageControl && !discardGenerator) {
            this.voltageControlEnabled = true;
            this.voltageControlCapability = true;
        } else {
            if (!Double.isNaN(targetQ)) {
                generationTargetQ += targetQ;
            }
        }
    }

    void addGenerator(Generator generator, LfNetworkLoadingReport report) {
        add(LfGeneratorImpl.create(generator, report), generator.isVoltageRegulatorOn(),
            generator.getTargetQ(), report);
    }

    void addStaticVarCompensator(StaticVarCompensator staticVarCompensator, LfNetworkLoadingReport report) {
        if (staticVarCompensator.getRegulationMode() != StaticVarCompensator.RegulationMode.OFF) {
            add(LfStaticVarCompensatorImpl.create(staticVarCompensator),
                    staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE,
                -staticVarCompensator.getReactivePowerSetPoint(),
                    report);
        }
    }

    void addVscConverterStation(VscConverterStation vscCs, LfNetworkLoadingReport report) {
        add(LfVscConverterStationImpl.create(vscCs), vscCs.isVoltageRegulatorOn(),
            vscCs.getReactivePowerSetpoint(), report);
    }

    void addShuntCompensator(ShuntCompensator sc) {
        shunts.add(new LfShuntImpl(sc));
    }

    @Override
    public double getGenerationTargetP() {
        return generators.stream().mapToDouble(LfGenerator::getTargetP).sum();
    }

    @Override
    public double getGenerationTargetQ() {
        return generationTargetQ / PerUnit.SB;
    }

    @Override
    public void setGenerationTargetQ(double generationTargetQ) {
        this.generationTargetQ = generationTargetQ * PerUnit.SB;
    }

    @Override
    public double getLoadTargetP() {
        return loadTargetP / PerUnit.SB;
    }

    @Override
    public void setLoadTargetP(double loadTargetP) {
        this.loadTargetP = loadTargetP * PerUnit.SB;
    }

    @Override
    public double getFixedLoadTargetP() {
        return fixedLoadTargetP / PerUnit.SB;
    }

    @Override
    public int getPositiveLoadCount() {
        return positiveLoadCount;
    }

    @Override
    public double getLoadTargetQ() {
        return loadTargetQ / PerUnit.SB;
    }

    private double getLimitQ(ToDoubleFunction<LfGenerator> limitQ) {
        return generators.stream()
                .mapToDouble(generator -> generator.hasVoltageControl() ? limitQ.applyAsDouble(generator)
                                                                        : generator.getTargetQ())
                .sum();
    }

    @Override
    public double getMinQ() {
        return getLimitQ(LfGenerator::getMinQ);
    }

    @Override
    public double getMaxQ() {
        return getLimitQ(LfGenerator::getMaxQ);
    }

    @Override
    public double getV() {
        return v / getNominalV();
    }

    @Override
    public void setV(double v) {
        this.v = v * getNominalV();
    }

    @Override
    public double getAngle() {
        return angle;
    }

    @Override
    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public double getCalculatedQ() {
        return calculatedQ / PerUnit.SB;
    }

    @Override
    public void setCalculatedQ(double calculatedQ) {
        this.calculatedQ = calculatedQ * PerUnit.SB;
    }

    @Override
    public List<LfShunt> getShunts() {
        return shunts;
    }

    @Override
    public List<LfGenerator> getGenerators() {
        return generators;
    }

    @Override
    public List<LfBranch> getBranches() {
        return branches;
    }

    @Override
    public void addBranch(LfBranch branch) {
        branches.add(Objects.requireNonNull(branch));
    }

    private double dispatchQ(List<LfGenerator> generatorsThatControlVoltage, boolean reactiveLimits, double qToDispatch) {
        double residueQ = 0;
        Iterator<LfGenerator> itG = generatorsThatControlVoltage.iterator();
        while (itG.hasNext()) {
            LfGenerator generator = itG.next();
            double calculatedQ = qToDispatch / generatorsThatControlVoltage.size();
            if (reactiveLimits && calculatedQ < generator.getMinQ()) {
                generator.setCalculatedQ(generator.getCalculatedQ() + generator.getMinQ());
                residueQ += calculatedQ - generator.getMinQ();
                itG.remove();
            } else if (reactiveLimits && calculatedQ > generator.getMaxQ()) {
                generator.setCalculatedQ(generator.getCalculatedQ() + generator.getMaxQ());
                residueQ += calculatedQ - generator.getMaxQ();
                itG.remove();
            } else {
                generator.setCalculatedQ(generator.getCalculatedQ() + calculatedQ);
            }
        }
        return residueQ;
    }

    private void updateGeneratorsState(double generationQ, boolean reactiveLimits) {
        double qToDispatch = generationQ / PerUnit.SB;
        List<LfGenerator> generatorsThatControlVoltage = new LinkedList<>();
        for (LfGenerator generator : generators) {
            if (generator.hasVoltageControl()) {
                generatorsThatControlVoltage.add(generator);
            } else {
                qToDispatch -= generator.getTargetQ();
            }
        }

        for (LfGenerator generator : generatorsThatControlVoltage) {
            generator.setCalculatedQ(0);
        }
        while (!generatorsThatControlVoltage.isEmpty() && Math.abs(qToDispatch) > Q_DISPATCH_EPSILON) {
            qToDispatch = dispatchQ(generatorsThatControlVoltage, reactiveLimits, qToDispatch);
        }
    }

    @Override
    public void updateState(boolean reactiveLimits, boolean writeSlackBus) {
        // update generator reactive power
        updateGeneratorsState(voltageControlEnabled ? calculatedQ + loadTargetQ : generationTargetQ, reactiveLimits);

        // update load power
        double factor = initialLoadTargetP != 0 ? loadTargetP / initialLoadTargetP : 1;
        for (Load load : loads) {
            load.getTerminal()
                    .setP(load.getP0() >= 0 ? factor * load.getP0() : load.getP0())
                    .setQ(load.getQ0());
        }

        // update battery power (which are not part of slack distribution)
        for (Battery battery : batteries) {
            battery.getTerminal()
                    .setP(battery.getP0())
                    .setQ(battery.getQ0());
        }

        // update lcc converter station power
        for (LccConverterStation lccCs : lccCss) {
            boolean isConverterStationRectifier = HvdcConverterStations.isRectifier(lccCs);
            HvdcLine line = lccCs.getHvdcLine();
            double p = (isConverterStationRectifier ? 1 : -1) * line.getActivePowerSetpoint() *
                    (1 + (isConverterStationRectifier ? 1 : -1) * lccCs.getLossFactor() / 100); // A LCC station has active losses.
            double q = Math.abs(p * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
            lccCs.getTerminal()
                    .setP(p)
                    .setQ(q);
        }
    }

}
