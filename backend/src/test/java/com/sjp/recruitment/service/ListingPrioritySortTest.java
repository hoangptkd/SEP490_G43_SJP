package com.sjp.recruitment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingPrioritySortTest {

    @Test
    void newestSort_prioritizesListingPriorityThenFreshnessThenId() {
        String order = JobService.resolveRemoteJobOrder("newest");
        assertTrue(order.contains("listingPriority") || order.contains("features->>'listingPriority'"));
        assertTrue(order.contains("ORDER BY"));
        assertTrue(order.indexOf("features->>'listingPriority'") < order.indexOf("published_at")
                || order.indexOf("listingPriority") < order.indexOf("published_at"));
        assertTrue(order.contains("j.id DESC"));
    }

    @Test
    void salaryAndDeadlineSort_stillPutPriorityFirst() {
        String salary = JobService.resolveRemoteJobOrder("salary");
        String deadline = JobService.resolveRemoteJobOrder("deadline");
        assertTrue(salary.contains("salary_max"));
        assertTrue(deadline.contains("deadline"));
        assertTrue(salary.contains("features->>'listingPriority'"));
        assertTrue(deadline.contains("features->>'listingPriority'"));
        assertTrue(salary.indexOf("features->>'listingPriority'") < salary.indexOf("salary_max"));
        assertTrue(deadline.indexOf("features->>'listingPriority'") < deadline.indexOf("deadline"));
    }

    @Test
    void featuredThreshold_isOne() {
        assertFalse(FeatureLimitService.isFeatured(0));
        assertTrue(FeatureLimitService.isFeatured(1));
        assertTrue(FeatureLimitService.isFeatured(2));
        assertTrue(FeatureLimitService.isFeatured(3));
    }

    @Test
    void listingPrioritySql_requiresActiveEmployerPlan() {
        String sql = FeatureLimitService.LISTING_PRIORITY_SQL;
        assertTrue(sql.contains("created_by_employer_id"));
        assertTrue(sql.contains("listingPriority"));
        assertTrue(sql.contains("employer"));
        assertTrue(sql.toLowerCase().contains("active"));
    }

    @Test
    void premiumOutranksPlusInOrderClause() {
        // Premium (3) > Pro (2) > Plus (1) > free (0) thanks to DESC on listing priority expression
        String order = JobService.resolveRemoteJobOrder("newest");
        assertTrue(order.contains("DESC"));
        int priorityPos = order.indexOf("features->>'listingPriority'");
        int publishedPos = order.indexOf("published_at");
        assertTrue(priorityPos >= 0);
        assertTrue(publishedPos > priorityPos);
    }
}
