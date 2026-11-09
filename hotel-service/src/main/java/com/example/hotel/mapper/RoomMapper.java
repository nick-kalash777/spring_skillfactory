package com.example.hotel.mapper;

import com.example.hotel.dto.RoomDto;
import com.example.hotel.dto.RoomStatsDto;
import com.example.hotel.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(source = "hotel.id", target = "hotelId")
    RoomDto toDto(Room room);

    List<RoomDto> toDtoList(List<Room> rooms);

    @Mapping(source = "id", target = "roomId")
    @Mapping(source = "hotel.id", target = "hotelId")
    @Mapping(target = "activeBookings", ignore = true)
    RoomStatsDto toStatsDto(Room room);
}
