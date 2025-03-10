/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelectorParametersReader;
import com.powsybl.openloadflow.network.SlackBusSelector;

import static com.powsybl.openloadflow.OpenLoadFlowParameters.*;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
public final class ParameterConstants {

    public static final String SLACK_BUS_SELECTOR_DEFAULT_TYPE = MostMeshedSlackBusSelectorParametersReader.NAME;
    public static final SlackBusSelector SLACK_BUS_SELECTOR_DEFAULT_VALUE = new MostMeshedSlackBusSelector();

    public static final String THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME = "throwsExceptionInCaseOfSlackDistributionFailure";
    public static final boolean THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE = false;

    public static final String VOLTAGE_REMOTE_CONTROL_PARAM_NAME = "voltageRemoteControl";
    public static final boolean VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE = true;

    public static final String LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME = "lowImpedanceBranchMode";
    public static final LowImpedanceBranchMode LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE = LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE;

    public static final String LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME = "loadPowerFactorConstant";
    public static final boolean LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE = false;

    public static final String DC_USE_TRANSFORMER_RATIO_PARAM_NAME = "dcUseTransformerRatio";
    public static final boolean DC_USE_TRANSFORMER_RATIO_DEFAULT_VALUE = true;

    public static final String PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME = "plausibleActivePowerLimit";
    public static final double PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE = 10000;

    public static final String ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_NAME = "addRatioToLinesWithDifferentNominalVoltageAtBothEnds";
    public static final boolean ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE = false;

    private ParameterConstants() {
    }
}
