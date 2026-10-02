package shite.themint.dbdcpension.pensionservice.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** The return rule (A2) on the screenshot's figures and on the edge cases. */
class ReturnCalculatorTest {

	private static InvestmentReturn calc(String contributions, String capital) {
		return ReturnCalculator.calculate(new BigDecimal(contributions), new BigDecimal(capital));
	}

	@Test
	void screenshotFigures_giveTheReturnOnTheScreen() {
		InvestmentReturn result = calc("20769.28", "25992.60");

		assertThat(result.amount()).isEqualByComparingTo("5223.32");
		assertThat(result.percentage()).isEqualByComparingTo("25.15");
	}

	@Test
	void loss_isNegative() {
		InvestmentReturn result = calc("10000.00", "9500.00");

		assertThat(result.amount()).isEqualByComparingTo("-500.00");
		assertThat(result.percentage()).isEqualByComparingTo("-5.00");
	}

	@Test
	void nothingPaidIn_hasNoPercentage_butStillAnAmount() {
		InvestmentReturn result = calc("0.00", "0.00");

		assertThat(result.amount()).isEqualByComparingTo("0.00");
		assertThat(result.percentage()).isNull();
	}

	@Test
	void nothingPaidIn_butSomeCapital_hasAnAmountAndNoPercentage() {
		InvestmentReturn result = calc("0", "150.00");

		assertThat(result.amount()).isEqualByComparingTo("150.00");
		assertThat(result.percentage()).isNull();
	}

	@Test
	void totalLoss_isMinusHundredPercent() {
		InvestmentReturn result = calc("100.00", "0.00");

		assertThat(result.amount()).isEqualByComparingTo("-100.00");
		assertThat(result.percentage()).isEqualByComparingTo("-100.00");
	}

	@Test
	void noChange_isZero() {
		InvestmentReturn result = calc("500.00", "500.00");

		assertThat(result.amount()).isEqualByComparingTo("0.00");
		assertThat(result.percentage()).isEqualByComparingTo("0.00");
	}

	@Test
	void percentageIsRoundedHalfUp_toTwoDecimals() {
		assertThat(calc("3.00", "4.00").percentage()).isEqualByComparingTo("33.33");   // 33.333...
		assertThat(calc("3.00", "5.00").percentage()).isEqualByComparingTo("66.67");   // 66.666...
		assertThat(calc("200.00", "200.01").percentage()).isEqualByComparingTo("0.01"); // 0.005 rounds up
	}

	@Test
	void amountsKeepTwoDecimals_evenWhenTheCoreSendsMore() {
		InvestmentReturn result = calc("100.004", "110.009");

		assertThat(result.amount()).isEqualTo(new BigDecimal("10.01"));
	}

	@Test
	void largeAmounts_doNotLoseCents() {
		InvestmentReturn result = calc("123456789.12", "234567890.34");

		assertThat(result.amount()).isEqualByComparingTo("111111101.22");
	}

}
