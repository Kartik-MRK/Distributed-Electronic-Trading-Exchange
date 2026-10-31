package com.dete.simulation.control;

import com.dete.common.domain.simulation.SimulationContext;

public record MatchingAdmission(boolean allowed, String reason, SimulationContext context) {}
