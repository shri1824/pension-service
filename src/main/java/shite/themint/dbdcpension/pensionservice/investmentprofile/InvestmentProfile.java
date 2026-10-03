package shite.themint.dbdcpension.pensionservice.investmentprofile;

/**
 * How a member's pension capital is invested, in our own vocabulary. The front end writes the sentence
 * ("invested neutrally"); a profile the core sends that we do not know is REJECTED, not guessed.
 */
public enum InvestmentProfile {
	/** Less risk, smaller fluctuations. */
	DEFENSIVE,
	/** The default profile. */
	NEUTRAL,
	/** More risk, higher peaks and deeper lows. */
	AGGRESSIVE,
	/** The member chooses the funds and carries all investment risk. */
	SELF_DIRECTED
}
