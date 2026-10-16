package kr.ktb.zura.needu.taste.service;

import java.util.Collection;

import kr.ktb.zura.needu.taste.repository.TasteAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TasteWithdrawalService {

    private final TasteAnalysisRepository tasteAnalysisRepository;

    @Transactional
    public void deleteAllByRoomIds(Collection<Long> roomIds) {
        if (!roomIds.isEmpty()) {
            tasteAnalysisRepository.deleteAllByRoomIdIn(roomIds);
        }
    }
}
