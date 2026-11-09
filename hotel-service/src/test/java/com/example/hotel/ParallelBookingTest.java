package com.example.hotel;

import com.example.hotel.dto.ConfirmAvailabilityRequest;
import com.example.hotel.entity.Hotel;
import com.example.hotel.entity.Room;
import com.example.hotel.exception.ConflictException;
import com.example.hotel.repository.HotelRepository;
import com.example.hotel.repository.RoomLockRepository;
import com.example.hotel.repository.RoomRepository;
import com.example.hotel.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ParallelBookingTest {

    private static final int THREADS = 5;

    @Autowired
    private RoomService roomService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomLockRepository roomLockRepository;

    @AfterEach
    void cleanUp() {
        roomLockRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private Room createRoom() {
        Hotel hotel = new Hotel();
        hotel.setName("Parallel Hotel");
        hotel.setAddress("Test street");
        hotelRepository.save(hotel);

        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber("1");
        room.setAvailable(true);
        return roomRepository.save(room);
    }

    private ConfirmAvailabilityRequest confirmRequest(int number) {
        ConfirmAvailabilityRequest request = new ConfirmAvailabilityRequest();
        request.setRequestId("parallel-" + number);
        request.setBookingId((long) number);
        request.setStartDate(LocalDate.of(2030, 6, 1));
        request.setEndDate(LocalDate.of(2030, 6, 5));
        return request;
    }

    @Test
    void onlyOneOfParallelRequestsBooksTheRoom() throws Exception {
        Room room = createRoom();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 1; i <= THREADS; i++) {
            ConfirmAvailabilityRequest request = confirmRequest(i);
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    roomService.confirmAvailability(room.getId(), request);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            };
            results.add(executor.submit(task));
        }

        start.countDown();
        int successCount = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        assertEquals(1, successCount);
        assertEquals(1, roomRepository.findById(room.getId()).get().getTimesBooked());
        assertEquals(1, roomLockRepository.count());
    }
}
