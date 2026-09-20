package top.whgojp.modules.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import top.whgojp.modules.ticket.entity.Ticket;

import java.util.List;

@Repository
public interface TicketMapper extends BaseMapper<Ticket> {
    List<Ticket> searchVul(@Param("q") String q, @Param("orderBy") String orderBy);

    List<Ticket> searchMine(@Param("reporter") String reporter);
}
