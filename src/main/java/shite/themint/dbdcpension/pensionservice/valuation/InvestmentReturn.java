package shite.themint.dbdcpension.pensionservice.valuation;

import java.math.BigDecimal;

/**
 * What the investments earned so far (the "Return" tile). A simple return, not a time-weighted one.
 *
 * @param amount     capital value minus total contributions; negative when the investments lost value
 * @param percentage amount as a percentage of the total contributions, 2 decimals; null when nothing
 *                   was paid in, because the percentage cannot be calculated then
 */
public record InvestmentReturn(BigDecimal amount, BigDecimal percentage) {
}
