# Owner Summary Feature — Design and Implementation Plan

Status: Proposed/Documented  
Audience: Developers, QA, Product  
Related: Owner Details Page, Visits, Pets

## 1) Overview

Introduce an Owner Summary page that consolidates key information about an Owner:
- Identity and contact information
- Aggregate metrics: total visits across all pets, last visit date, pet count
- VIP status badge with rationale
- Entry point via a "View Summary" button from the Owner Details page

Primary goals are to provide a one-stop view for support and clinic staff, reduce navigation friction, and create a foundation for future analytics.

## 2) Goals and Non-Goals

Goals:
- Add a dedicated route: GET /owners/{ownerId}/summary
- Show owner details + key metrics (total visits, last visit date, number of pets)
- Compute and display VIP status
- Ensure good performance (single round-trip query, avoid N+1)
- Add basic tests for controller/service logic and edge cases

Non-Goals (for now):
- Editing data from the summary page
- Historical charts or time-series analytics
- Cross-owner comparative analytics

## 3) Functional Specification

User Stories:
- As a clinic staff member, I can click “View Summary” from an owner’s detail page and see a summary of their information and activity.
- As a clinic staff member, I can quickly identify VIP owners to prioritize service.

Acceptance Criteria:
- A button is visible on the Owner Details page linking to /owners/{ownerId}/summary
- Summary page shows:
  - Owner full name, address, telephone
  - Number of pets
  - Total visits across all pets
  - Last visit date (or “No visits yet”)
  - VIP badge if owner meets VIP criteria
- For owners with no pets or visits, metrics display zero and appropriate empty states
- Page renders under 300 ms server time on typical data sets

## 4) Domain and Data Access

Entities:
- Owner (1) — (N) Pet
- Pet (1) — (N) Visit

Aggregations:
- totalVisits = COUNT(Visit) over all pets for a given owner
- lastVisitDate = MAX(Visit.date) over all pets for a given owner
- petCount = COUNT(Pet) for the owner

Recommended Repository-level approach:
- Use a single query with LEFT JOINs to compute aggregates per ownerId
- Parameterize optional time-window if configured (see Configuration)

Example SQL (conceptual):
- Owner-level aggregates
  - COUNT of visits via JOIN from pets -> visits
  - MAX visit date
  - COUNT pets

## 5) VIP Status Logic

Definition:
- VIP if totalVisits >= threshold within an optional window
- Default threshold: 10 lifetime visits
- Window: 0 days by default (lifetime). If > 0, use last N days.

Config:
- owner.summary.vip.threshold=10
- owner.summary.vip.windowDays=0

VIP Reasoning shown to user:
- “VIP: 17 visits” or “VIP: 12 visits in last 365 days” depending on window configuration

## 6) API/Endpoint Contract

Route:
- GET /owners/{ownerId}/summary

Controller Model (DTO-like):
- ownerId: number
- fullName: string
- address: string
- city: string
- telephone: string
- petCount: number
- totalVisits: number
- lastVisitDate: date|null
- vip: boolean
- vipReason: string|null

Response:
- Server-side rendered view (Thymeleaf) consuming the above model
- HTTP 404 if owner not found

## 7) UI/UX

Entry:
- “View Summary” button on Owner Details page, right-aligned near page title or action area

Summary Page Layout:
- Owner Card: Name, contact info, VIP badge (if applicable)
- Metrics Row: Pet Count, Total Visits, Last Visit Date
- Empty States: 
  - No pets: show 0 pets
  - No visits: show “No visits yet”

Accessibility:
- VIP badge includes an aria-label (e.g., aria-label="VIP owner")
- Color contrast meets WCAG AA

i18n:
- Strings placed in messages.properties (e.g., owner.summary.title, owner.summary.totalVisits)

## 8) Configuration

Properties (application.properties / yaml):
- owner.summary.vip.threshold=10
- owner.summary.vip.windowDays=0
- owner.summary.enabled=true

Behavior:
- If owner.summary.enabled=false, route can return 404 or redirect to Owner Details.

## 9) Performance and Caching

- Use a single aggregate query for owner summary
- Optional caching to reduce load:
  - @Cacheable(cacheNames="ownerSummary", key="#ownerId")
  - Invalidate on visit creation/update/deletion and pet ownership changes

## 10) Security and Privacy

- If security is enabled, ensure only authorized roles can access owner details/summary
- Avoid exposing PII beyond what is already present in Owner Details

## 11) Observability

Metrics:
- Counter: owner.summary.view
- Timer: owner.summary.render.duration
- Gauge (optional): owner.summary.totalVisits for viewed owner
Logs:
- INFO on summary view for audit (avoid logging PII)

## 12) Testing Strategy

Unit Tests:
- VIP calculation boundaries (threshold - 1, threshold, threshold + 1)
- Windowed calculation if windowDays > 0
- Aggregation result mapping to DTO

Web/Controller Tests (MockMvc):
- GET /owners/{id}/summary returns 200 and expected model attributes
- 404 for non-existent owner

Data/Repository Tests (with H2):
- Aggregate query correctness for:
  - Owner with no pets
  - Owner with pets but no visits
  - Owner with multiple pets and visits
  - Windowed scenario

View Tests (lightweight):
- VIP badge shown/hidden based on model
- Empty states render correctly

## 13) Rollout

- Feature flag owner.summary.enabled
- Backward compatible—no schema changes required
- Smoke test scenarios:
  - New owner (no pets/visits)
  - Active owner with many visits
  - Owner with recent vs old visits (if windowed logic enabled)

## 14) Risks and Mitigations

- Performance on large datasets: mitigate with single aggregate query and optional caching
- Misclassification of VIP: make threshold configurable and test boundary conditions
- Security leakage of PII: follow existing access controls and avoid logging PII

## 15) Implementation Checklist

Core
- [x] Route: GET /owners/{ownerId}/summary
- [x] "View Summary" button on Owner Details page
- [x] Service method to compute aggregates
- [x] Thymeleaf view for summary
- [x] Basic validation and error handling

Quality
- [x] Unit tests: service logic and edge cases
- [x] Web tests: controller and view model
- [ ] Repository tests: aggregate query variants
- [ ] i18n keys in messages.properties
- [ ] Accessibility labels for VIP badge

Ops
- [ ] Config properties with defaults
- [ ] Micrometer metrics
- [ ] Optional caching and eviction hooks
- [ ] Documentation (this file)

## 16) Future Enhancements

- Small inline chart (sparklines) of visits over time
- Breakdowns: visits per pet, common visit types
- Owner segmentation tiers (Gold/Platinum) beyond simple VIP threshold
- Export summary as PDF for front-desk workflows

---

Appendix: Example Aggregate Query (conceptual)

SELECT
  o.id AS owner_id,
  COUNT(DISTINCT p.id) AS pet_count,
  COUNT(v.id) AS total_visits,
  MAX(v.visit_date) AS last_visit_date
FROM owners o
LEFT JOIN pets p ON p.owner_id = o.id
LEFT JOIN visits v ON v.pet_id = p.id
WHERE o.id = :ownerId
  AND (:windowStart IS NULL OR v.visit_date >= :windowStart)
GROUP BY o.id;

