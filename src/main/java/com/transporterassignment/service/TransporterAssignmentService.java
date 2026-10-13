package com.transporterassignment.service;

import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.LaneAssignmentDto;
import com.transporterassignment.dto.LaneDto;
import com.transporterassignment.dto.LaneQuoteDto;
import com.transporterassignment.dto.TransporterDto;
import com.transporterassignment.entity.LaneEntity;
import com.transporterassignment.entity.LaneQuoteEntity;
import com.transporterassignment.entity.TransporterEntity;
import com.transporterassignment.exception.InfeasibleAssignmentException;
import com.transporterassignment.exception.InvalidInputException;
import com.transporterassignment.exception.NoInputDataException;
import com.transporterassignment.exception.SolverTimeoutException;
import com.transporterassignment.repository.LaneQuoteRepository;
import com.transporterassignment.repository.LaneRepository;
import com.transporterassignment.repository.TransporterRepository;
import com.transporterassignment.solver.AssignmentSolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransporterAssignmentService {

    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final long timeBudgetMillis;

    public TransporterAssignmentService(LaneRepository laneRepository,
                                        TransporterRepository transporterRepository,
                                        LaneQuoteRepository laneQuoteRepository,
                                        @Value("${assignment.solver.time-budget-ms:8000}") long timeBudgetMillis) {
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.laneQuoteRepository = laneQuoteRepository;
        this.timeBudgetMillis = timeBudgetMillis;
    }

    /** Replaces any previously stored data set with the one in the request. */
    @Transactional
    public void saveInput(InputRequest request) {
        validate(request);

        // Bulk deletes run immediately, so re-inserting the same ids below is safe.
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();

        laneRepository.saveAll(request.lanes().stream()
                .map(l -> new LaneEntity(l.id(), l.origin().trim(), l.destination().trim()))
                .toList());
        transporterRepository.saveAll(request.transporters().stream()
                .map(t -> new TransporterEntity(t.id(), t.name().trim()))
                .toList());

        List<LaneQuoteEntity> quotes = new ArrayList<>();
        for (TransporterDto transporter : request.transporters()) {
            for (LaneQuoteDto quote : transporter.laneQuotes()) {
                quotes.add(new LaneQuoteEntity(transporter.id(), quote.laneId(), quote.quote()));
            }
        }
        laneQuoteRepository.saveAll(quotes);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse assign(int maxTransporters) {
        List<LaneEntity> lanes = laneRepository.findAllByOrderByIdAsc();
        if (lanes.isEmpty()) {
            throw new NoInputDataException();
        }
        List<Long> laneIds = lanes.stream().map(LaneEntity::getId).toList();

        Map<Long, Map<Long, Long>> quotes = new LinkedHashMap<>();
        for (TransporterEntity transporter : transporterRepository.findAllByOrderByIdAsc()) {
            quotes.put(transporter.getId(), new LinkedHashMap<>());
        }
        for (LaneQuoteEntity quote : laneQuoteRepository.findAll()) {
            quotes.computeIfAbsent(quote.getTransporterId(), id -> new LinkedHashMap<>())
                    .put(quote.getLaneId(), quote.getQuote());
        }

        AssignmentSolver.Result result = AssignmentSolver.solve(laneIds, quotes, maxTransporters, timeBudgetMillis);

        if (result.status() == AssignmentSolver.Status.TIMED_OUT) {
            throw new SolverTimeoutException();
        }
        if (result.status() == AssignmentSolver.Status.INFEASIBLE) {
            throw new InfeasibleAssignmentException(infeasibleMessage(laneIds, quotes, maxTransporters, result));
        }

        List<LaneAssignmentDto> assignments = result.laneToTransporter().entrySet().stream()
                .map(e -> new LaneAssignmentDto(e.getKey(), e.getValue()))
                .toList();
        Boolean optimal = result.status() == AssignmentSolver.Status.OPTIMAL ? null : Boolean.FALSE;
        return new AssignmentResponse("success", result.totalCost(), assignments,
                result.selectedTransporters(), optimal);
    }

    private String infeasibleMessage(List<Long> laneIds,
                                     Map<Long, Map<Long, Long>> quotes,
                                     int maxTransporters,
                                     AssignmentSolver.Result result) {
        if (!result.unquotedLanes().isEmpty()) {
            return "No transporter has quoted for lane(s) " + result.unquotedLanes()
                    + ". Every lane needs at least one quote.";
        }
        for (int k = maxTransporters + 1; k <= quotes.size(); k++) {
            if (AssignmentSolver.solve(laneIds, quotes, k, timeBudgetMillis).hasSolution()) {
                return "Covering every lane needs at least " + k + " transporters, but maxTransporters is "
                        + maxTransporters + ".";
            }
        }
        return "The lanes cannot all be covered with the given quotes.";
    }

    private void validate(InputRequest request) {
        List<String> errors = new ArrayList<>();

        Set<Long> laneIds = new HashSet<>();
        for (LaneDto lane : request.lanes()) {
            if (!laneIds.add(lane.id())) {
                errors.add("Duplicate lane id: " + lane.id());
            }
        }

        Set<Long> transporterIds = new HashSet<>();
        for (TransporterDto transporter : request.transporters()) {
            if (!transporterIds.add(transporter.id())) {
                errors.add("Duplicate transporter id: " + transporter.id());
            }
            Set<Long> quotedLanes = new HashSet<>();
            for (LaneQuoteDto quote : transporter.laneQuotes()) {
                if (!laneIds.contains(quote.laneId())) {
                    errors.add("Transporter " + transporter.id() + " quoted for unknown lane " + quote.laneId());
                }
                if (!quotedLanes.add(quote.laneId())) {
                    errors.add("Transporter " + transporter.id() + " has more than one quote for lane "
                            + quote.laneId());
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
    }
}
