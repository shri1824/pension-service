/**
 * Everything pension-service does about an <b>investment-choice change</b>. It does one small thing: when the
 * back office completes a request, the member's cached investment profile is thrown away, so the next page load asks
 * the core again and shows the new profile at once (read your own writes).
 *
 * <pre>
 * Kafka pension.change-request.status-changed ──► ProfileCacheListener ──► Redis: delete investment-profile::{member}
 *                                                      │ unreadable event, or the cache cannot be reached
 *                                                      ▼
 *                                            pension.change-request.status-changed.dlt
 * </pre>
 *
 * <ul>
 * <li>Only a move to COMPLETED matters; every other status change, and every other kind of request, is ignored.</li>
 * <li>Deleting an entry that is not there, or twice, does no harm, so duplicate events need no special care.</li>
 * <li>Only the investment profile is evicted. The pension projection (cached 6 hours) may change when the profile
 * changes; when and how the core recalculates it is not known and is a question for the core team.</li>
 * <li>If Redis cannot be reached the listener retries, and then parks the event on the dead-letter topic. Until
 * then the old profile can show for at most the cache time (1 hour).</li>
 * </ul>
 */
package shite.themint.dbdcpension.pensionservice.investmentchange;
