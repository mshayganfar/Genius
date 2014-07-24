package examplepackage;

import org.jfree.data.function.PowerFunction2D;

import negotiator.Bid;
import negotiator.analysis.BidSpace;
import negotiator.utility.UtilitySpace;
import agents.bayesianopponentmodel.OpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;

public class Appraisal extends AffectiveAgent{
	
	private int turnCount    = 0;
	private long startingTime = 0;
	private long elapsedTime  = 0;
	
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
	
	public boolean isControllable(double dValue, double alphaValue, double thresholdValue) {
		
		int i = 0;
		
		double accDenom = 0.0;
		double weight = 0.0;
		double numerator = 0.0;
		double controllabilityResult = 0.0;
		
		turnCount = getTurnCount();
		
		for (i = 1 ; i <= turnCount ; i++)
			accDenom += Math.pow(0.5, i);
		
		for (i = turnCount ; i <= 1 ; i--)
		{
			weight = (double)Math.pow(0.5, i)/accDenom;
			numerator += weight*(getAcceptedOffersCount(i)/getTotalOffersCount(i)); 
		}
		
		controllabilityResult = ((double)numerator/(turnCount*Math.pow(getTime(), dValue))) + alphaValue;
		
		if (controllabilityResult <= thresholdValue) return false; else return true;
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
	
	private int getTurnCount() {
		return this.turnCount;
	}
	
	public void setTurnCount(int turnCount) {
		this.turnCount = turnCount;
	}
	
	private long getTime() {
		return (System.currentTimeMillis()-startingTime)/1000;
	}
	
	public void startClock() {
		startingTime = System.currentTimeMillis();
	}
}
