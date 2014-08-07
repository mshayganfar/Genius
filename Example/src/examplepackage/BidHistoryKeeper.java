package examplepackage;

import negotiator.Bid;

public interface BidHistoryKeeper
{
	public BidHistory getOpponentHistory();

	public Bid getMyLastBid();

	public Bid getMySecondLastBid();

	public Bid getOpponentLastBid();
}
