package com.ulticode.modules.notification.dispatcher;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;

import java.util.List;
import java.util.Map;

/**
 * Deep announcement-fan-out port for admin and other producers that need
 * to deliver a system announcement to many recipients at once
 * (architecture-review candidate #4).
 *
 * <p>Before the deepening, the admin service inlined the recipient
 * resolution, preference filter, batch-row assembly, and audit-anchor
 * logic in a single ~80-line method. The notification module's
 * {@link NotificationDispatcher} handled only single-recipient
 * {@link com.ulticode.modules.notification.intent.NotificationIntent}
 * dispatch, leaving the broadcast fan-out as a parallel path.
 *
 * <p>After the deepening, the broadcast path crosses this seam: the
 * producer (admin announcement, future feature flags, etc.) describes
 * one announcement and the broadcaster owns:
 * <ol>
 *   <li>Target resolution (all users, an explicit set, or no-one).</li>
 *   <li>Preference filtering for opt-out categories
 *       ({@code MARKETING}, {@code COMMUNICATION}). SECURITY / SYSTEM
 *       announcements are force-delivered (ADR-004 §2.3).</li>
 *   <li>Announcement-row persistence so each recipient gets a notification
 *       row sharing one {@code announcementId}.</li>
 *   <li>Optionally, the cross-recipient {@code NotificationIntent}
 *       emission so the dispatcher-led fan-out (channels, ledger,
 *       idempotency) reuses the same machinery as event-driven intents.</li>
 * </ol>
 *
 * <p>The admin service keeps the audit hook, edit/delete state machine,
 * and the validation policy; this port owns the broadcast mechanics.
 *
 * @author ulticode
 */
public interface AnnouncementBroadcaster {

    /**
     * Broadcast outcome summary returned to the producer (admin service)
     * for audit-context anchoring and idempotency reporting.
     *
     * @param announcementId the shared announcement id used to group all
     *                       persisted notification rows; equals
     *                       {@code representativeId} when no row was
     *                       persisted (every recipient opted out)
     * @param representativeId the id of the first persisted notification
     *                         row, or {@code announcementId} when no
     *                         rows were persisted
     * @param delivered recipient count after preference filtering
     * @param suppressed recipient count removed by preference opt-out
     * @param totalTargets total recipients before preference filtering
     */
    record Outcome(
            String announcementId,
            String representativeId,
            int delivered,
            int suppressed,
            int totalTargets
    ) {}

    /**
     * Broadcast a single announcement. Persists notification rows per
     * delivered recipient and groups them by {@code announcementId}.
     * Does NOT trigger channel fan-out (in-app row only); use
     * {@link #broadcastWithChannelFanOut} when channels must run.
     *
     * @param title           the announcement title (audit + display)
     * @param body            the announcement body (persisted + delivered)
     * @param type            intent type discriminator (e.g. {@code "SYSTEM_ANNOUNCEMENT"})
     * @param category        preference category controlling force-delivery
     *                        vs opt-out behavior
     * @param target          either {@code "ALL"} (all active users) or
     *                        {@code "USERS"} (only those in {@code userIds})
     * @param userIds         explicit recipient ids when {@code target == "USERS"};
     *                        ignored otherwise
     * @param metadata        arbitrary key/value metadata persisted on the row
     * @param existingAnnouncementId optional pre-generated announcement id
     *                        (lets the producer pin the audit anchor before
     *                        the broadcast); if {@code null} a new id is
     *                        generated
     * @return broadcast outcome summary
     */
    Outcome broadcast(String title,
                      String body,
                      String type,
                      NotificationCategory category,
                      String target,
                      List<String> userIds,
                      Map<String, Object> metadata,
                      String existingAnnouncementId);

    /**
     * Broadcast a single announcement and additionally emit a
     * {@link com.ulticode.modules.notification.intent.NotificationIntent}
     * per delivered recipient, reusing the dispatcher's
     * preference-gate + ledger + channel machinery. Channel fan-out
     * honors the same {@link NotificationCategory} preference semantics
     * as event-driven intents, so an admin {@code SECURITY} push and a
     * code-driven {@code SECURITY} push take the identical path.
     */
    Outcome broadcastWithChannelFanOut(String title,
                                       String body,
                                       String type,
                                       NotificationCategory category,
                                       String target,
                                       List<String> userIds,
                                       Map<String, Object> metadata,
                                       String existingAnnouncementId);
}