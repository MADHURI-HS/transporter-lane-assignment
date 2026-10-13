package com.transporterassignment.solver;

import com.transporterassignment.solver.AssignmentSolver.Result;
import com.transporterassignment.solver.AssignmentSolver.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentSolverTest {

    private static final long BUDGET_MS = 5_000;
    private static final List<Long> LANES_1_TO_5 = List.of(1L, 2L, 3L, 4L, 5L);

    /** Sample test case 1 from the problem statement: 7 transporters, 5 lanes. */
    private static Map<Long, Map<Long, Long>> testCase1() {
        long[][] quotes = {
                {20835, 10512, 22105, 42481, 19862},
                {48844, 31326, 18640, 45828, 18297},
                {39020, 20648, 31438, 36447, 12789},
                {14400, 44514, 14316, 10678, 13032},
                {11601, 19760, 40870, 20635, 26421},
                {35095, 12494, 17808, 36210, 39444},
                {26070, 41098, 20932, 16897, 27938}};
        Map<Long, Map<Long, Long>> result = new LinkedHashMap<>();
        for (int t = 0; t < quotes.length; t++) {
            Map<Long, Long> row = new LinkedHashMap<>();
            for (int l = 0; l < quotes[t].length; l++) {
                row.put((long) (l + 1), quotes[t][l]);
            }
            result.put((long) (t + 1), row);
        }
        return result;
    }

    /** Sample test case 2 from the problem statement: transporters quote only some lanes. */
    private static Map<Long, Map<Long, Long>> testCase2() {
        Map<Long, Map<Long, Long>> result = new LinkedHashMap<>();
        result.put(1L, Map.of(1L, 1000L, 2L, 1500L));
        result.put(2L, Map.of(3L, 2000L, 4L, 1800L));
        result.put(3L, Map.of(5L, 1200L));
        result.put(4L, Map.of(1L, 1100L, 4L, 1750L));
        return result;
    }

    @Test
    @DisplayName("test case 1, max 3 transporters: finds the true minimum (60139)")
    void testCase1MaxThree() {
        Result r = AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 3, BUDGET_MS);

        assertEquals(Status.OPTIMAL, r.status());
        assertEquals(60_139, r.totalCost());
        assertEquals(List.of(1L, 4L, 5L), r.selectedTransporters());
        assertEquals(Map.of(1L, 5L, 2L, 1L, 3L, 4L, 4L, 4L, 5L, 4L), r.laneToTransporter());
    }

    @Test
    @DisplayName("test case 1: tighter limits cost more, looser limits never cost more")
    void testCase1VariousLimits() {
        Result one = AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 1, BUDGET_MS);
        assertEquals(96_940, one.totalCost());
        assertEquals(List.of(4L), one.selectedTransporters());

        Result two = AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 2, BUDGET_MS);
        assertEquals(62_938, two.totalCost());
        assertEquals(List.of(1L, 4L), two.selectedTransporters());

        Result four = AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 4, BUDGET_MS);
        assertEquals(59_896, four.totalCost());
        assertEquals(List.of(1L, 3L, 4L, 5L), four.selectedTransporters());

        // More transporters than needed: the optimum does not change.
        Result seven = AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 7, BUDGET_MS);
        assertEquals(59_896, seven.totalCost());
    }

    @Test
    @DisplayName("test case 2, max 3: each lane's only quoting transporter is forced (7500)")
    void testCase2MaxThree() {
        Result r = AssignmentSolver.solve(LANES_1_TO_5, testCase2(), 3, BUDGET_MS);

        assertEquals(Status.OPTIMAL, r.status());
        assertEquals(7_500, r.totalCost());
        assertEquals(List.of(1L, 2L, 3L), r.selectedTransporters());
        assertEquals(Map.of(1L, 1L, 2L, 1L, 3L, 2L, 4L, 2L, 5L, 3L), r.laneToTransporter());
    }

    @Test
    @DisplayName("test case 2, max 4: the extra transporter lowers the cost (7450)")
    void testCase2MaxFour() {
        Result r = AssignmentSolver.solve(LANES_1_TO_5, testCase2(), 4, BUDGET_MS);

        assertEquals(7_450, r.totalCost());
        assertEquals(List.of(1L, 2L, 3L, 4L), r.selectedTransporters());
        assertEquals(4L, r.laneToTransporter().get(4L));
    }

    @Test
    @DisplayName("test case 2, max 2: infeasible because three transporters are unavoidable")
    void testCase2MaxTwoIsInfeasible() {
        Result r = AssignmentSolver.solve(LANES_1_TO_5, testCase2(), 2, BUDGET_MS);

        assertEquals(Status.INFEASIBLE, r.status());
        assertFalse(r.hasSolution());
        assertTrue(r.unquotedLanes().isEmpty());
    }

    @Test
    @DisplayName("a lane that nobody quoted is reported as unquoted")
    void unquotedLane() {
        Result r = AssignmentSolver.solve(List.of(1L, 2L, 3L, 4L, 5L, 6L), testCase2(), 4, BUDGET_MS);

        assertEquals(Status.INFEASIBLE, r.status());
        assertEquals(List.of(6L), r.unquotedLanes());
    }

    @Test
    @DisplayName("equal cost: the solution using more transporters is preferred")
    void maximisesUsageOnCostTies() {
        Map<Long, Map<Long, Long>> quotes = new LinkedHashMap<>();
        quotes.put(1L, Map.of(1L, 100L));
        quotes.put(2L, Map.of(1L, 100L, 2L, 100L));
        quotes.put(3L, Map.of(2L, 100L));

        // Transporter 2 alone costs 200 with one transporter; 1 + 2 also costs 200 with two.
        Result two = AssignmentSolver.solve(List.of(1L, 2L), quotes, 2, BUDGET_MS);
        assertEquals(200, two.totalCost());
        assertEquals(List.of(1L, 2L), two.selectedTransporters());

        Result one = AssignmentSolver.solve(List.of(1L, 2L), quotes, 1, BUDGET_MS);
        assertEquals(200, one.totalCost());
        assertEquals(List.of(2L), one.selectedTransporters());
    }

    @Test
    @DisplayName("maxTransporters below 1 is rejected")
    void rejectsZeroLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> AssignmentSolver.solve(LANES_1_TO_5, testCase1(), 0, BUDGET_MS));
    }

    @Test
    @DisplayName("matches exhaustive search on 2000 random instances")
    void matchesBruteForceOnRandomInstances() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 2000; iteration++) {
            int laneCount = 1 + random.nextInt(6);
            int transporterCount = 1 + random.nextInt(7);
            int limit = 1 + random.nextInt(transporterCount);

            List<Long> lanes = new ArrayList<>();
            for (int l = 1; l <= laneCount; l++) {
                lanes.add((long) l);
            }
            Map<Long, Map<Long, Long>> quotes = new LinkedHashMap<>();
            for (int t = 1; t <= transporterCount; t++) {
                Map<Long, Long> row = new LinkedHashMap<>();
                for (int l = 1; l <= laneCount; l++) {
                    if (random.nextInt(100) < 55) {
                        row.put((long) l, 1L + random.nextInt(6) * 10L);
                    }
                }
                if (!row.isEmpty()) {
                    quotes.put((long) t, row);
                }
            }
            if (quotes.isEmpty()) {
                continue;
            }

            Result actual = AssignmentSolver.solve(lanes, quotes, limit, BUDGET_MS);
            long[] expected = bruteForce(lanes, quotes, limit); // {cost, usedTransporters} or null

            if (expected == null) {
                assertFalse(actual.hasSolution(), "expected infeasible at iteration " + iteration);
                continue;
            }
            assertTrue(actual.hasSolution(), "expected a solution at iteration " + iteration);
            assertEquals(expected[0], actual.totalCost(), "cost differs at iteration " + iteration);
            assertEquals(expected[1], actual.selectedTransporters().size(), "usage differs at iteration " + iteration);

            // The returned assignment itself must be valid.
            assertEquals(laneCount, actual.laneToTransporter().size());
            assertTrue(actual.selectedTransporters().size() <= limit);
            long recomputed = 0;
            for (Map.Entry<Long, Long> e : actual.laneToTransporter().entrySet()) {
                Long quote = quotes.get(e.getValue()).get(e.getKey());
                assertTrue(quote != null, "assigned to a transporter that did not quote the lane");
                recomputed += quote;
            }
            assertEquals(actual.totalCost(), recomputed);
        }
    }

    /** Reference implementation: try every subset of at most {@code limit} transporters. */
    private static long[] bruteForce(List<Long> lanes, Map<Long, Map<Long, Long>> quotes, int limit) {
        List<Long> transporters = new ArrayList<>(quotes.keySet());
        int n = transporters.size();
        long bestCost = Long.MAX_VALUE;
        int bestUsed = -1;
        for (int mask = 1; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) > limit) {
                continue;
            }
            long cost = 0;
            boolean covered = true;
            Set<Long> used = new HashSet<>();
            for (long lane : lanes) {
                long best = Long.MAX_VALUE;
                long winner = -1;
                for (int i = 0; i < n; i++) {
                    if ((mask >> i & 1) == 1) {
                        Long quote = quotes.get(transporters.get(i)).get(lane);
                        if (quote != null && (quote < best || (quote == best && transporters.get(i) < winner))) {
                            best = quote;
                            winner = transporters.get(i);
                        }
                    }
                }
                if (winner < 0) {
                    covered = false;
                    break;
                }
                cost += best;
                used.add(winner);
            }
            if (covered && (cost < bestCost || (cost == bestCost && used.size() > bestUsed))) {
                bestCost = cost;
                bestUsed = used.size();
            }
        }
        return bestCost == Long.MAX_VALUE ? null : new long[]{bestCost, bestUsed};
    }
}
