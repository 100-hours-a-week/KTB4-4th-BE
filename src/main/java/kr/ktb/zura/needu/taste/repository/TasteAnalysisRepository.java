package kr.ktb.zura.needu.taste.repository;

import java.util.Collection;

import kr.ktb.zura.needu.taste.entity.TasteAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TasteAnalysisRepository extends JpaRepository<TasteAnalysis, Long> {

    @Modifying
    @Query("delete from TasteAnalysis analysis where analysis.roomId in :roomIds")
    void deleteAllByRoomIdIn(@Param("roomIds") Collection<Long> roomIds);
}
