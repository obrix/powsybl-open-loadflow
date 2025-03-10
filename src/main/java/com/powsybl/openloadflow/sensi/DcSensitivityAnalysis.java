/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.BranchState;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.PropagatedContingency;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gaël Macherel <gael.macherel@artelys.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static final double CONNECTIVITY_LOSS_THRESHOLD = 10e-7;

    static class ComputedContingencyElement {

        private int contingencyIndex = -1; // index of the element in the rhs for +1-1
        private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
        private double alphaForSensitivityValue = Double.NaN;
        private double alphaForFunctionReference = Double.NaN;
        private final ContingencyElement element;
        private final LfBranch lfBranch;
        private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

        public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem equationSystem) {
            this.element = element;
            lfBranch = lfNetwork.getBranchById(element.getId());
            branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        }

        public int getContingencyIndex() {
            return contingencyIndex;
        }

        public void setContingencyIndex(final int index) {
            this.contingencyIndex = index;
        }

        public int getLocalIndex() {
            return localIndex;
        }

        public void setLocalIndex(final int index) {
            this.localIndex = index;
        }

        public double getAlphaForSensitivityValue() {
            return alphaForSensitivityValue;
        }

        public void setAlphaForSensitivityValue(final double alpha) {
            this.alphaForSensitivityValue = alpha;
        }

        public double getAlphaForFunctionReference() {
            return alphaForFunctionReference;
        }

        public void setAlphaForFunctionReference(final double alpha) {
            this.alphaForFunctionReference = alpha;
        }

        public ContingencyElement getElement() {
            return element;
        }

        public LfBranch getLfBranch() {
            return lfBranch;
        }

        public ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
            return branchEquation;
        }

        public static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setContingencyIndex(index++);
            }
        }

        public static void setLocalIndexes(Collection<ComputedContingencyElement> elements) {
            int index = 0;
            for (ComputedContingencyElement element : elements) {
                element.setLocalIndex(index++);
            }
        }

    }

    static class PhaseTapChangerContingenciesIndexing {

        private Collection<PropagatedContingency> contingenciesWithoutTransformers;
        private Map<Set<LfBranch>, Collection<PropagatedContingency>> contingenciesIndexedByPhaseTapChangers;

        public PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
            this(contingencies, contingencyElementByBranch, Collections.emptySet());
        }

        public PhaseTapChangerContingenciesIndexing(Collection<PropagatedContingency> contingencies, Map<String,
                ComputedContingencyElement> contingencyElementByBranch, Collection<String> elementIdsToSkip) {
            contingenciesIndexedByPhaseTapChangers = new HashMap<>();
            contingenciesWithoutTransformers = new ArrayList<>();
            for (PropagatedContingency contingency : contingencies) {
                Set<LfBranch> lostTransformers = contingency.getBranchIdsToOpen().stream()
                        .filter(element -> !elementIdsToSkip.contains(element))
                        .map(contingencyElementByBranch::get)
                        .map(ComputedContingencyElement::getLfBranch)
                        .filter(LfBranch::hasPhaseControlCapability)
                        .collect(Collectors.toSet());
                if (lostTransformers.isEmpty()) {
                    contingenciesWithoutTransformers.add(contingency);
                } else {
                    contingenciesIndexedByPhaseTapChangers.computeIfAbsent(lostTransformers, key -> new ArrayList<>()).add(contingency);
                }
            }
        }

        public Collection<PropagatedContingency> getContingenciesWithoutPhaseTapChangerLoss() {
            return contingenciesWithoutTransformers;
        }

        public Map<Set<LfBranch>, Collection<PropagatedContingency>> getContingenciesIndexedByPhaseTapChangers() {
            return contingenciesIndexedByPhaseTapChangers;
        }
    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(matrixFactory, connectivityProvider);
    }

    protected DenseMatrix setReferenceActivePowerFlows(DcLoadFlowEngine dcLoadFlowEngine, EquationSystem equationSystem, JacobianMatrix j,
                                                       List<LfSensitivityFactor> factors, LoadFlowParameters lfParameters,
                                                       List<ParticipatingElement> participatingElements, Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches) {

        Map<LfBus, BusState> busStates = new HashMap<>();
        if (lfParameters.isDistributedSlack()) {
            busStates = BusState.createBusStates(participatingElements.stream()
                .map(ParticipatingElement::getLfBus)
                .collect(Collectors.toSet()));
        }
        // the A1 variables will be set to 0 for disabledBranches, so we need to restore them at the end
        Map<LfBranch, BranchState> branchStates = BranchState.createBranchStates(disabledBranches);

        dcLoadFlowEngine.run(equationSystem, j, disabledBuses, disabledBranches);

        for (LfSensitivityFactor factor : factors) {
            factor.setFunctionReference(factor.getFunctionLfBranch().getP1().eval());
        }

        if (lfParameters.isDistributedSlack()) {
            BusState.restoreBusActiveStates(busStates);
        }
        BranchState.restoreBranchStates(branchStates);

        double[] dx = dcLoadFlowEngine.getTargetVector();
        return new DenseMatrix(dx.length, 1, dx);
    }

    private void createBranchSensitivityValue(LfSensitivityFactor factor, DenseMatrix contingenciesStates,
                                              Collection<ComputedContingencyElement> contingencyElements,
                                              String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter) {
        double sensiValue;
        double flowValue;
        EquationTerm p1 = factor.getEquationTerm();
        if (factor.getPredefinedResult() != null) {
            sensiValue = factor.getPredefinedResult();
            flowValue = factor.getPredefinedResult();
        } else {
            sensiValue = factor.getBaseSensitivityValue();
            flowValue = factor.getFunctionReference();
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                if (contingencyElement.getElement().getId().equals(factor.getFunctionId())
                        || contingencyElement.getElement().getId().equals(factor.getVariableId())) {
                    // the sensitivity on a removed branch is 0, the sensitivity if the variable was a removed branch is 0
                    sensiValue = 0d;
                    flowValue = 0d;
                    break;
                }
                double contingencySensitivity = p1.calculateSensi(contingenciesStates, contingencyElement.getContingencyIndex());
                flowValue += contingencyElement.getAlphaForFunctionReference() * contingencySensitivity;
                sensiValue +=  contingencyElement.getAlphaForSensitivityValue() * contingencySensitivity;
            }
        }
        valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, sensiValue * PerUnit.SB, flowValue * PerUnit.SB);
    }

    protected void setBaseCaseSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor factor : factorGroup.getFactors()) {
                factor.setBaseCaseSensitivityValue(factor.getEquationTerm().calculateSensi(factorsState, factorGroup.getIndex()));
            }
        }
    }

    protected void calculateSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorStates,
                                              DenseMatrix contingenciesStates, DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements,
                                              String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter) {
        setAlphas(contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            setAlphas(contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForSensitivityValue);
            for (LfSensitivityFactor factor : factorGroup.getFactors()) {
                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingencyId, contingencyIndex, valueWriter);
            }
        }
    }

    private void setAlphas(Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                           DenseMatrix contingenciesStates, int columnState, BiConsumer<ComputedContingencyElement, Double> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = lfBranch.getPiModel().getX() / PerUnit.SB - (contingenciesStates.get(p1.getVariables().get(0).getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getVariables().get(1).getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getVariables().get(0).getRow(), columnState) - states.get(p1.getVariables().get(1).getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            // FIXME: direct resolution if contingencyElements.size() == 2
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getVariables().get(0).getRow(), columnState)
                        - states.get(p1.getVariables().get(1).getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = lfBranch.getPiModel().getX() / PerUnit.SB;
                    }
                    value = value - (contingenciesStates.get(p1.getVariables().get(0).getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getVariables().get(1).getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            LUDecomposition lu = matrix.decomposeLU();
            lu.solve(rhs); // rhs now contains state matrix
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
        }
    }

    private Set<ComputedContingencyElement> getGroupOfElementsBreakingConnectivity(LfNetwork lfNetwork, DenseMatrix contingenciesStates,
                                                                                   Collection<ComputedContingencyElement> contingencyElements,
                                                                                   EquationSystem equationSystem) {
        // use a sensitivity-criterion to detect the loss of connectivity after a contingency
        // we consider a +1 -1 on a line, and we observe the sensitivity of these injections on the other contingency elements
        // if the sum of the sensitivities (in absolute value) is 1, it means that all the flow is going through the lines with a non-zero sensitivity
        // thus, losing these lines will lose the connectivity
        Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = new HashSet<>();
        for (ComputedContingencyElement element : contingencyElements) {
            Set<ComputedContingencyElement> responsibleElements = new HashSet<>();
            double sum = 0d;
            for (ComputedContingencyElement element2 : contingencyElements) {
                LfBranch branch = lfNetwork.getBranchById(element2.getElement().getId());
                ClosedBranchSide1DcFlowEquationTerm p = equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                double value = Math.abs(p.calculateSensi(contingenciesStates, element.getContingencyIndex()));
                if (value > CONNECTIVITY_LOSS_THRESHOLD) {
                    responsibleElements.add(element2);
                }
                sum += value;
            }
            if (sum * PerUnit.SB > 1d - CONNECTIVITY_LOSS_THRESHOLD) {
                // all lines that have a non-0 sensitivity associated to "element" breaks the connectivity
                groupOfElementsBreakingConnectivity.addAll(responsibleElements);
            }
        }
        return groupOfElementsBreakingConnectivity;
    }

    protected void fillRhsContingency(final LfNetwork lfNetwork, final EquationSystem equationSystem,
                                      final Collection<ComputedContingencyElement> contingencyElements, final Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements) {
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation p = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
            } else if (bus2.isSlack()) {
                Equation p = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
            } else {
                Equation p1 = equationSystem.getEquation(bus1.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                Equation p2 = equationSystem.getEquation(bus2.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getContingencyIndex(), 1 / PerUnit.SB);
                rhs.set(p2.getColumn(), element.getContingencyIndex(), -1 / PerUnit.SB);
            }
        }
    }

    protected DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Collection<ComputedContingencyElement> contingencyElements) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    private void detectPotentialConnectivityLoss(LfNetwork lfNetwork, DenseMatrix states, List<PropagatedContingency> contingencies,
                                                 Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                 EquationSystem equationSystem, Collection<PropagatedContingency> nonLosingConnectivityContingencies,
                                                 Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity) {
        for (PropagatedContingency contingency : contingencies) {
            Set<ComputedContingencyElement> groupOfElementsBreakingConnectivity = getGroupOfElementsBreakingConnectivity(lfNetwork, states,
                    contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList()), equationSystem);
            if (groupOfElementsBreakingConnectivity.isEmpty()) { // connectivity not broken
                nonLosingConnectivityContingencies.add(contingency);
            } else {
                contingenciesByGroupOfElementsBreakingConnectivity.computeIfAbsent(groupOfElementsBreakingConnectivity, key -> new LinkedList<>()).add(contingency);
            }
        }
    }

    static class ConnectivityAnalysisResult {

        private Map<LfSensitivityFactor, Double> predefinedResults;

        private Collection<PropagatedContingency> contingencies = new HashSet<>();

        private Set<String> elementsToReconnect;

        private Set<LfBus> disabledBuses;

        private Set<LfBus> slackConnectedComponent;

        ConnectivityAnalysisResult(Collection<LfSensitivityFactor> factors, Set<ComputedContingencyElement> elementsBreakingConnectivity,
                                   GraphDecrementalConnectivity<LfBus> connectivity, LfNetwork lfNetwork) {
            elementsToReconnect = computeElementsToReconnect(connectivity, elementsBreakingConnectivity);
            disabledBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
            slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
            slackConnectedComponent.removeAll(disabledBuses);
            predefinedResults = new HashMap<>();
            for (LfSensitivityFactor factor : factors) {
                // check if the factor function and variable are in different connected components
                if (factor.areVariableAndFunctionDisconnected(connectivity)) {
                    predefinedResults.put(factor, 0d);
                } else if (!factor.isConnectedToComponent(slackConnectedComponent)) {
                    predefinedResults.put(factor, Double.NaN); // works for sensitivity and function reference
                }
            }
        }

        public Double getPredefinedResult(LfSensitivityFactor lfSensitivityFactor) {
            return predefinedResults.get(lfSensitivityFactor);
        }

        public Collection<PropagatedContingency> getContingencies() {
            return contingencies;
        }

        public Set<String> getElementsToReconnect() {
            return elementsToReconnect;
        }

        public Set<LfBus> getDisabledBuses() {
            return disabledBuses;
        }

        public Set<LfBus> getSlackConnectedComponent() {
            return slackConnectedComponent;
        }

        private static Set<String> computeElementsToReconnect(GraphDecrementalConnectivity<LfBus> connectivity, Set<ComputedContingencyElement> breakingConnectivityCandidates) {
            Set<String> elementsToReconnect = new HashSet<>();

            Map<Pair<Integer, Integer>, ComputedContingencyElement> elementByConnectedComponents = new HashMap<>();
            for (ComputedContingencyElement element : breakingConnectivityCandidates) {
                int bus1Cc = connectivity.getComponentNumber(element.getLfBranch().getBus1());
                int bus2Cc = connectivity.getComponentNumber(element.getLfBranch().getBus2());

                Pair<Integer, Integer> pairOfCc = bus1Cc > bus2Cc ? Pair.of(bus2Cc, bus1Cc) : Pair.of(bus1Cc, bus2Cc);
                // we only need to reconnect one line to restore connectivity
                elementByConnectedComponents.put(pairOfCc, element);
            }

            Map<Integer, Set<Integer>> connections = new HashMap<>();
            for (int i = 0; i < connectivity.getSmallComponents().size() + 1; i++) {
                connections.put(i, Collections.singleton(i));
            }

            for (Map.Entry<Pair<Integer, Integer>, ComputedContingencyElement> elementsByCc : elementByConnectedComponents.entrySet()) {
                Integer cc1 = elementsByCc.getKey().getKey();
                Integer cc2 = elementsByCc.getKey().getValue();
                if (connections.get(cc1).contains(cc2)) {
                    // cc are already connected
                    continue;
                }
                elementsToReconnect.add(elementsByCc.getValue().getElement().getId());
                Set<Integer> newCc = new HashSet<>();
                newCc.addAll(connections.get(cc1));
                newCc.addAll(connections.get(cc2));
                newCc.forEach(integer -> connections.put(integer, newCc));
            }

            return elementsToReconnect;
        }
    }

    private Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> computeConnectivityData(LfNetwork lfNetwork, final Collection<LfSensitivityFactor> lfFactors,
                                                                                                     Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity,
                                                                                                     Collection<PropagatedContingency> nonLosingConnectivityContingencies) {
        Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = new HashMap<>();
        if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
            return connectivityAnalysisResults;
        }

        GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity(connectivityProvider);
        for (Map.Entry<Set<ComputedContingencyElement>, List<PropagatedContingency>> groupOfElementPotentiallyBreakingConnectivity : contingenciesByGroupOfElementsBreakingConnectivity.entrySet()) {
            Set<ComputedContingencyElement> breakingConnectivityCandidates = groupOfElementPotentiallyBreakingConnectivity.getKey();
            List<PropagatedContingency> contingencyList = groupOfElementPotentiallyBreakingConnectivity.getValue();
            cutConnectivity(lfNetwork, connectivity, breakingConnectivityCandidates.stream().map(ComputedContingencyElement::getElement).map(ContingencyElement::getId).collect(Collectors.toSet()));

            // filter the branches that really impacts connectivity
            Set<ComputedContingencyElement> breakingConnectivityElements = breakingConnectivityCandidates.stream().filter(element -> {
                LfBranch lfBranch = element.getLfBranch();
                return connectivity.getComponentNumber(lfBranch.getBus1()) != connectivity.getComponentNumber(lfBranch.getBus2());
            }).collect(Collectors.toSet());
            if (breakingConnectivityElements.isEmpty()) {
                // we did not break any connectivity
                nonLosingConnectivityContingencies.addAll(contingencyList);
            } else {
                connectivityAnalysisResults.computeIfAbsent(breakingConnectivityElements, branches -> new ConnectivityAnalysisResult(lfFactors, branches, connectivity, lfNetwork)).getContingencies().addAll(contingencyList);
            }
            connectivity.reset();
        }
        return connectivityAnalysisResults;
    }

    public void analyse(Network network, List<PropagatedContingency> contingencies, LoadFlowParameters lfParameters,
                        OpenLoadFlowParameters lfParametersExt, SensitivityFactorReader factorReader, SensitivityValueWriter valueWriter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(valueWriter);

        // create the network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkParameters(lfParametersExt.getSlackBusSelector(), false, true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(), false));
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkLoadFlowParameters(lfParameters);

        List<LfSensitivityFactor> lfFactors = readAndCheckFactors(network, factorReader, lfNetwork);

        lfFactors.stream()
                .filter(lfFactor -> !(lfFactor instanceof LfBranchFlowPerInjectionIncrease)
                        && !(lfFactor instanceof LfBranchFlowPerLinearGlsk)
                        && !(lfFactor instanceof LfBranchFlowPerPSTAngle))
                .findFirst()
                .ifPresent(ignored -> {
                    throw new PowsyblException("Only sensitivity factors of type LfBranchFlowPerInjectionIncrease, LfBranchFlowPerLinearGlsk and LfBranchFlowPerPSTAngle are yet supported in DC");
                });

        LOGGER.info("Running DC sensitivity analysis with {} factors and {} contingencies",  lfFactors.size(), contingencies.size());

        // create DC load flow engine for setting the function reference
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters(lfParametersExt.getSlackBusSelector(), matrixFactory,
            true, lfParametersExt.isDcUseTransformerRatio(), lfParameters.isDistributedSlack(), lfParameters.getBalanceType(), true,
            lfParametersExt.getPlausibleActivePowerLimit(), lfParametersExt.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds());
        DcLoadFlowEngine dcLoadFlowEngine = new DcLoadFlowEngine(lfNetworks, dcLoadFlowParameters);

        // create DC equation system for sensitivity analysis
        DcEquationSystemCreationParameters dcEquationSystemCreationParameters = new DcEquationSystemCreationParameters(dcLoadFlowParameters.isUpdateFlows(), true,
            dcLoadFlowParameters.isForcePhaseControlOffAndAddAngle1Var(), lfParametersExt.isDcUseTransformerRatio());
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(), dcEquationSystemCreationParameters);

        // we wrap the factor into a class that allows us to have access to their branch and EquationTerm instantly
        List<LfSensitivityFactor> zeroFactors = lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.ZERO).collect(Collectors.toList());
        warnSkippedFactors(lfFactors);
        lfFactors = lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.VALID)).collect(Collectors.toList());
        zeroFactors.forEach(lfFactor -> valueWriter.write(lfFactor.getContext(), null, -1, 0, Double.NaN));
        // index factors by variable group to compute the minimal number of states
        List<SensitivityFactorGroup> factorGroups = createFactorGroups(lfFactors);

        boolean hasGlsk = factorGroups.stream().anyMatch(group -> group instanceof LinearGlskGroup);

        // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
        // buses that contain elements participating to slack distribution)
        List<ParticipatingElement> participatingElements = null;
        Map<LfBus, Double> slackParticipationByBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters, lfParametersExt);
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                ParticipatingElement::getLfBus,
                element -> -element.getFactor(),
                Double::sum
            ));
        } else {
            slackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus(), -1d);
        }
        computeInjectionFactors(slackParticipationByBus, factorGroups);

        // prepare management of contingencies
        Map<String, ComputedContingencyElement> contingencyElementByBranch =
            contingencies.stream()
                             .flatMap(contingency -> contingency.getBranchIdsToOpen().stream())
                             .map(branch -> new ComputedContingencyElement(new BranchContingency(branch), lfNetwork, equationSystem))
                             .filter(element -> element.getLfBranchEquation() != null)
                             .collect(Collectors.toMap(
                                 computedContingencyElement -> computedContingencyElement.getElement().getId(),
                                 computedContingencyElement -> computedContingencyElement,
                                 (existing, replacement) -> existing
                             ));
        ComputedContingencyElement.setContingencyIndexes(contingencyElementByBranch.values());

        // create jacobian matrix either using calculated voltages from pre-contingency network or nominal voltages
        VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        try (JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer)) {

            // run DC load on pre-contingency network
            DenseMatrix flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactors, lfParameters, participatingElements, Collections.emptyList(), Collections.emptyList());

            // compute the pre-contingency sensitivity values + the states with +1 -1 to model the contingencies
            DenseMatrix factorsStates = initFactorsRhs(lfNetwork, equationSystem, factorGroups); // this is the rhs for the moment
            DenseMatrix contingenciesStates = initContingencyRhs(lfNetwork, equationSystem, contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
            j.solveTransposed(factorsStates); // states for the sensitivity factors
            j.solveTransposed(contingenciesStates); // states for the +1 -1 of contingencies

            // sensitivity values for pre-contingency network
            setBaseCaseSensitivityValues(factorGroups, factorsStates);
            calculateSensitivityValues(factorGroups, factorsStates, contingenciesStates, flowStates, Collections.emptyList(), null, -1, valueWriter);

            // connectivity analysis by contingency
            // we have to compute sensitivities and reference functions in a different way depending on either or not the contingency breaks connectivity
            // so, we will index contingencies by a list of branch that may breaks connectivity
            // for example, if in the network, loosing line L1 breaks connectivity, and loosing L2 and L3 together breaks connectivity,
            // the index would be: {L1, L2, L3}
            // a contingency involving a phase tap changer loss has to be treated separately
            Collection<PropagatedContingency> nonLosingConnectivityContingencies = new LinkedList<>();
            Map<Set<ComputedContingencyElement>, List<PropagatedContingency>> contingenciesByGroupOfElementsBreakingConnectivity = new HashMap<>();

            detectPotentialConnectivityLoss(lfNetwork, contingenciesStates, contingencies, contingencyElementByBranch, equationSystem,
                    nonLosingConnectivityContingencies, contingenciesByGroupOfElementsBreakingConnectivity);

            // process connectivity data for all contingencies that potentially lose connectivity
            Map<Set<ComputedContingencyElement>, ConnectivityAnalysisResult> connectivityAnalysisResults = computeConnectivityData(lfNetwork, lfFactors, contingenciesByGroupOfElementsBreakingConnectivity, nonLosingConnectivityContingencies);

            PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(nonLosingConnectivityContingencies, contingencyElementByBranch);

            // compute the contingencies without loss of connectivity
            // first we compute the ones without loss of phase tap changers (because we reuse the load flows from the pre contingency network for all of them)
            for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
                zeroFactors.forEach(lfFactor -> valueWriter.write(lfFactor.getContext(), contingency.getContingency().getId(), contingency.getIndex(), 0, Double.NaN));
                List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
                calculateSensitivityValues(factorGroups, factorsStates, contingenciesStates, flowStates, contingencyElements,
                        contingency.getContingency().getId(), contingency.getIndex(), valueWriter);
            }

            // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
            for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> entry : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
                Set<LfBranch> removedPhaseTapChangers = entry.getKey();
                Collection<PropagatedContingency> propagatedContingencies = entry.getValue();
                flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactors, lfParameters, participatingElements, Collections.emptyList(), removedPhaseTapChangers);
                for (PropagatedContingency contingency : propagatedContingencies) {
                    zeroFactors.forEach(lfFactor -> valueWriter.write(lfFactor.getContext(), contingency.getContingency().getId(), contingency.getIndex(), 0, Double.NaN));
                    List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().map(contingencyElementByBranch::get).collect(Collectors.toList());
                    calculateSensitivityValues(factorGroups, factorsStates, contingenciesStates, flowStates,
                            contingencyElements, contingency.getContingency().getId(), contingency.getIndex(), valueWriter);
                }
            }

            if (contingenciesByGroupOfElementsBreakingConnectivity.isEmpty()) {
                return;
            }

            // compute the contingencies with loss of connectivity
            for (ConnectivityAnalysisResult connectivityAnalysisResult : connectivityAnalysisResults.values()) {
                lfFactors.forEach(factor -> factor.setPredefinedResult(connectivityAnalysisResult.getPredefinedResult(factor)));
                Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
                // null and unused if slack is not distributed
                List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
                boolean rhsChanged = false; // true if there if the disabled buses changes the slack distribution, or the GLSK
                if (lfParameters.isDistributedSlack()) {
                    rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
                }
                if (hasGlsk) {
                    // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
                    rescaleGlsk(factorGroups, disabledBuses);
                    rhsChanged = rhsChanged || factorGroups.stream().filter(LinearGlskGroup.class::isInstance)
                        .map(LinearGlskGroup.class::cast)
                        .flatMap(group -> group.getGlskMap().keySet().stream())
                        .anyMatch(disabledBuses::contains);
                }

                // we need to recompute the factor states because the connectivity changed
                if (rhsChanged) {
                    Map<LfBus, Double> slackParticipationByBusForThisConnectivity;

                    if (lfParameters.isDistributedSlack()) {
                        participatingElementsForThisConnectivity = getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters, lfParametersExt); // will also be used to recompute the loadflow
                        slackParticipationByBusForThisConnectivity = participatingElementsForThisConnectivity.stream().collect(Collectors.toMap(
                            element -> lfNetwork.getBusById(element.getLfBus().getId()),
                            element -> -element.getFactor(),
                            Double::sum
                        ));
                    } else {
                        slackParticipationByBusForThisConnectivity = Collections.singletonMap(lfNetwork.getBusById(lfNetwork.getSlackBus().getId()), -1d);
                    }

                    computeInjectionFactors(slackParticipationByBusForThisConnectivity, factorGroups); // write the right injections in the factor groups
                    factorsStates.reset(); // avoid creating a new matrix to avoid buffer allocation time
                    fillRhsSensitivityVariable(lfNetwork, equationSystem, factorGroups, factorsStates);
                    j.solveTransposed(factorsStates); // get the states for the new connectivity
                    setBaseCaseSensitivityValues(factorGroups, factorsStates); // use this state to compute the base sensitivity (without +1-1)
                }

                Set<String> elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
                phaseTapChangerContingenciesIndexing = new PhaseTapChangerContingenciesIndexing(connectivityAnalysisResult.getContingencies(), contingencyElementByBranch, elementsToReconnect);

                flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactors, lfParameters,
                    participatingElementsForThisConnectivity, disabledBuses, Collections.emptyList());

                // compute contingencies without loss of phase tap changer
                for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
                    Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().filter(element -> !elementsToReconnect.contains(element)).map(contingencyElementByBranch::get).collect(Collectors.toList());
                    zeroFactors.forEach(lfFactor -> valueWriter.write(lfFactor.getContext(), contingency.getContingency().getId(), contingency.getIndex(), 0, Double.NaN));
                    calculateSensitivityValues(factorGroups, factorsStates, contingenciesStates, flowStates, contingencyElements,
                            contingency.getContingency().getId(), contingency.getIndex(), valueWriter);
                }

                // then we compute the ones involving the loss of a phase tap changer (because we need to recompute the load flows)
                for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> entry1 : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
                    Set<LfBranch> disabledPhaseTapChangers = entry1.getKey();
                    Collection<PropagatedContingency> propagatedContingencies = entry1.getValue();
                    flowStates = setReferenceActivePowerFlows(dcLoadFlowEngine, equationSystem, j, lfFactors, lfParameters, participatingElements, disabledBuses, disabledPhaseTapChangers);
                    for (PropagatedContingency contingency : propagatedContingencies) {
                        Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().stream().filter(element -> !elementsToReconnect.contains(element)).map(contingencyElementByBranch::get).collect(Collectors.toList());
                        zeroFactors.forEach(lfFactor -> valueWriter.write(lfFactor.getContext(), contingency.getContingency().getId(), contingency.getIndex(), 0, Double.NaN));
                        calculateSensitivityValues(factorGroups, factorsStates, contingenciesStates, flowStates, contingencyElements,
                                contingency.getContingency().getId(), contingency.getIndex(), valueWriter);
                    }
                }
            }
        }
    }
}
