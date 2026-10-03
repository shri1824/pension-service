package shite.themint.dbdcpension.pensionservice.investmentprofile;

/**
 * A change the member may make right now. The front end shows or hides its buttons from this list, but
 * the server decides: every change is checked against this list again when it is submitted.
 */
public enum AvailableAction {
	/** Change the investment profile and the pension target. */
	INVESTMENT_CHOICE,
	/** Change the retirement age. */
	RETIREMENT_AGE,
	/** Other choices on the Arrange it yourself tab; always available. */
	OTHER_CHOICES
}
