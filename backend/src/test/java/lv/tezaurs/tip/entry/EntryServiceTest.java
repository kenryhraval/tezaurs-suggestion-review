package lv.tezaurs.tip.entry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.entry.dto.EntryResponse;
import lv.tezaurs.tip.entry.dto.SaveEntryRequest;
import lv.tezaurs.tip.entry.entity.Entry;
import lv.tezaurs.tip.entry.entity.EntryType;
import lv.tezaurs.tip.entry.entity.Lexeme;
import lv.tezaurs.tip.entry.repository.EntryRepository;
import lv.tezaurs.tip.entry.repository.LexemeRepository;
import lv.tezaurs.tip.tezaurs.entity.TezaursEntry;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.tip.tezaurs.service.TezaursFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntryServiceTest {
    private EntryRepository entryRepository;
    private LexemeRepository lexemeRepository;
    private ReviewRepository reviewRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private TezaursFlagService flagService;
    private EntryService service;

    @BeforeEach
    void setUp() {
        entryRepository = mock(EntryRepository.class);
        lexemeRepository = mock(LexemeRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        tezaursEntryRepository = mock(TezaursEntryRepository.class);
        flagService = mock(TezaursFlagService.class);
        service = new EntryService(entryRepository, lexemeRepository, reviewRepository,
                tezaursEntryRepository, flagService);

        when(entryRepository.save(any(Entry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lexemeRepository.save(any(Lexeme.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aMissingEntryIsNotReady() {
        UUID reviewId = UUID.randomUUID();

        assertThat(service.isReady(reviewId)).isFalse();
        verifyNoInteractions(lexemeRepository);
    }

    @Test
    void readinessRequiresExactlyOnePrimaryLexeme() {
        UUID reviewId = UUID.randomUUID();
        Entry entry = new Entry(reviewId, "jauns vārds", 1);
        Lexeme firstLexeme = new Lexeme(entry.getId(), 1);
        Lexeme secondLexeme = new Lexeme(entry.getId(), 2);
        when(entryRepository.findByReviewId(reviewId)).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(List.of(firstLexeme, secondLexeme));

        assertThat(service.isReady(reviewId)).isFalse();
        firstLexeme.setPrimary(true);
        assertThat(service.isReady(reviewId)).isTrue();
        secondLexeme.setPrimary(true);
        assertThat(service.isReady(reviewId)).isFalse();
    }

    @Test
    void createsAnEntryWithItsFirstPrimaryLexeme() {
        Review review = createEditableEntryReview();
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByReviewId(review.getId())).thenReturn(Optional.empty());

        TezaursEntry existingEntry = mock(TezaursEntry.class);
        when(existingEntry.getHomonymNo()).thenReturn(2);
        when(tezaursEntryRepository.findFirstByHeadingOrderByHomonymNoDesc("jauns vārds"))
                .thenReturn(Optional.of(existingEntry));
        Entry otherDraft = mock(Entry.class);
        when(otherDraft.getHomonymNo()).thenReturn(4);
        when(entryRepository.findFirstByHeadingOrderByHomonymNoDesc("jauns vārds"))
                .thenReturn(Optional.of(otherDraft));

        var response = service.create(review.getId());

        assertThat(response.heading()).isEqualTo("jauns vārds");
        assertThat(response.type()).isEqualTo(EntryType.WORD);
        assertThat(response.humanKey()).isEqualTo("jauns vārds:5");
        assertThat(response.lexemes()).singleElement()
                .extracting(EntryResponse.LexemeResponse::lemma)
                .isEqualTo("jauns vārds");
        assertThat(response.flags()).isEmpty();
        assertThat(response.notes()).isNull();
        assertThat(response.lexemes().getFirst().primary()).isTrue();
        verifyNoInteractions(flagService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jauns vārds", "labots vārds"})
    void editingPreservesTheAssignedHomonymNumber(String heading) {
        Review review = createEditableEntryReview();
        Entry entry = new Entry(review.getId(), "jauns vārds", 1);
        entry.update("jauns vārds", 2, EntryType.WORD, null, null, null, null, null, Map.of());
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByReviewId(review.getId())).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(List.of(createPrimaryLexeme("jauns vārds")));
        var request = new SaveEntryRequest(
                heading, EntryType.WORD, null, null, null, null, "updated note", Map.of());

        var response = service.update(review.getId(), request);

        assertThat(response.heading()).isEqualTo(heading);
        assertThat(response.notes()).isEqualTo("updated note");
        assertThat(response.humanKey()).isEqualTo(heading + ":2");
        verifyNoInteractions(tezaursEntryRepository);
        verify(entryRepository, never()).findFirstByHeadingOrderByHomonymNoDesc(any());
    }

    @Test
    void reusesAnExistingDraftWithoutOverwritingItsEdits() {
        Review review = createEditableEntryReview();
        Entry entry = new Entry(review.getId(), "jauns vārds", 1);
        entry.update("edited heading", 2, EntryType.WORD, null, null, null, null, "saved note", Map.of());
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(entryRepository.findByReviewId(review.getId())).thenReturn(Optional.of(entry));
        when(lexemeRepository.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId()))
                .thenReturn(List.of(createPrimaryLexeme("saved lemma")));

        var response = service.create(review.getId());

        assertThat(response.heading()).isEqualTo("edited heading");
        assertThat(response.notes()).isEqualTo("saved note");
        verify(entryRepository, never()).save(any());
        verify(lexemeRepository, never()).save(any());
    }

    private static Review createEditableEntryReview() {
        Review review = new Review("jauns vārds", "reviewer", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setType(ReviewType.ENTRY);
        review.setUsageDecision(lv.tezaurs.tip.review.entity.UsageDecision.USED);
        return review;
    }

    private static Lexeme createPrimaryLexeme(String lemma) {
        Lexeme lexeme = new Lexeme(UUID.randomUUID(), 1);
        lexeme.update(lemma, true, 1, 10, "jaun", null, null,
                List.of(), Map.of(), 1);
        return lexeme;
    }

    @Test
    void existingEntriesStayReadOnlyEvenWhenSenseEditingWasChosen() {
        Review review = createEditableEntryReview();
        review.setType(ReviewType.SENSE);
        review.setMatchedEntryId(42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        assertThat(service.find(review.getId())).isEmpty();
        assertThatThrownBy(() -> service.create(review.getId())).isInstanceOf(ConflictException.class);
        var request = new SaveEntryRequest("existing", EntryType.WORD, null, null, null, null, null, Map.of());
        assertThatThrownBy(() -> service.update(review.getId(), request)).isInstanceOf(ConflictException.class);
        verify(entryRepository, never()).save(any());
    }

}
