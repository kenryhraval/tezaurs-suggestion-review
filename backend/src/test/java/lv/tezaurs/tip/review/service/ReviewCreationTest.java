package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.sense.service.SenseService;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ReviewCreationTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewService service = new ReviewService(reviews, mock(ReviewSuggestionRepository.class),
            mock(TezaursEntryRepository.class), new CurrentUserService(),
            mock(EntryService.class), mock(SenseRepository.class), mock(SenseService.class));

    @Test
    void createsAReviewWithASixCharacterLowercaseAlphanumericId() {
        when(reviews.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));

        Review created = service.createFromSuggestion("vārds", ReviewStatus.NEW);

        assertThat(created.getPublicId()).matches("[a-z0-9]{6}");
        assertThat(created.getReviewedTerm()).isEqualTo("vārds");
        assertThat(created.getStatus()).isEqualTo(ReviewStatus.NEW);
        assertThat(created.getLastStatusChangedBy()).isNull();
        verify(reviews).save(created);
    }

    @Test
    void letsTheTransactionFailWhenTheDatabaseRejectsTheId() {
        var collision = new DataIntegrityViolationException("Duplicate public ID");
        when(reviews.save(any(Review.class))).thenThrow(collision);

        assertThatThrownBy(() -> service.createFromSuggestion("vārds", ReviewStatus.NEW))
                .isSameAs(collision);

        verify(reviews, times(1)).save(any(Review.class));
    }
}
