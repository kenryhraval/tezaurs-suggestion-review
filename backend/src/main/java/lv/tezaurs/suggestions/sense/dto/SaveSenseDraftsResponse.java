package lv.tezaurs.suggestions.sense.dto;

import java.util.List;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;

public record SaveSenseDraftsResponse(List<SenseDraftResponse> senses, ReviewResponse review) {
}
