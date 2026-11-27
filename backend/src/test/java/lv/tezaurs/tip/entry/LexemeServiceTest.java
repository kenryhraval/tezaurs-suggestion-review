package lv.tezaurs.tip.entry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.entry.dto.SaveLexemeRequest;
import lv.tezaurs.tip.entry.entity.Entry;
import lv.tezaurs.tip.entry.entity.Lexeme;
import lv.tezaurs.tip.entry.repository.EntryRepository;
import lv.tezaurs.tip.entry.repository.LexemeRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursLexemeRoleRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursParadigmRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class LexemeServiceTest {
    private EntryRepository entryRepository;
    private LexemeRepository lexemeRepository;
    private ReviewRepository reviewRepository;
    private LexemeService service;

    @BeforeEach
    void setUp() {
        entryRepository = mock(EntryRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        lexemeRepository = mock(LexemeRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        reviewRepository = mock(ReviewRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursLexemeRoleRepository roleRepository = mock(
                TezaursLexemeRoleRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursParadigmRepository paradigmRepository = mock(
                TezaursParadigmRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        TezaursFlagService flagService = mock(TezaursFlagService.class);
        service = new LexemeService(entryRepository, lexemeRepository, reviewRepository,
                roleRepository, paradigmRepository, flagService);

        when(roleRepository.existsById(1)).thenReturn(true);
        when(paradigmRepository.existsById(10)).thenReturn(true);
        when(lexemeRepository.save(any(Lexeme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsLexemesSeparatelyFromTheEntry() {
        Review review = createEditableEntryReview();
        Entry entry = createEntry(review.getId());
        Lexeme primary = createLexeme(entry.getId(), "jauns vārds", true, 1);
        stubEntryAndLexemes(review, entry, List.of(primary));

        var response = service.create(review.getId(), createLexemeRequest("jaunvārds", false));

        assertThat(response.lemma()).isEqualTo("jaunvārds");
        assertThat(response.primary()).isFalse();
    }

    @Test
    void selectingAPrimaryLexemeDemotesThePreviousOne() {
        Review review = createEditableEntryReview();
        Entry entry = createEntry(review.getId());
        Lexeme oldPrimary = createLexeme(entry.getId(), "jauns vārds", true, 1);
        Lexeme newPrimary = createLexeme(entry.getId(), "jaunvārds", false, 2);
        stubEntryAndLexemes(review, entry, List.of(oldPrimary, newPrimary));
        when(lexemeRepository.findByIdAndEntryId(newPrimary.getId(), entry.getId()))
                .thenReturn(Optional.of(newPrimary));

        service.update(review.getId(), newPrimary.getId(), createLexemeRequest("jaunvārds", true));

        assertThat(oldPrimary.isPrimary()).isFalse();
        assertThat(newPrimary.isPrimary()).isTrue();
    }

    @Test
    void doesNotDeleteThePrimaryLexeme() {
        Review review = createEditableEntryReview();
        Entry entry = createEntry(review.getId());
        Lexeme primary = createLexeme(entry.getId(), "jauns vārds", true, 1);
        stubEntryAndLexemes(review, entry, List.of(primary));
        when(lexemeRepository.findByIdAndEntryId(primary.getId(), entry.getId()))
                .thenReturn(Optional.of(primary));

        assertThatThrownBy(() -> service.delete(review.getId(), primary.getId()))
                .isInstanceOf(ConflictException.class);
    }

    private void stubEntryAndLexemes(
            Review review, Entry entry, List<Lexeme> lexemes) {
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByReviewId(review.getId())).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(lexemes);
    }

    @Test
    void completedReviewsDoNotAllowLexemeChanges() {
        Review review = createEditableEntryReview();
        review.setStatus(ReviewStatus.COMPLETED);
        review.setLastStatusChangedBy("reviewer");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> service.create(review.getId(), createLexemeRequest("vārds", false)))
                .isInstanceOf(ConflictException.class);
    }

    private static Review createEditableEntryReview() {
        Review review = new Review("jauns vārds", "reviewer", "case01");
        review.setStatus(ReviewStatus.READY_FOR_REVIEW);
        review.setLastStatusChangedBy("reviewer");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("reviewer");
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        review.setUsageDecision(lv.tezaurs.tip.review.entity.UsageDecision.USED);
        review.setUsageNote(null);
        review.setUsageDecidedBy("reviewer");
        review.setUsageDecidedAt(java.time.Instant.now());
        return review;
    }

    private static Entry createEntry(UUID reviewId) {
        return new Entry(reviewId, "jauns vārds", 1);
    }

    private static Lexeme createLexeme(UUID entryId, String lemma, boolean primary, int orderNo) {
        Lexeme lexeme = new Lexeme(entryId, orderNo);
        lexeme.update(lemma, primary, 1, 10, null, null, null,
                List.of(), Map.of(), orderNo);
        return lexeme;
    }

    private static SaveLexemeRequest createLexemeRequest(String lemma, boolean primary) {
        return new SaveLexemeRequest(lemma, primary, 1, 10,
                null, null, null, List.of(), Map.of());
    }
}
