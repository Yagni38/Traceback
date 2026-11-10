
package com.traceback.traceback.service;

import com.traceback.traceback.dto.MatchResult;
import com.traceback.traceback.dto.ReportResponse;
import com.traceback.traceback.entity.ItemReport;
import com.traceback.traceback.repository.ItemReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ItemMatchingService {

    private final ItemReportRepository repository;

    public ItemMatchingService(ItemReportRepository repository) {
        this.repository = repository;
    }

    /**
     * @param includeContact true only for admins; the public gets masked contact details
     */
    @Transactional(readOnly = true)
    public List<MatchResult> findMatches(Long reportId, boolean includeContact) {

        ItemReport original = repository.findById(reportId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Report not found: " + reportId));

        String oppositeType =
                "LOST".equalsIgnoreCase(original.getReportType())
                        ? "FOUND" : "LOST";

        return repository.findAll().stream()
                .filter(item -> !item.getId().equals(reportId))
                .filter(item -> oppositeType.equalsIgnoreCase(item.getReportType()))
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .map(item -> calculateMatch(original, item, includeContact))
                .filter(match -> match.matchPercentage() >= 30)
                .sorted(Comparator.comparingInt(
                        MatchResult::matchPercentage).reversed())
                .collect(Collectors.toList());
    }

    private MatchResult calculateMatch(
            ItemReport original, ItemReport candidate, boolean includeContact) {

        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. Category match: 30 points
        if (sameText(original.getCategory(), candidate.getCategory())) {
            score += 30;
            reasons.add("Same item category");
        }

        // 2. Name and description keyword match: 30 points
        Set<String> firstWords = getKeywords(
                original.getTitle() + " " + safe(original.getDescription()));

        Set<String> secondWords = getKeywords(
                candidate.getTitle() + " " + safe(candidate.getDescription()));

        Set<String> commonWords = new HashSet<>(firstWords);
        commonWords.retainAll(secondWords);

        int keywordScore = firstWords.isEmpty() || secondWords.isEmpty()
                ? 0
                : (int) Math.round(
                    30.0 * commonWords.size()
                    / Math.min(firstWords.size(), secondWords.size()));

        keywordScore = Math.min(30, keywordScore);
        score += keywordScore;

        if (keywordScore > 0) {
            reasons.add("Matching item description keywords");
        }

        // 3. Color match: 15 points
        if (sameText(original.getColor(), candidate.getColor())) {
            score += 15;
            reasons.add("Same color");
        }

        // 4. Location match: 15 points
        if (sameText(original.getLocation(), candidate.getLocation())) {
            score += 15;
            reasons.add("Same location");
        } else {
            Set<String> locations = getKeywords(original.getLocation());
            locations.retainAll(getKeywords(candidate.getLocation()));

            if (!locations.isEmpty()) {
                score += 8;
                reasons.add("Similar location");
            }
        }

        // 5. Date proximity: 10 points
        if (original.getEventDate() != null
                && candidate.getEventDate() != null) {

            long days = Math.abs(ChronoUnit.DAYS.between(
                    original.getEventDate(),
                    candidate.getEventDate()));

            if (days <= 7) {
                score += 10;
                reasons.add("Reported within 7 days");
            } else if (days <= 30) {
                score += 5;
                reasons.add("Reported within 30 days");
            }
        }

        return new MatchResult(
                ReportResponse.from(candidate, includeContact),
                Math.min(score, 100), reasons);
    }

    private boolean sameText(String first, String second) {
        return first != null && second != null
                && !first.isBlank() && !second.isBlank()
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Set<String> getKeywords(String text) {
        Set<String> stopWords = Set.of(
                "the", "a", "an", "is", "with", "and",
                "or", "near", "at", "in", "on", "of",
                "to", "for", "my", "i", "found", "lost"
        );

        Set<String> words = new HashSet<>();

        for (String word : safe(text)
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .split("\\s+")) {

            if (word.length() > 1 && !stopWords.contains(word)) {
                words.add(word);
            }
        }

        return words;
    }
}
