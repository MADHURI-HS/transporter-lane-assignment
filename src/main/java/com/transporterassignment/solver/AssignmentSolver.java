package com.transporterassignment.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Exact solver for the "Transporter Assignment on Lanes" problem.
 *
 * <p>Problem: every lane must be served by exactly one transporter who quoted for it, using at most
 * {@code maxTransporters} different transporters, at minimum total cost. Among solutions with the same
 * cost, the one that uses the MOST transporters wins (the "maximize transporter usage" objective).
 *
 * <p>Method: depth-first branch and bound over the set of chosen transporters. For a chosen set the best
 * assignment is simply "each lane goes to its cheapest chosen transporter", so only the set has to be
 * searched. A lane-by-lane lower bound (cheapest quote among the chosen and the still-undecided
 * transporters) prunes most of the tree. The problem is NP-hard in general (it is a set-cover /
 * facility-location variant), so a time budget guards the worst case: if it runs out, the best
 * solution found so far is returned and flagged as not proven optimal.
 *
 * <p>Pure Java with no Spring dependency, so it can be unit-tested in isolation.
 */
public final class AssignmentSolver {

    private static final long INF = Long.MAX_VALUE / 4;

    public enum Status {
        /** Proven optimal. */
        OPTIMAL,
        /** Valid solution, but the time budget ran out before optimality was proven. */
        BEST_EFFORT,
        /** No valid assignment exists (lanes nobody quoted for, or too few transporters allowed). */
        INFEASIBLE,
        /** Time budget ran out before any valid solution was found. */
        TIMED_OUT
    }

    /**
     * @param laneToTransporter    lane id to transporter id, in the order the lanes were given
     * @param selectedTransporters ids of transporters that received at least one lane, ascending
     * @param unquotedLanes        lanes nobody quoted for (only set when infeasible for that reason)
     */
    public record Result(Status status,
                         long totalCost,
                         Map<Long, Long> laneToTransporter,
                         List<Long> selectedTransporters,
                         List<Long> unquotedLanes) {

        public boolean hasSolution() {
            return status == Status.OPTIMAL || status == Status.BEST_EFFORT;
        }
    }

    private final int laneCount;
    private final int transporterCount;
    private final long[] transporterIds;
    private final long[][] quote;       // [transporter][lane], INF when not quoted
    private final long[][] suffixMin;   // [from transporter index][lane] = cheapest quote among transporters i..end
    private final int maxTransporters;
    private final long deadlineNanos;

    private long nodes;
    private boolean timedOut;
    private long bestCost = Long.MAX_VALUE;
    private int bestUsed = -1;
    private int[] bestArg;

    private AssignmentSolver(long[] transporterIds, long[][] quote, int laneCount,
                             int maxTransporters, long timeBudgetMillis) {
        this.transporterIds = transporterIds;
        this.quote = quote;
        this.transporterCount = transporterIds.length;
        this.laneCount = laneCount;
        this.maxTransporters = maxTransporters;
        this.deadlineNanos = System.nanoTime() + timeBudgetMillis * 1_000_000L;
        this.suffixMin = new long[transporterCount + 1][laneCount];
        Arrays.fill(suffixMin[transporterCount], INF);
        for (int t = transporterCount - 1; t >= 0; t--) {
            for (int l = 0; l < laneCount; l++) {
                suffixMin[t][l] = Math.min(quote[t][l], suffixMin[t + 1][l]);
            }
        }
    }

    /**
     * @param laneIds          the lanes that must all be covered, in output order
     * @param quotes           transporter id to (lane id to quote); quotes for unknown lanes are ignored
     * @param maxTransporters  upper limit of different transporters that may be used (at least 1)
     * @param timeBudgetMillis wall-clock limit for the search
     */
    public static Result solve(List<Long> laneIds,
                               Map<Long, Map<Long, Long>> quotes,
                               int maxTransporters,
                               long timeBudgetMillis) {
        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be at least 1");
        }
        int laneCount = laneIds.size();
        Map<Long, Integer> laneIndex = new LinkedHashMap<>();
        for (int i = 0; i < laneCount; i++) {
            laneIndex.put(laneIds.get(i), i);
        }

