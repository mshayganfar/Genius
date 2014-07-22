package examplepackage;

import negotiator.Bid;
import negotiator.analysis.BidSpace;
import negotiator.utility.UtilitySpace;
import agents.bayesianopponentmodel.OpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;

public class Appraisal extends AffectiveAgent{
	
	private enum FairnessType {NASH, KALAI};
	private enum Intentionality {INTENTIONAL, UNINTENTIONAL};
	
	private Intentionality intenStatus = Intentionality.INTENTIONAL;
	
	public boolean isDesirable(UtilitySpace utilitySpace, OpponentModel fOpponentModel, Bid opponentLastBid, double opponentUtility, EvaluationType evalType, double threshold) throws Exception {
		
		BidSpace bs = new BidSpace(utilitySpace, new OpponentModelUtilSpace(fOpponentModel), false, true);
		double obtainedUtility = bs.ourUtilityOnPareto(fOpponentModel.getNormalizedUtility(opponentLastBid));

		switch (evalType)
		{
			case BATNA:
				if ((obtainedUtility - utilitySpace.getReservationValue()) > threshold) return true; else return false;
			case MAX:
				if ((getUtility(utilitySpace.getMaxUtilityBid()) - obtainedUtility) < threshold) return true; else return false;
			case FAIR:
				if ((obtainedUtility - getFairUtilityOnPareto(bs, FairnessType.NASH)) > threshold) return true; else return false;
			default:
				System.out.println("Appraisal--Desirability Failed!");
				return false;
		}
	}
	
	public boolean isIntentional() {
		if (intenStatus == Intentionality.INTENTIONAL) return true; else return false;
	}
	
	public boolean isOpponentCause() {
		return true;
	}
	
	private double getFairUtilityOnPareto(BidSpace bidSpace, FairnessType fairType) throws Exception {
		
		switch(fairType)
		{
			case NASH:
				return getUtility(bidSpace.getNash().getBid());
			case KALAI:
				return getUtility(bidSpace.getKalaiSmorodinsky().getBid());
		}
		
		return -1.0;
	}
}
