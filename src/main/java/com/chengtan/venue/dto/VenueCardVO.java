package com.chengtan.venue.dto;

import com.chengtan.venue.entity.Venue;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class VenueCardVO implements Serializable {

    private Long id;
    private String name;
    private Integer typeId;
    private String images;
    private String address;
    private BigDecimal rating;
    private Double distance;

    public static VenueCardVO from(Venue venue) {
        VenueCardVO vo = new VenueCardVO();
        vo.setId(venue.getId());
        vo.setName(venue.getName());
        vo.setTypeId(venue.getTypeId());
        vo.setImages(venue.getImages());
        vo.setAddress(venue.getAddress());
        vo.setRating(venue.getRating());
        return vo;
    }
}
