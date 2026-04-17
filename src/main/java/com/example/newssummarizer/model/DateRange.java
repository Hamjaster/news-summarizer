package com.example.newssummarizer.model;

import java.time.LocalDate;

/**
 * Represents a date interval used for filtering news queries.
 */
public class DateRange {

    // Inclusive lower bound for the article publish date.
    private LocalDate startDate;

    // Inclusive upper bound for the article publish date.
    private LocalDate endDate;

    public DateRange() {
    }

    public DateRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}