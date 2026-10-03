package shite.themint.dbdcpension.pensionservice.investmentprofile;

/**
 * What kind of pension the member is heading for, in our own vocabulary. The front end writes the
 * sentence ("heading for a fixed pension").
 */
public enum PensionTarget {
	/** A fixed and guaranteed monthly amount from the retirement date. This is the default (assumption A4). */
	FIXED,
	/** A variable amount: the capital stays invested after retirement. */
	VARIABLE
}
