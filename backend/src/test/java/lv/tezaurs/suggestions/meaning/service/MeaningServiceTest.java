package lv.tezaurs.suggestions.meaning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.entity.ReviewMeaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.entity.MeaningStatus;
import lv.tezaurs.suggestions.meaning.repository.ReviewMeaningRepository;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class MeaningServiceTest {
    private ReviewMeaningRepository meaningRepository;
    private ReviewSuggestionRepository reviewRepository;
    private MeaningService service;

    @BeforeEach
    void setUp() {
        meaningRepository = mock(ReviewMeaningRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        reviewRepository = mock(ReviewSuggestionRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        service = new MeaningService(meaningRepository, reviewRepository);
        when(meaningRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ReviewMeaning.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void revisesWithoutOverwritingTheSource() {
        ReviewSuggestion review = review();
        ReviewMeaning source = new ReviewMeaning(review.getId(), MeaningOrigin.GENERATED, "original text");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(meaningRepository.findByIdAndReviewId(source.getId(), review.getId()))
                .thenReturn(Optional.of(source));

        var revision = service.revise(review.getId(), source.getId(),
                new CreateMeaningRevisionRequest("corrected text"));

        assertThat(source.getGloss()).isEqualTo("original text");
        assertThat(source.getStatus()).isEqualTo(MeaningStatus.SUPERSEDED);
        assertThat(revision.origin()).isEqualTo(MeaningOrigin.REVIEWER);
        assertThat(revision.status()).isEqualTo(MeaningStatus.CURRENT);
        assertThat(revision.supersedesMeaningId()).isEqualTo(source.getId());
        assertThat(revision.gloss()).isEqualTo("corrected text");
        assertThatThrownBy(() -> source.revise("another revision"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsUnknownReviewsAndMeanings() {
        UUID reviewId = UUID.randomUUID();
        UUID meaningId = UUID.randomUUID();
        assertThatThrownBy(() -> service.list(reviewId)).isInstanceOf(NotFoundException.class);

        ReviewSuggestion review = review();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.revise(review.getId(), meaningId,
                new CreateMeaningRevisionRequest("revision")))
                .isInstanceOf(NotFoundException.class);
    }

    private static ReviewSuggestion review() {
        return new ReviewSuggestion(42, "term");
    }
}
