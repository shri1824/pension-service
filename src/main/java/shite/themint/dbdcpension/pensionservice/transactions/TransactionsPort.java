package shite.themint.dbdcpension.pensionservice.transactions;

/**
 * What the rest of the service needs to know about a member's transactions, without saying where they
 * come from.
 */
public interface TransactionsPort {

	/**
	 * @param page zero-based
	 * @param size how many transactions per page
	 * @throws shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException the member is unknown
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException     the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException        the core answered with data we cannot use
	 */
	TransactionList getTransactions(String participantId, int page, int size);

	/**
	 * One transaction in full. A transaction that is not this member's looks exactly like one that does not
	 * exist, so transaction ids cannot be probed.
	 *
	 * @throws shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException the member or the transaction is unknown
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException     the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException        the core answered with data we cannot use
	 */
	TransactionDetail getTransaction(String participantId, String transactionId);

}
