package lv.tezaurs.tip.entry.dto;

import lv.tezaurs.tip.review.dto.ReviewResponse;

public record SaveEntryResponse(
        EntryResponse draft,
        ReviewResponse review) {
}
