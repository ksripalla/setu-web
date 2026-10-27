package moneytransfer;

/**
 * Outcome of a transfer call.
 * replayed == true means this request id was already processed earlier, so NO new money moved;
 * the original transaction id is returned (safe to retry after a timeout / double-click).
 */
public record TransferResult(long transactionId, boolean replayed) { }
