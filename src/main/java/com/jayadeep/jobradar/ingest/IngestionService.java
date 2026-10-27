package com.jayadeep.jobradar.ingest;

import com.jayadeep.jobradar.config.AppProperties;
import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.Verdict;
import com.jayadeep.jobradar.scoring.FitResult;
import com.jayadeep.jobradar.scoring.ScoringService;
import com.jayadeep.jobradar.screening.DealBreakerScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pulls postings from every configured board, drops the ones already seen or with off-target titles,
 * screens the rest for deal-breakers, scores fit, and saves them.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final AppProperties props;
    private final Map<String, JobSource> sources;
    private final JobRepository repository;
    private final DealBreakerScanner scanner;
    private final ScoringService scoring;

    public IngestionService(AppProperties props,
                            List<JobSource> sources,
                            JobRepository repository,
                            DealBreakerScanner scanner,
                            ScoringService scoring) {
        this.props = props;
        this.sources = sources.stream().collect(Collectors.toMap(JobSource::name, Function.identity()));
        this.repository = repository;
        this.scanner = scanner;
        this.scoring = scoring;
    }

    public IngestReport runAll() {
        int fetched = 0;
        int added = 0;
        int filtered = 0;
        List<String> errors = new ArrayList<>();

        for (AppProperties.Board board : props.boards()) {
            JobSource source = sources.get(board.source());
            if (source == null) {
                errors.add(board.token() + ": unknown source '" + board.source() + "'");
                continue;
            }
            List<RawJob> raw;
            try {
                raw = source.fetch(board.token(), board.company());
            } catch (RuntimeException e) {
                // One broken board shouldn't stop the run.
                log.warn("Fetching {} board '{}' failed: {}", board.source(), board.token(), e.getMessage());
                errors.add(board.token() + ": " + e.getMessage());
                continue;
            }
            fetched += raw.size();
            for (RawJob job : raw) {
                if (!titleWanted(job.title())) {
                    filtered++;
                    continue;
                }
                if (repository.existsBySourceAndExternalId(job.source(), job.externalId())) {
                    continue;
                }
                save(job);
                added++;
            }
        }
        log.info("Ingestion finished: fetched={}, new={}, filteredByTitle={}, errors={}", fetched, added, filtered, errors.size());
        return new IngestReport(fetched, added, filtered, errors);
    }

    /** Screens, scores and stores one posting. Also used for jobs pasted in by hand. */
    public Job save(RawJob raw) {
        Job job = new Job(raw.source(), raw.externalId(), raw.company(), raw.title());
        job.setLocation(raw.location());
        job.setUrl(raw.url());
        job.setDescription(raw.description());
        job.setPostedAt(raw.postedAt());

        List<String> dealBreakers = scanner.scan(raw.title(), raw.description());
        job.setDealBreakers(dealBreakers);

        if (dealBreakers.isEmpty()) {
            // Only spend an LLM call on jobs that could actually be applied to.
            FitResult fit = scoring.score(raw.title(), raw.description());
            job.setFitScore(fit.score());
            job.setFitReason(fit.reason());
            job.setVerdict(Verdict.from(fit.score(), false));
        } else {
            job.setVerdict(Verdict.SKIP);
            job.setFitReason("Skipped: " + String.join("; ", dealBreakers));
        }
        return repository.save(job);
    }

    boolean titleWanted(String title) {
        if (title == null) {
            return false;
        }
        String t = title.toLowerCase(Locale.ROOT);
        boolean included = props.titleInclude().isEmpty()
                || props.titleInclude().stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)));
        boolean excluded = props.titleExclude().stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)));
        return included && !excluded;
    }

    public record IngestReport(int fetched, int added, int filteredByTitle, List<String> errors) {
    }
}
