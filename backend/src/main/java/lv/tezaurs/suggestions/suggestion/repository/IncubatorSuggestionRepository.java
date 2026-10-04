package lv.tezaurs.suggestions.suggestion.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncubatorSuggestionRepository extends JpaRepository<IncubatorSuggestion, Integer> {
    List<IncubatorSuggestion> findAllByChannelIdOrderByCreatedAtAscIdAsc(Integer channelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select suggestion from IncubatorSuggestion suggestion where suggestion.id = :id")
    Optional<IncubatorSuggestion> findByIdForUpdate(@Param("id") Integer id);
}
