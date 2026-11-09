package com.example.hotel.mapper;

import com.example.hotel.dto.HotelDto;
import com.example.hotel.entity.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    HotelDto toDto(Hotel hotel);

    List<HotelDto> toDtoList(List<Hotel> hotels);

    @Mapping(target = "id", ignore = true)
    Hotel toEntity(HotelDto dto);
}
