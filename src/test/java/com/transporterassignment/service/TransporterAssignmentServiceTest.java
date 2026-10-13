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
import com.transporterassignment.repository.LaneQuoteRepository;
import com.transporterassignment.repository.LaneRepository;
import com.transporterassignment.repository.TransporterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransporterAssignmentServiceTest {

    @Mock private LaneRepository laneRepository;
    @Mock private TransporterRepository transporterRepository;
    @Mock private LaneQuoteRepository laneQuoteRepository;
    @Captor private ArgumentCaptor<List<LaneQuoteEntity>> quoteCaptor;

    private TransporterAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new TransporterAssignmentService(laneRepository, transporterRepository, laneQuoteRepository, 5_000L);
    }

    private static LaneDto lane(long id) {
        return new LaneDto(id, "Origin " + id, "Destination " + id);
    }

    private static TransporterDto transporter(long id, LaneQuoteDto... quotes) {
        return new TransporterDto(id, "Transporter " + id, List.of(quotes));
    }

    private static LaneQuoteDto quote(long laneId, long amount) {
        return new LaneQuoteDto(laneId, amount);
    }

    // ---------------------------------------------------------------- saveInput

    @Test
    @DisplayName("saveInput: clears old data, then stores lanes, transporters and quotes")
    void saveInputStoresEverything() {
        InputRequest request = new InputRequest(
                List.of(lane(1), lane(2)),
                List.of(transporter(1, quote(1, 5000), quote(2, 6000)), transporter(2, quote(2, 5500))));

        service.saveInput(request);

        InOrder order = inOrder(laneQuoteRepository, transporterRepository, laneRepository);
        order.verify(laneQuoteRepository).deleteAllInBatch();
        order.verify(transporterRepository).deleteAllInBatch();
        order.verify(laneRepository).deleteAllInBatch();
        verify(laneRepository).saveAll(org.mockito.ArgumentMatchers.<Iterable<LaneEntity>>any());
        verify(transporterRepository).saveAll(org.mockito.ArgumentMatchers.<Iterable<TransporterEntity>>any());
        verify(laneQuoteRepository).saveAll(quoteCaptor.capture());
        assertEquals(3, quoteCaptor.getValue().size());
    }

    @Test
    @DisplayName("saveInput: duplicate lane ids are rejected and nothing is touched")
    void saveInputRejectsDuplicateLanes() {
        InputRequest request = new InputRequest(
                List.of(lane(1), lane(1)),
                List.of(transporter(1, quote(1, 5000))));

        InvalidInputException ex = assertThrows(InvalidInputException.class, () -> service.saveInput(request));

        assertTrue(ex.getDetails().contains("Duplicate lane id: 1"));
        verifyNoInteractions(laneRepository, transporterRepository, laneQuoteRepository);
    }

    @Test
    @DisplayName("saveInput: duplicate transporter ids, unknown lanes and repeated quotes are all reported")
    void saveInputReportsEveryProblem() {
        InputRequest request = new InputRequest(
                List.of(lane(1)),
                List.of(transporter(1, quote(1, 5000), quote(1, 4000), quote(9, 3000)),
                        transporter(1, quote(1, 4500))));

        InvalidInputException ex = assertThrows(InvalidInputException.class, () -> service.saveInput(request));

        assertTrue(ex.getDetails().contains("Duplicate transporter id: 1"));
        assertTrue(ex.getDetails().contains("Transporter 1 quoted for unknown lane 9"));
        assertTrue(ex.getDetails().contains("Transporter 1 has more than one quote for lane 1"));
        verifyNoInteractions(laneRepository, transporterRepository, laneQuoteRepository);
    }

    // ------------------------------------------------------------------- assign

    /** Sample test case 2 from the problem statement. */
    private void stubTestCase2() {
        when(laneRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                new LaneEntity(1L, "Chandigarh", "Shimla"),
                new LaneEntity(2L, "Agra", "Kanpur"),
                new LaneEntity(3L, "Varanasi", "Gorakhpur"),
                new LaneEntity(4L, "Amritsar", "Ludhiana"),
                new LaneEntity(5L, "Coimbatore", "Madurai")));
        when(transporterRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                new TransporterEntity(1L, "Transporter X"),
                new TransporterEntity(2L, "Transporter Y"),
                new TransporterEntity(3L, "Transporter Z"),
                new TransporterEntity(4L, "Transporter W")));
        when(laneQuoteRepository.findAll()).thenReturn(List.of(
                new LaneQuoteEntity(1L, 1L, 1000L), new LaneQuoteEntity(1L, 2L, 1500L),
                new LaneQuoteEntity(2L, 3L, 2000L), new LaneQuoteEntity(2L, 4L, 1800L),
                new LaneQuoteEntity(3L, 5L, 1200L),
                new LaneQuoteEntity(4L, 1L, 1100L), new LaneQuoteEntity(4L, 4L, 1750L)));
    }

    @Test
    @DisplayName("assign: returns the optimal assignment for stored data")
    void assignReturnsOptimalAssignment() {
        stubTestCase2();

        AssignmentResponse response = service.assign(3);

        assertEquals("success", response.status());
        assertEquals(7_500L, response.totalCost());
        assertEquals(List.of(1L, 2L, 3L), response.selectedTransporters());
        assertEquals(List.of(
                        new LaneAssignmentDto(1L, 1L), new LaneAssignmentDto(2L, 1L),
                        new LaneAssignmentDto(3L, 2L), new LaneAssignmentDto(4L, 2L),
                        new LaneAssignmentDto(5L, 3L)),
                response.assignments());
        assertNull(response.optimal(), "optimal flag is only present when the result is not proven optimal");
    }

    @Test
    @DisplayName("assign: a higher limit can lower the cost")
    void assignWithHigherLimit() {
        stubTestCase2();

        AssignmentResponse response = service.assign(4);

        assertEquals(7_450L, response.totalCost());
        assertEquals(List.of(1L, 2L, 3L, 4L), response.selectedTransporters());
    }

    @Test
    @DisplayName("assign: no stored data gives NoInputDataException")
    void assignWithoutData() {
        when(laneRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertThrows(NoInputDataException.class, () -> service.assign(3));
    }

    @Test
    @DisplayName("assign: limit too small explains how many transporters are needed")
    void assignLimitTooSmall() {
        stubTestCase2();

        InfeasibleAssignmentException ex = assertThrows(InfeasibleAssignmentException.class, () -> service.assign(2));

        assertTrue(ex.getMessage().contains("at least 3 transporters"), ex.getMessage());
        assertTrue(ex.getMessage().contains("maxTransporters is 2"), ex.getMessage());
    }

    @Test
    @DisplayName("assign: a lane without any quote is named in the error")
    void assignLaneWithoutQuotes() {
        when(laneRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                new LaneEntity(1L, "A", "B"), new LaneEntity(6L, "C", "D")));
        when(transporterRepository.findAllByOrderByIdAsc()).thenReturn(List.of(new TransporterEntity(1L, "T1")));
        when(laneQuoteRepository.findAll()).thenReturn(List.of(new LaneQuoteEntity(1L, 1L, 500L)));

        InfeasibleAssignmentException ex = assertThrows(InfeasibleAssignmentException.class, () -> service.assign(3));

        assertTrue(ex.getMessage().contains("[6]"), ex.getMessage());
    }
}
