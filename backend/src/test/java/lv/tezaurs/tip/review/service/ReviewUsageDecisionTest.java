package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.dto.UsageDecisionRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.example.service.ExampleService;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReviewUsageDecisionTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final EntryService drafts = mock(EntryService.class);
    private final ExampleService examples = new ExampleService(reviews, exampleRepository, new CurrentUserService(), mock(SenseRepository.class));
    private final ReviewService service = new ReviewService(reviews, mock(ReviewSuggestionRepository.class),
            mock(TezaursEntryRepository.class), new CurrentUserService(),
            drafts, mock(SenseRepository.class));
    private Review review;

    @BeforeEach
    void prepareReview() {
        review = new Review("vārds", "reviewer", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("reviewer");
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(exampleRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void confirmsUsageFromTheReviewersKnowledgeAndPostponesWithoutCallingItInvented() {
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, "Nav datu"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("user");
        verifyNoInteractions(drafts);
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.USED, "Pazīstu šo vārdu."), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageNote()).isEqualTo("Pazīstu šo vārdu.");
        assertThat(review.getUsageDecidedBy()).isEqualTo("user");
        assertThat(review.getUsageDecidedAt()).isNotNull();
        verify(drafts).create(review.getId());
        var decisionTime = review.getUsageDecidedAt();
        review.setReviewedTerm("cits vārds");
        assertThat(review.getReviewedTerm()).isEqualTo("cits vārds");
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Pazīstu šo vārdu.");
        assertThat(review.getUsageDecidedBy()).isEqualTo("user");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @Test
    void distinguishesPostponementFromAnExplicitInventedDecision() {
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, "Jāturpina izpēte"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.INVENTED, "Valodnieka izvērtējums"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INVENTED);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.INVENTED);
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.INVENTED, null), "Basic dXNlcjpwYXNz");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(drafts).create(review.getId());
    }

    @ParameterizedTest
    @EnumSource(UsageDecision.class)
    void usageDecisionsRequireAUsername(UsageDecision decision) {
        assertThatThrownBy(() -> service.recordUsageDecision(review.getId(), new UsageDecisionRequest(decision, null), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isNull();
        verifyNoInteractions(drafts);
    }

    @Test
    void rejectsWritesToClosedOrExistingEntryReviews() {
        review.setStatus(ReviewStatus.COMPLETED);
        review.setLastStatusChangedBy("reviewer");
        assertThatThrownBy(() -> service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> examples.saveExample(review.getId(), external("https://example.com"), null))
                .isInstanceOf(ConflictException.class);
        Review other = new Review("lemt", "reviewer", "case01");
        other.setStatus(ReviewStatus.IN_PROGRESS);
        other.setLastStatusChangedBy("reviewer");
        other.setType(ReviewType.SENSE);
        other.setMatchedEntryId(100);
        when(reviews.findById(other.getId())).thenReturn(Optional.of(other));
        when(reviews.findLockedById(other.getId())).thenReturn(Optional.of(other));
        assertThat(examples.getExamplesForReview(other.getId())).isEmpty();
        assertThat(examples.saveExample(other.getId(), external("https://example.com"), null)).isNotNull();
        assertThatThrownBy(() -> service.recordUsageDecision(other.getId(), new UsageDecisionRequest(UsageDecision.USED, null),
                "Basic dXNlcjpwYXNz")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> examples.getExamplesForReview(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"INSUFFICIENT_DATA", "INVENTED"})
    void pausedReviewsKeepEvidenceReadableButRejectChangesUntilResumed(ReviewStatus pausedStatus) {
        UsageDecision previousDecision = pausedStatus == ReviewStatus.INSUFFICIENT_DATA
                ? UsageDecision.NOT_CONFIRMED : UsageDecision.INVENTED;
        var request = external("https://example.com");
        Example saved = new Example(review.getId(), request, "reviewer");
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(saved));
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(previousDecision, "Saglabāts vērtējums"),
                "Basic dXNlcjpwYXNz");

        assertThat(examples.getExamplesForReview(review.getId())).hasSize(1);
        assertThatThrownBy(() -> examples.saveExample(review.getId(), request, null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> examples.deleteExample(review.getId(), saved.getId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null),
                "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        assertThat(review.getUsageNote()).isEqualTo("Saglabāts vērtējums");
        verifyNoInteractions(drafts);
        verify(exampleRepository, never()).save(any());
        verify(exampleRepository, never()).delete(any());

        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("user");
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        examples.saveExample(review.getId(), request, null);
        service.recordUsageDecision(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(drafts).create(review.getId());
    }

    private static SaveExampleRequest external(String url) {
        return new SaveExampleRequest(ExampleSource.EXTERNAL, null, null, "vārds", "Citāts", url, null, null);
    }
}