        // Keep only transporters that quoted at least one known lane.
        List<Long> tIds = new ArrayList<>();
        Map<Long, long[]> rows = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, Long>> e : quotes.entrySet()) {
            long[] row = new long[laneCount];
            Arrays.fill(row, INF);
            int quoted = 0;
            for (Map.Entry<Long, Long> q : e.getValue().entrySet()) {
                Integer idx = laneIndex.get(q.getKey());
                if (idx != null) {
                    row[idx] = q.getValue();
                    quoted++;
                }
            }
            if (quoted > 0) {
                tIds.add(e.getKey());
                rows.put(e.getKey(), row);
            }
        }
        // Most lanes first (better pruning); ties by id so the result is deterministic.
        Comparator<Long> byCoverage = Comparator
                .comparingLong((Long id) -> -Arrays.stream(rows.get(id)).filter(v -> v < INF).count())
                .thenComparingLong(id -> id);
        tIds.sort(byCoverage);

        long[] ids = new long[tIds.size()];
        long[][] matrix = new long[tIds.size()][];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = tIds.get(i);
            matrix[i] = rows.get(tIds.get(i));
        }

        // Lanes that nobody quoted can never be covered.
        List<Long> unquoted = new ArrayList<>();
        for (int l = 0; l < laneCount; l++) {
            boolean covered = false;
            for (long[] row : matrix) {
                if (row[l] < INF) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                unquoted.add(laneIds.get(l));
            }
        }
        if (laneCount == 0 || !unquoted.isEmpty()) {
            return new Result(Status.INFEASIBLE, 0, Map.of(), List.of(), unquoted);
        }

        AssignmentSolver solver = new AssignmentSolver(ids, matrix, laneCount, maxTransporters, timeBudgetMillis);
        long[] curMin = new long[laneCount];
        Arrays.fill(curMin, INF);
        int[] curArg = new int[laneCount];
        Arrays.fill(curArg, -1);
        solver.search(0, 0, curMin, curArg);

        if (solver.bestArg == null) {
            return new Result(solver.timedOut ? Status.TIMED_OUT : Status.INFEASIBLE,
                    0, Map.of(), List.of(), List.of());
        }

        Map<Long, Long> assignment = new LinkedHashMap<>();
        TreeSet<Long> selected = new TreeSet<>();
        for (int l = 0; l < laneCount; l++) {
            long transporterId = ids[solver.bestArg[l]];
            assignment.put(laneIds.get(l), transporterId);
            selected.add(transporterId);
        }
        return new Result(solver.timedOut ? Status.BEST_EFFORT : Status.OPTIMAL,
                solver.bestCost, assignment, new ArrayList<>(selected), List.of());
    }

    private void search(int idx, int chosen, long[] curMin, int[] curArg) {
        if (timedOut) {
            return;
        }
        if ((++nodes & 0xFFF) == 0 && System.nanoTime() > deadlineNanos) {
            timedOut = true;
            return;
        }

        // Lower bound: every lane costs at least its cheapest quote among chosen + undecided transporters.
        long lowerBound = 0;
        boolean allCovered = true;
        for (int l = 0; l < laneCount; l++) {
            long best = Math.min(curMin[l], suffixMin[idx][l]);
            if (best >= INF) {
                return; // this lane can no longer be covered on this branch
            }
            lowerBound += best;
            if (curMin[l] >= INF) {
                allCovered = false;
            }
        }
        if (lowerBound > bestCost) {
            return;
        }

        if (allCovered) {
            consider(curMin, curArg);
        }
        if (idx == transporterCount || chosen == maxTransporters) {
            return;
        }

        // Branch 1: include transporter idx (only if it actually wins at least one lane).
        long[] nextMin = curMin.clone();
        int[] nextArg = curArg.clone();
        boolean improves = false;
        for (int l = 0; l < laneCount; l++) {
            long q = quote[idx][l];
            if (q >= INF) {
                continue;
            }
            if (q < nextMin[l] || (q == nextMin[l] && transporterIds[idx] < transporterIds[nextArg[l]])) {
                nextMin[l] = q;
                nextArg[l] = idx;
                improves = true;
            }
        }
        if (improves) {
            search(idx + 1, chosen + 1, nextMin, nextArg);
        }
        // Branch 2: skip transporter idx.
        search(idx + 1, chosen, curMin, curArg);
    }

    private void consider(long[] curMin, int[] curArg) {
        long cost = 0;
        for (long v : curMin) {
            cost += v;
        }
        int used = (int) Arrays.stream(curArg).distinct().count();
        if (cost < bestCost || (cost == bestCost && used > bestUsed)) {
            bestCost = cost;
            bestUsed = used;
            bestArg = curArg.clone();
        }
    }
}
