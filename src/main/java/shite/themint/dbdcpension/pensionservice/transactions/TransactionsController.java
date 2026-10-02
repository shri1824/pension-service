package shite.themint.dbdcpension.pensionservice.transactions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Internal API, called only by portal-bff with a service token (see ParticipationController). */
@RestController
@RequestMapping("/internal/v1/participants")
public class TransactionsController {

	/** Our own limit, lower than the core's: a page of a pension portal never needs more. */
	static final int MAX_PAGE_SIZE = 50;

	private final TransactionsPort transactionsPort;

	public TransactionsController(TransactionsPort transactionsPort) {
		this.transactionsPort = transactionsPort;
	}

	@GetMapping("/{participantId}/transactions")
	public TransactionList getTransactions(
			// Defence in depth: only plain ids reach the core, whatever the caller sends.
			@PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String participantId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return transactionsPort.getTransactions(participantId, page, size);
	}

	@GetMapping("/{participantId}/transactions/{transactionId}")
	public TransactionDetail getTransaction(
			@PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String participantId,
			// The same rule the adapter applies to every id it lists, so every listed id can be asked for.
			@PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$") String transactionId) {
		return transactionsPort.getTransaction(participantId, transactionId);
	}

}
