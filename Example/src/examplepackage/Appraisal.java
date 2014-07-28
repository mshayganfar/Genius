package examplepackage;

import java.util.List;

import org.jfree.data.function.PowerFunction2D;

import examplepackage.AffectiveAgent.AgentLabel;
import negotiator.Bid;
import negotiator.analysis.BidSpace;
import negotiator.utility.UtilitySpace;
import agents.bayesianopponentmodel.OpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;

public class Appraisal extends AffectiveAgent{
	
	private long startingTime = 0;
	private long elapsedTime  = 0;
	private FairnessType fairnessType = FairnessType.NASH;
	
	private enum FairnessType {NASH, KALAI};
	private enum Intentionality {INTENTIONAL, UNINTENTIONAL};
	private enum Time {SECONDS, MINUTES, TURNS};
	
	private Intentionality intenStatus = Intentionality.INTENTIONAL;
	
	public boolean isDesirable(OpponentModel opponentModel, Bid opponentLastBid, EvaluationType evalType, double threshold) throws Exception {
		
		BidSpace bs = new BidSpace(utilitySpace, new OpponentModelUtilSpace(opponentModel), false, true);
		double obtainedUtility = bs.ourUtilityOnPareto(opponentModel.getNormalizedUtility(opponentLastBid));

		switch (evalType)
		{
			case BATNA:
				if ((obtainedUtility - utilitySpace.getReservationValue()) > threshold) return true; else return false;
			case MAX:
				if ((getUtility(utilitySpace.getMaxUtilityBid()) - obtainedUtility) < threshold) return true; else return false;
			case FAIR:
				if ((obtainedUtility - getFairUtilityOnPareto(bs, getFairnessType())) > threshold) return true; else return false;
			default:
				System.out.println("Appraisal--Desirability Failed!");
				return false;
		}
	}
	
	public boolean isControllable(double dValue, double alphaValue, double thresholdValue) {
		
		int i = 0;
		int turnCount = getTurnCount();
		
		double accDenom = 0.0;
		double weight = 0.0;
		double numerator = 0.0;
		double controllabilityResult = 0.0;
		
		for (i = 1 ; i <= turnCount ; i++)
			accDenom += Math.pow(0.5, i);
		
		for (i = turnCount ; i <= 1 ; i--)
		{
			weight = (double)Math.pow(0.5, i)/accDenom;
			numerator += weight*(getAcceptedOffersCount(i)/getTotalOffersCount(i)); 
		}
		
		controllabilityResult = ((double)numerator/(turnCount*Math.pow(getTime(Time.SECONDS), dValue))) + alphaValue;
		
		if (controllabilityResult <= thresholdValue) return false; else return true;
	}
	
	public boolean isControllable(Bid opponentLastBid, double thresholdValue) throws Exception {
		
		if((getUtility(utilitySpace.getMaxUtilityBid()) - thresholdValue) < getUtility(opponentLastBid)) return true; else return false;
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
	
	private long getTime(Time time) {
		
		switch(time)
		{
			case SECONDS:
				return (System.currentTimeMillis()-startingTime)/1000;
			case MINUTES:
				return (System.currentTimeMillis()-startingTime)/60000;
			case TURNS:
				return getTurnCount();
			default:
				System.out.println("Wrong type of requested time!");
				return -1;
		}
	}
	
	public void startClock() {
		startingTime = System.currentTimeMillis();
	}
	
	private FairnessType getFairnessType() {
		return fairnessType;
	}
	
	public void setFairnessType(FairnessType fairnessType) {
		this.fairnessType = fairnessType;
	}
}
