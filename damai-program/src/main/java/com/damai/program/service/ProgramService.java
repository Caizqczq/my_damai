package com.damai.program.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.exception.BizException;
import com.damai.program.dto.ProgramDetailDTO;
import com.damai.program.dto.ProgramListItem;
import com.damai.program.dto.SeatDTO;
import com.damai.program.dto.StockDTO;
import com.damai.program.entity.Program;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.ProgramMapper;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramService {
    private final ProgramMapper programMapper;
    private final TicketCategoryMapper categoryMapper;
    private final SeatMapper seatMapper;
    
    public List<ProgramListItem>list(String city){
        LambdaQueryWrapper<Program>wrapper = new LambdaQueryWrapper<>();
        if(city!=null&&!city.isBlank()){
            wrapper.eq(Program::getCity,city);
        }
        wrapper.orderByAsc(Program::getShowTime);
        
        List<Program> programs = programMapper.selectList(wrapper);
        if(programs.isEmpty()){
            return List.of();
        }
        
        List<Long>programIds = programs.stream().map(Program::getId).toList();
        Map<Long,List<TicketCategory>>categoryMap=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .in(TicketCategory::getProgramId,programIds)
        ).stream()
                .collect(Collectors.groupingBy(TicketCategory::getProgramId));

        List<ProgramListItem> result = new ArrayList<>();
        
        for (Program program : programs) {
            ProgramListItem item = new ProgramListItem();
            item.setId(program.getId());
            item.setTitle(program.getTitle());
            item.setArtist(program.getArtist());
            item.setVenue(program.getVenue());
            item.setCity(program.getCity());
            item.setShowTime(program.getShowTime());
            item.setSaleTime(program.getSaleTime());
            item.setPosterUrl(program.getPosterUrl());
            item.setStatus(program.getStatus());

            List<TicketCategory> categories = categoryMap.getOrDefault(program.getId(),List.of());
            categories.stream()
                    .map(TicketCategory::getPrice)
                    .min(BigDecimal::compareTo)
                    .ifPresent(item::setMinPrice);
            result.add(item);
        }
        return result;
    }
    
    
    public ProgramDetailDTO detail(Long id){
        Program program = programMapper.selectById(id);
        if (program == null) {
            throw new BizException(1001, "节目不存在");
        }

        List<TicketCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId, id));

        ProgramDetailDTO dto = new ProgramDetailDTO();
        dto.setId(program.getId());
        dto.setTitle(program.getTitle());
        dto.setArtist(program.getArtist());
        dto.setVenue(program.getVenue());
        dto.setCity(program.getCity());
        dto.setShowTime(program.getShowTime());
        dto.setSaleTime(program.getSaleTime());
        dto.setDescription(program.getDescription());
        dto.setStatus(program.getStatus());
        dto.setCategories(cats.stream().map(c -> {
            ProgramDetailDTO.CategoryDTO cd = new ProgramDetailDTO.CategoryDTO();
            cd.setId(c.getId());
            cd.setName(c.getName());
            cd.setPrice(c.getPrice());
            cd.setAvailableStock(c.getAvailableStock());
            return cd;
        }).toList());
        return dto;
    }
    
    public StockDTO stock(Long programId){
        List<TicketCategory>cats=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId,programId)
        );
        StockDTO stockDTO=new StockDTO();
        stockDTO.setId(programId);
        stockDTO.setCategories(
                cats.stream().map(c->{
                    StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
                    cs.setCategoryId(c.getId());
                    cs.setName(c.getName());
                    cs.setAvailable(c.getAvailableStock());
                    return cs;
                }).toList()
        ); 
        stockDTO.setTotalAvailable(cats.stream().mapToInt(TicketCategory::getAvailableStock).sum());
        return stockDTO;
    }
    
    public List<SeatDTO>seats(Long programId,Long categoryId){
        LambdaQueryWrapper<Seat> wrapper = new LambdaQueryWrapper<Seat>()
                .eq(Seat::getProgramId, programId);
        if (categoryId != null) {
            wrapper.eq(Seat::getCategoryId, categoryId);
        }
        wrapper.orderByAsc(Seat::getArea, Seat::getRowNum, Seat::getColNum);
        List<Seat> seatList = seatMapper.selectList(wrapper);

        Map<String, Map<String, List<Seat>>> grouped = seatList.stream()
                .collect(Collectors.groupingBy(Seat::getArea,
                        Collectors.groupingBy(Seat::getRowNum)));
        return grouped.entrySet().stream().map(areaEntry -> {
            SeatDTO dto = new SeatDTO();
            dto.setArea(areaEntry.getKey());
            dto.setRows(areaEntry.getValue().entrySet().stream().map(rowEntry -> {
                SeatDTO.SeatRow row = new SeatDTO.SeatRow();
                row.setRowNum(rowEntry.getKey());
                row.setSeats(rowEntry.getValue().stream().map(s -> {
                    SeatDTO.SeatInfo info = new SeatDTO.SeatInfo();
                    info.setSeatId(s.getId());
                    info.setCol(s.getColNum());
                    info.setLabel(s.getSeatLabel());
                    info.setStatus(s.getStatus());
                    info.setPrice(s.getPrice());
                    return info;
                }).toList());
                return row;
            }).toList());
            return dto;
        }).toList();
    }
}
