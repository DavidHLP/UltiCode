package com.ulticode.modules.subscription.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.subscription.PremiumAccessPolicy;
import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import com.ulticode.modules.subscription.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for SubscriptionService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private java.time.Clock clock;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PremiumAccessPolicy premiumAccessPolicy;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private static final String USER_ID = "test-user-id";
    private static final String SUBSCRIPTION_ID = "test-subscription-id";

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
        // Stub the Clock so LocalDateTime.now(clock) inside the service doesn't
        // NPE on a fresh @Mock — the service compares expiresAt against
        // LocalDateTime.now(clock) and stamps cancelledAt from it.
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());

        // Default policy behaviour: only ADMIN/SUPER_ADMIN bypass; ordinary
        // users get verdicts driven by hasActivePremium / hasExpired below.
        // Tests that need different policy behaviour override these stubs.
        lenient().when(premiumAccessPolicy.isAdminBypass(nullable(String.class)))
                .thenAnswer(invocation -> {
                    String role = invocation.getArgument(0);
                    return PremiumAccessPolicy.ADMIN_ROLE.equals(role)
                            || PremiumAccessPolicy.SUPER_ADMIN_ROLE.equals(role);
                });
        lenient().when(premiumAccessPolicy.hasActivePremium(nullable(Subscription.class)))
                .thenAnswer(invocation -> {
                    Subscription s = invocation.getArgument(0);
                    if (s == null) {
                        return false;
                    }
                    if (!SubscriptionStatus.ACTIVE.getValue().equals(s.getStatus())) {
                        return false;
                    }
                    LocalDateTime expiresAt = s.getExpiresAt();
                    if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now(clock))) {
                        return false;
                    }
                    SubscriptionPlan plan = SubscriptionPlan.fromValue(s.getPlan());
                    return plan != null && plan.isPremium();
                });
        lenient().when(premiumAccessPolicy.hasExpired(nullable(Subscription.class)))
                .thenAnswer(invocation -> {
                    Subscription s = invocation.getArgument(0);
                    if (s == null) {
                        return false;
                    }
                    LocalDateTime expiresAt = s.getExpiresAt();
                    return expiresAt != null && expiresAt.isBefore(LocalDateTime.now(clock));
                });
    }

    private Subscription createTestSubscription() {
        Subscription subscription = new Subscription();
        subscription.setId(SUBSCRIPTION_ID);
        subscription.setUserId(USER_ID);
        subscription.setPlan(SubscriptionPlan.PREMIUM_MONTHLY.getValue());
        subscription.setStatus(SubscriptionStatus.ACTIVE.getValue());
        subscription.setExpiresAt(LocalDateTime.now().plusMonths(1));
        subscription.setAutoRenew(true);
        subscription.setIsDeleted(false);
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());
        return subscription;
    }

    private Subscription createExpiredSubscription() {
        Subscription subscription = new Subscription();
        subscription.setId(SUBSCRIPTION_ID);
        subscription.setUserId(USER_ID);
        subscription.setPlan(SubscriptionPlan.PREMIUM_MONTHLY.getValue());
        subscription.setStatus(SubscriptionStatus.ACTIVE.getValue());
        subscription.setExpiresAt(LocalDateTime.now().minusDays(1));
        subscription.setAutoRenew(true);
        subscription.setIsDeleted(false);
        return subscription;
    }

    // ==================== hasPremiumAccess Tests ====================

    @Nested
    @DisplayName("hasPremiumAccess")
    class HasPremiumAccessTests {

        @Test
        @DisplayName("should return true for ADMIN role")
        void shouldReturnTrueForAdminRole() {
            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "ADMIN");

            // Assert
            assertTrue(result.getHasAccess());
            assertEquals("ADMIN", result.getSubscription().getPlan());
            assertEquals(SubscriptionStatus.ACTIVE.getValue(), result.getSubscription().getStatus());
        }

        @Test
        @DisplayName("should return true for SUPER_ADMIN role")
        void shouldReturnTrueForSuperAdminRole() {
            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "SUPER_ADMIN");

            // Assert
            assertTrue(result.getHasAccess());
            assertEquals("ADMIN", result.getSubscription().getPlan());
        }

        @Test
        @DisplayName("should return false when no active subscription")
        void shouldReturnFalseWhenNoActiveSubscription() {
            // Arrange
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(null);

            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "USER");

            // Assert
            assertFalse(result.getHasAccess());
            assertNull(result.getSubscription());
        }

        @Test
        @DisplayName("should return true for premium monthly plan")
        void shouldReturnTrueForPremiumMonthlyPlan() {
            // Arrange
            Subscription subscription = createTestSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);

            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "USER");

            // Assert
            assertTrue(result.getHasAccess());
            assertEquals(SubscriptionPlan.PREMIUM_MONTHLY.getValue(), result.getSubscription().getPlan());
        }

        @Test
        @DisplayName("should return true for premium yearly plan")
        void shouldReturnTrueForPremiumYearlyPlan() {
            // Arrange
            Subscription subscription = createTestSubscription();
            subscription.setPlan(SubscriptionPlan.PREMIUM_YEARLY.getValue());
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);

            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "USER");

            // Assert
            assertTrue(result.getHasAccess());
            assertEquals(SubscriptionPlan.PREMIUM_YEARLY.getValue(), result.getSubscription().getPlan());
        }

        @Test
        @DisplayName("should return false for free plan")
        void shouldReturnFalseForFreePlan() {
            // Arrange
            Subscription subscription = createTestSubscription();
            subscription.setPlan(SubscriptionPlan.FREE.getValue());
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);

            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "USER");

            // Assert
            assertFalse(result.getHasAccess());
        }

        @Test
        @DisplayName("should return false and NOT update status when subscription expired in query path")
        void shouldReturnFalseAndNotUpdateStatusWhenExpired() {
            // Arrange
            Subscription subscription = createExpiredSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);
            when(subscriptionMapper.updateStatus(any(), any())).thenReturn(1);

            // Act
            SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(USER_ID, "USER");

            // Assert: the query path is pure — it never mutates the row.
            assertFalse(result.getHasAccess());
            assertEquals(SubscriptionStatus.EXPIRED.getValue(), result.getSubscription().getStatus());
            verify(subscriptionMapper, never()).updateStatus(any(), any());
        }
    }

    // ==================== getActiveSubscription Tests ====================

    @Nested
    @DisplayName("getActiveSubscription")
    class GetActiveSubscriptionTests {

        @Test
        @DisplayName("should return active subscription DTO when found")
        void shouldReturnActiveSubscriptionDtoWhenFound() {
            // Arrange
            Subscription subscription = createTestSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);

            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(SUBSCRIPTION_ID, result.getId());
        }

        @Test
        @DisplayName("should return null when no active subscription")
        void shouldReturnNullWhenNoActiveSubscription() {
            // Arrange
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(null);

            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription(USER_ID);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null userId")
        void shouldReturnNullForNullUserId() {
            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription(null);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should return null for blank userId")
        void shouldReturnNullForBlankUserId() {
            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription("   ");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should lazy-transition expired subscription to EXPIRED on load")
        void shouldLazyTransitionExpiredSubscriptionOnLoad() {
            // Arrange: ACTIVE row whose expiresAt is already in the past
            Subscription expired = createExpiredSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(expired);
            when(subscriptionMapper.updateStatus(any(), any())).thenReturn(1);

            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription(USER_ID);

            // Assert: the load-for-update path now persists the EXPIRED status
            assertNotNull(result);
            assertEquals(SubscriptionStatus.EXPIRED.getValue(), result.getStatus());
            verify(subscriptionMapper).updateStatus(SUBSCRIPTION_ID, SubscriptionStatus.EXPIRED.getValue());
        }

        @Test
        @DisplayName("should not call updateStatus when loaded subscription is still active")
        void shouldNotCallUpdateStatusWhenActive() {
            // Arrange: ACTIVE row whose expiresAt is in the future
            Subscription active = createTestSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(active);

            // Act
            SubscriptionDTO result = subscriptionService.getActiveSubscription(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(SubscriptionStatus.ACTIVE.getValue(), result.getStatus());
            verify(subscriptionMapper, never()).updateStatus(any(), any());
        }
    }

    // ==================== getCurrentUserSubscription Tests ====================

    @Nested
    @DisplayName("getCurrentUserSubscription")
    class GetCurrentUserSubscriptionTests {

        @Test
        @DisplayName("should return current user subscription when authenticated")
        void shouldReturnCurrentUserSubscriptionWhenAuthenticated() {
            // Arrange
            Subscription subscription = createTestSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(subscription);

            // Act
            SubscriptionDTO result = subscriptionService.getCurrentUserSubscription();

            // Assert
            assertNotNull(result);
            assertEquals(SUBSCRIPTION_ID, result.getId());
        }

        @Test
        @DisplayName("should return null when no active subscription")
        void shouldReturnNullWhenNoActiveSubscription() {
            // Arrange
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(null);

            // Act
            SubscriptionDTO result = subscriptionService.getCurrentUserSubscription();

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            when(currentUserProvider.getCurrentUserId()).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> subscriptionService.getCurrentUserSubscription()
            );
            assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        }
    }

    // ==================== createSubscription Tests ====================

    @Nested
    @DisplayName("createSubscription")
    class CreateSubscriptionTests {

        @Test
        @DisplayName("should create subscription successfully")
        void shouldCreateSubscriptionSuccessfully() {
            // Arrange
            CreateSubscriptionDTO dto = new CreateSubscriptionDTO();
            dto.setPlan(SubscriptionPlan.PREMIUM_MONTHLY.getValue());

            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(null);
            when(subscriptionMapper.insert(any(Subscription.class))).thenAnswer(invocation -> {
                Subscription s = invocation.getArgument(0);
                s.setId("new-subscription-id");
                return 1;
            });

            // Act
            SubscriptionDTO result = subscriptionService.createSubscription(dto, USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(SubscriptionPlan.PREMIUM_MONTHLY.getValue(), result.getPlan());
            assertEquals(SubscriptionStatus.ACTIVE.getValue(), result.getStatus());
            verify(subscriptionMapper).insert(any(Subscription.class));
        }

        @Test
        @DisplayName("should create subscription with custom status")
        void shouldCreateSubscriptionWithCustomStatus() {
            // Arrange
            CreateSubscriptionDTO dto = new CreateSubscriptionDTO();
            dto.setPlan(SubscriptionPlan.PREMIUM_YEARLY.getValue());
            dto.setStatus(SubscriptionStatus.PENDING.getValue());
            dto.setExpiresAt(LocalDateTime.now().plusYears(1));

            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(null);
            when(subscriptionMapper.insert(any(Subscription.class))).thenReturn(1);

            // Act
            SubscriptionDTO result = subscriptionService.createSubscription(dto, USER_ID);

            // Assert
            assertEquals(SubscriptionStatus.PENDING.getValue(), result.getStatus());
        }

        @Test
        @DisplayName("should throw error when invalid plan")
        void shouldThrowErrorWhenInvalidPlan() {
            // Arrange
            CreateSubscriptionDTO dto = new CreateSubscriptionDTO();
            dto.setPlan("INVALID_PLAN");

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> subscriptionService.createSubscription(dto, USER_ID)
            );
            assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw error when active subscription exists")
        void shouldThrowErrorWhenActiveSubscriptionExists() {
            // Arrange
            CreateSubscriptionDTO dto = new CreateSubscriptionDTO();
            dto.setPlan(SubscriptionPlan.PREMIUM_MONTHLY.getValue());

            Subscription existingSubscription = createTestSubscription();
            when(subscriptionMapper.findActiveByUserId(USER_ID)).thenReturn(existingSubscription);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> subscriptionService.createSubscription(dto, USER_ID)
            );
            assertEquals(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE, exception.getErrorCode());
        }
    }

    // ==================== cancelSubscription Tests ====================

    @Nested
    @DisplayName("cancelSubscription")
    class CancelSubscriptionTests {

        @Test
        @DisplayName("should cancel subscription successfully")
        void shouldCancelSubscriptionSuccessfully() {
            // Arrange
            Subscription subscription = createTestSubscription();
            Subscription cancelledSubscription = createTestSubscription();
            cancelledSubscription.setStatus(SubscriptionStatus.CANCELLED.getValue());
            cancelledSubscription.setCancelledAt(LocalDateTime.now());

            // First call returns active subscription, second call returns cancelled subscription
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID))
                    .thenReturn(subscription)
                    .thenReturn(cancelledSubscription);
            when(subscriptionMapper.cancelById(SUBSCRIPTION_ID)).thenReturn(1);

            // Act
            SubscriptionDTO result = subscriptionService.cancelSubscription(SUBSCRIPTION_ID, USER_ID);

            // Assert
            assertNotNull(result);
            verify(subscriptionMapper).cancelById(SUBSCRIPTION_ID);
        }

        @Test
        @DisplayName("should throw SUBSCRIPTION_NOT_FOUND when not found")
        void shouldThrowSubscriptionNotFoundWhenNotFound() {
            // Arrange
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> subscriptionService.cancelSubscription(SUBSCRIPTION_ID, USER_ID)
            );
            assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw FORBIDDEN when cancelling another user's subscription")
        void shouldThrowForbiddenWhenCancellingOtherUserSubscription() {
            // Arrange
            Subscription subscription = createTestSubscription();
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID)).thenReturn(subscription);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> subscriptionService.cancelSubscription(SUBSCRIPTION_ID, "other-user-id")
            );
            assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        }
    }

    // ==================== getSubscriptionById Tests ====================

    @Nested
    @DisplayName("getSubscriptionById")
    class GetSubscriptionByIdTests {

        @Test
        @DisplayName("should return subscription DTO when found")
        void shouldReturnSubscriptionDtoWhenFound() {
            // Arrange
            Subscription subscription = createTestSubscription();
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID)).thenReturn(subscription);

            // Act
            SubscriptionDTO result = subscriptionService.getSubscriptionById(SUBSCRIPTION_ID);

            // Assert
            assertNotNull(result);
            assertEquals(SUBSCRIPTION_ID, result.getId());
        }

        @Test
        @DisplayName("should copy every persisted field onto the DTO (real projection)")
        void shouldCopyEveryPersistedFieldOntoDto() {
            // Arrange: a row carrying every DTO-visible field. This locks the
            // BeanUtils copy against silent field-name drift between entity
            // and DTO (the previous test mocked the projection away).
            LocalDateTime now = LocalDateTime.now();
            Subscription subscription = createTestSubscription();
            subscription.setCancelledAt(now.minusHours(1));
            subscription.setTransactionId("tx-123");
            subscription.setCreatedAt(now);
            subscription.setUpdatedAt(now);
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID)).thenReturn(subscription);

            // Act
            SubscriptionDTO result = subscriptionService.getSubscriptionById(SUBSCRIPTION_ID);

            // Assert
            assertNotNull(result);
            assertEquals(subscription.getId(), result.getId());
            assertEquals(subscription.getUserId(), result.getUserId());
            assertEquals(subscription.getPlan(), result.getPlan());
            assertEquals(subscription.getStatus(), result.getStatus());
            assertEquals(subscription.getExpiresAt(), result.getExpiresAt());
            assertEquals(subscription.getCancelledAt(), result.getCancelledAt());
            assertEquals(subscription.getTransactionId(), result.getTransactionId());
            assertEquals(subscription.getAutoRenew(), result.getAutoRenew());
            assertEquals(subscription.getCreatedAt(), result.getCreatedAt());
            assertEquals(subscription.getUpdatedAt(), result.getUpdatedAt());
        }

        @Test
        @DisplayName("should return null when not found")
        void shouldReturnNullWhenNotFound() {
            // Arrange
            when(subscriptionMapper.selectById(SUBSCRIPTION_ID)).thenReturn(null);

            // Act
            SubscriptionDTO result = subscriptionService.getSubscriptionById(SUBSCRIPTION_ID);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null id")
        void shouldReturnNullForNullId() {
            // Act
            SubscriptionDTO result = subscriptionService.getSubscriptionById(null);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should return null for blank id")
        void shouldReturnNullForBlankId() {
            // Act
            SubscriptionDTO result = subscriptionService.getSubscriptionById("   ");

            // Assert
            assertNull(result);
        }
    }

    // ==================== SubscriptionPlan Tests ====================

    @Nested
    @DisplayName("SubscriptionPlan enum")
    class SubscriptionPlanTests {

        @Test
        @DisplayName("should parse plan from value")
        void shouldParsePlanFromValue() {
            assertEquals(SubscriptionPlan.FREE, SubscriptionPlan.fromValue("FREE"));
            assertEquals(SubscriptionPlan.PREMIUM_MONTHLY, SubscriptionPlan.fromValue("PREMIUM_MONTHLY"));
            assertEquals(SubscriptionPlan.PREMIUM_YEARLY, SubscriptionPlan.fromValue("PREMIUM_YEARLY"));
            assertEquals(SubscriptionPlan.PREMIUM_YEARLY, SubscriptionPlan.fromValue("premium_yearly")); // case insensitive
            assertNull(SubscriptionPlan.fromValue("INVALID"));
            assertNull(SubscriptionPlan.fromValue(null));
        }

        @Test
        @DisplayName("should correctly identify premium plans")
        void shouldCorrectlyIdentifyPremiumPlans() {
            assertFalse(SubscriptionPlan.FREE.isPremium());
            assertTrue(SubscriptionPlan.PREMIUM_MONTHLY.isPremium());
            assertTrue(SubscriptionPlan.PREMIUM_YEARLY.isPremium());
        }
    }

    // ==================== SubscriptionStatus Tests ====================

    @Nested
    @DisplayName("SubscriptionStatus enum")
    class SubscriptionStatusTests {

        @Test
        @DisplayName("should parse status from value")
        void shouldParseStatusFromValue() {
            assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.fromValue("ACTIVE"));
            assertEquals(SubscriptionStatus.EXPIRED, SubscriptionStatus.fromValue("EXPIRED"));
            assertEquals(SubscriptionStatus.CANCELLED, SubscriptionStatus.fromValue("CANCELLED"));
            assertEquals(SubscriptionStatus.PENDING, SubscriptionStatus.fromValue("PENDING"));
            assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.fromValue("active")); // case insensitive
            assertNull(SubscriptionStatus.fromValue("INVALID"));
            assertNull(SubscriptionStatus.fromValue(null));
        }
    }
}
