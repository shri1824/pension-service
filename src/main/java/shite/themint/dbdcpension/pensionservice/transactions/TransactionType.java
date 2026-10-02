package shite.themint.dbdcpension.pensionservice.transactions;

/**
 * Kind of transaction, in our own vocabulary. The front end writes the explanation per type (the texts
 * are content keyed on this code). A kind the core sends that we do not know is REJECTED, not guessed.
 */
public enum TransactionType {
	/** Investment units were bought (positive amount) or sold (negative amount). */
	INVESTMENT_WITHDRAWAL,
	/** Units bought or sold at a later price because the entitlement day was not a trading day. */
	PRICE_DIFFERENCE
}
