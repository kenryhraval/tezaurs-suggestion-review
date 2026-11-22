package lv.tezaurs.tip.sense.dto;

import lv.tezaurs.tip.review.dto.ReviewResponse;

public record SaveSenseResponse(SenseResponse sense, ReviewResponse review) {
}
