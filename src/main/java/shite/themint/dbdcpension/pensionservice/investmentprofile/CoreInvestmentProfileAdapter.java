package shite.themint.dbdcpension.pensionservice.investmentprofile;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.participation.ContractType;

/**
 * The anti-corruption layer for the investment choice: the only place that knows the core's URL, field
 * names and codes. A code we do not know is rejected instead of guessed, because showing a member the
 * wrong profile or pension target is worse than showing nothing. The one exception is a MISSING payout
 * preference: a member who never made a choice is heading for a fixed pension (assumption A4).
 */
@Component
class CoreInvestmentProfileAdapter implements InvestmentProfilePort {

	private static final String PATH = "/core/v1/participants/{participantId}/investment-profile";

	private final RestClient coreRestClient;

	CoreInvestmentProfileAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached, and only what does not depend on today's date (see InvestmentProfileData).
	@Override
	@Cacheable(cacheNames = CacheConfig.INVESTMENT_PROFILE_CACHE, key = "#participantId")
	public InvestmentProfileData getInvestmentProfile(String participantId) {
		return toData(CoreCalls.get(coreRestClient, PATH, participantId, CoreInvestmentProfileResponse.class));
	}

	static InvestmentProfileData toData(CoreInvestmentProfileResponse core) {
		if (core.retirementDate() == null) {
			throw new CoreResponseException("Retirement date from the core is missing");
		}
		return new InvestmentProfileData(
				toProfile(core.riskProfile()),
				toTarget(core.payoutPreference()),
				toContractType(core.planType()),
				core.retirementDate());
	}

	private static InvestmentProfile toProfile(String code) {
		return switch (code == null ? "" : code) {
			case "DEFENSIVE" -> InvestmentProfile.DEFENSIVE;
			case "NEUTRAL" -> InvestmentProfile.NEUTRAL;
			case "AGGRESSIVE" -> InvestmentProfile.AGGRESSIVE;
			case "EXECUTION_ONLY" -> InvestmentProfile.SELF_DIRECTED;
			default -> throw new CoreResponseException("Unknown risk profile from the core");
		};
	}

	private static PensionTarget toTarget(String code) {
		if (code == null) {
			return PensionTarget.FIXED;
		}
		return switch (code) {
			case "FIXED_ANNUITY" -> PensionTarget.FIXED;
			case "VARIABLE_ANNUITY" -> PensionTarget.VARIABLE;
			default -> throw new CoreResponseException("Unknown payout preference from the core");
		};
	}

	private static ContractType toContractType(String code) {
		return switch (code == null ? "" : code) {
			case "DC" -> ContractType.DC;
			case "DB" -> ContractType.DB;
			default -> throw new CoreResponseException("Unknown plan type from the core");
		};
	}

}
