package com.chengtan.venue.dto;

import com.chengtan.session.dto.SessionVO;
import com.chengtan.venue.entity.Venue;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VenueDetailVO implements Serializable {

    private Long id;
    private String name;
    private Integer typeId;
    private String images;
    private String address;
    private Double x;
    private Double y;
    private BigDecimal rating;
    private String description;
    private Double distance;
    private List<SessionVO> sessions;

    public static VenueDetailVO from(Venue venue) {
        VenueDetailVO vo = new VenueDetailVO();
        vo.setId(venue.getId());
        vo.setName(venue.getName());
        vo.setTypeId(venue.getTypeId());
        vo.setImages(venue.getImages());
        vo.setAddress(venue.getAddress());
        vo.setX(venue.getX());
        vo.setY(venue.getY());
        vo.setRating(venue.getRating());
        vo.setDescription(venue.getDescription());
        return vo;
    }
}
