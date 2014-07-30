package examplepackage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.function.PowerFunction2D;

import examplepackage.AffectiveAgent.AgentLabel;
import negotiator.Bid;
import negotiator.analysis.BidSpace;
import negotiator.issue.ValueInteger;
import negotiator.utility.UtilitySpace;
import agents.anac.y2012.CUHKAgent.OpponentBidHistory;
import agents.anac.y2013.MetaAgent.portfolio.thenegotiatorreloaded.BidIterator;
import agents.bayesianopponentmodel.OpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;

public class Appraisal extends AffectiveAgent{
	
	private long startingTime = 0;
	private long elapsedTime  = 0;
	
	private double rSquared = 0.0;
	
	private Double minUtility=null, maxUtility=null;
	
	private double beta1, beta0;
	
	private final String[] userModels = {"RPL", "RLP", "PLR", "PRL", "LPR", "LRP"};
	
	private HashMap<String, Double> probabilities = new HashMap<String, Double>();
	private HashMap<String, Double> lastProbabilities = new HashMap<String, Double>();
	
	private FairnessType fairnessType = FairnessType.NASH;
	
	private enum FairnessType {NASH, KALAI};
	private enum Intentionality {INTENTIONAL, UNINTENTIONAL};
	private enum Time {SECONDS, MINUTES, TURNS};
	
	private Intentionality intenStatus = Intentionality.INTENTIONAL;
	
	public boolean isDesirable(OpponentModel opponentModel, Bid opponentLastBid, EvaluationType evalType, double threshold) throws Exception {
		
		BidSpace bs = new BidSpace(utilitySpace, new OpponentModelUtilSpace(opponentModel), false, true);
		double obtainedUtility = bs.ourUtilityOnPareto(opponentModel.getNormalizedUtility(opponentLastBid)); // This should be just the utility instead of utility on Pareto!

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
		
		for (i = turnCount ; i >= 1 ; i--)
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
	
	public boolean isUnexpected(Bid opponentLastBid, double thresholdValue) throws Exception {
		
		updateUserModelProbabilities(opponentLastBid);

		if (getUserModelDistance() <= thresholdValue) return false; else return true;
	}
	
	public boolean isTemporalStatusFuture(OpponentModel opponentModel, double time, double rSquaredThresholdValue, double acceptableDistanceToAspirationValue) throws Exception {
		
		computeLinearRegression();
		
		if (getrSquaredValue() >= rSquaredThresholdValue)
			if(estimateUtilityDistanceToAspirationValueAtTime(time) <= acceptableDistanceToAspirationValue)
				return true;
		
		return false;
	}
	
	private void initializeUserModelProbabilities() {
		
		for (int i = 0 ; i < userModels.length ; i++)
		{
			probabilities.put(userModels[i], (double)100/userModels.length);
		}
		
		lastProbabilities = (HashMap)probabilities.clone();
	}
	
	private void initializeUserModelProbabilities(String defaultUserModel, Double probability) {
		
		probabilities.put(defaultUserModel, probability);
		
		for (int i = 0 ; i < userModels.length ; i++)
		{
			if(!userModels[i].equals(defaultUserModel)) 
				probabilities.put(userModels[i], (double)(100-probability)/(userModels.length-1));
		}
		
		lastProbabilities = (HashMap)probabilities.clone();
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
	
	private void updateUserModelProbabilities(Bid opponentLastBid) throws Exception {
		
		updateUserModelProbabilityRecord(opponentLastBid);
		updateUserModelProbabilityLamp(opponentLastBid);
		updateUserModelProbabilityPainting(opponentLastBid);
	}
	
	private void updateUserModelProbabilityRecord(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(1).toString());
		
		switch (issueAmount) {
			case 0:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", probabilities.get("RPL")*3);
						probabilities.put("RLP", probabilities.get("RLP")*3);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", probabilities.get("PRL")*2);
						probabilities.put("LRP", probabilities.get("LRP")*2);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", probabilities.get("PLR")*1);
						probabilities.put("LPR", probabilities.get("LPR")*1);
					}
				}
				break;
			case 1:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", probabilities.get("RPL")*2);
						probabilities.put("RLP", probabilities.get("RLP")*2);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", probabilities.get("PRL")*1.5);
						probabilities.put("LRP", probabilities.get("LRP")*1.5);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", probabilities.get("PLR")*1);
						probabilities.put("LPR", probabilities.get("LPR")*1);
					}
				}
				break;
			case 2:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", (double)probabilities.get("RPL")/2);
						probabilities.put("RLP", (double)probabilities.get("RLP")/2);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", (double)probabilities.get("PRL")/1.5);
						probabilities.put("LRP", (double)probabilities.get("LRP")/1.5);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", (double)probabilities.get("PLR")/1);
						probabilities.put("LPR", (double)probabilities.get("LPR")/1);
					}
				}
				break;
			case 3:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", (double)probabilities.get("RPL")/3);
						probabilities.put("RLP", (double)probabilities.get("RLP")/3);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", (double)probabilities.get("PRL")/2);
						probabilities.put("LRP", (double)probabilities.get("LRP")/2);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", (double)probabilities.get("PLR")/1);
						probabilities.put("LPR", (double)probabilities.get("LPR")/1);
					}
				}
				break;
		}
		
		normalizeProbabilities();
	}
	
	private void updateUserModelProbabilityLamp(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(2).toString());
		
		switch (issueAmount) {
		case 0:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", probabilities.get("LPR")*3);
					probabilities.put("LRP", probabilities.get("LRP")*3);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", probabilities.get("PLR")*2);
					probabilities.put("RLP", probabilities.get("RLP")*2);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", probabilities.get("PRL")*1);
					probabilities.put("RPL", probabilities.get("RPL")*1);
				}
			}
			break;
		case 1:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", (double)probabilities.get("LPR")/2);
					probabilities.put("LRP", (double)probabilities.get("LRP")/2);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", (double)probabilities.get("PLR")/1.5);
					probabilities.put("RLP", (double)probabilities.get("RLP")/1.5);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/1);
					probabilities.put("RPL", (double)probabilities.get("RPL")/1);
				}
			}
			break;
		case 2:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", (double)probabilities.get("LPR")/3);
					probabilities.put("LRP", (double)probabilities.get("LRP")/3);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", (double)probabilities.get("PLR")/2);
					probabilities.put("RLP", (double)probabilities.get("RLP")/2);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/1);
					probabilities.put("RPL", (double)probabilities.get("RPL")/1);
				}
			}
			break;
		}
		
		normalizeProbabilities();
	}
	
	private void updateUserModelProbabilityPainting(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(3).toString());
		
		switch (issueAmount) {
		case 0:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("PRL") || userModels[i].equals("PLR")) {
					probabilities.put("PRL", probabilities.get("PRL")*3);
					probabilities.put("PLR", probabilities.get("PLR")*3);
				}
				else if(userModels[i].equals("RPL") || userModels[i].equals("LPR")) {
					probabilities.put("RPL", probabilities.get("RPL")*2);
					probabilities.put("LPR", probabilities.get("LPR")*2);						
				}
				else if(userModels[i].equals("LRP") || userModels[i].equals("RLP")) {
					probabilities.put("LRP", probabilities.get("LRP")*1);
					probabilities.put("RLP", probabilities.get("RLP")*1);
				}
			}
			break;
		case 1:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("PRL") || userModels[i].equals("PLR")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/3);
					probabilities.put("PLR", (double)probabilities.get("PLR")/3);
				}
				else if(userModels[i].equals("RPL") || userModels[i].equals("LPR")) {
					probabilities.put("RPL", (double)probabilities.get("RPL")/2);
					probabilities.put("LPR", (double)probabilities.get("LPR")/2);						
				}
				else if(userModels[i].equals("LRP") || userModels[i].equals("RLP")) {
					probabilities.put("LRP", (double)probabilities.get("LRP")/1);
					probabilities.put("RLP", (double)probabilities.get("RLP")/1);
				}
			}
			break;
		}
		
		normalizeProbabilities();
	}
	
	private void normalizeProbabilities() {
		
		double sum = 0.0;
		
		for (int i= 0 ; i < userModels.length ; i++)
			sum += probabilities.get(userModels[i]);
		
		for (int i= 0 ; i < userModels.length ; i++)
			probabilities.put(userModels[i], (double)probabilities.get(userModels[i])/sum);
	}
	
	private double getUserModelDistance() {
		
		double sum = 0.0;
		
		for (int i= 0 ; i < userModels.length ; i++)
			sum += Math.pow(probabilities.get(userModels[i])-lastProbabilities.get(userModels[i]), 2);
		
		lastProbabilities = (HashMap)probabilities.clone();
		
		return Math.sqrt(sum);
	}
	
	private void computeLinearRegression() {
        int n = 1;

        double opponentBidUtility;
        double sumx = 0.0, sumy = 0.0, sumx2 = 0.0;
        
        while(opponentBidHistory.size() >= n) {
            opponentBidUtility = getUtility(opponentBidHistory.get(n-1));
        	sumx  += opponentBidUtility;
            sumx2 += opponentBidUtility * opponentBidUtility;
            sumy  += n;
            n++;
        }
        
        double xbar = sumx / n;
        double ybar = sumy / n;

        double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
        
        for (int i = 1; i <= n; i++) {
        	opponentBidUtility = getUtility(opponentBidHistory.get(i-1));
            xxbar += (opponentBidUtility - xbar) * (opponentBidUtility - xbar);
            yybar += (i - ybar) * (i - ybar);
            xybar += (opponentBidUtility - xbar) * (i - ybar);
        }
        
        beta1 = xybar / xxbar;
        beta0 = ybar - beta1 * xbar;

        System.out.println("y = " + beta1 + " * x + " + beta0);

        double ssr = 0.0;      // regression sum of squares
        
        for (int i = 0; i < n; i++) {
            double fit = beta1 * getUtility(opponentBidHistory.get(i)) + beta0;
            ssr += (fit - ybar) * (fit - ybar);
        }
        
        rSquared = ssr / yybar;
	}
	
	private double getrSquaredValue() {
		return rSquared;
	}
	
	private double estimateUtilityDistanceToAspirationValueAtTime(double time) throws Exception {
		return Math.abs(getUtility(utilitySpace.getMaxUtilityBid()) - getNormalizedUtility(beta1 + (time * beta0)));
	}
	
	private double getNormalizedUtility(double utilityValue) throws Exception
	{
		double value = 0.0;
		
		if (minUtility == null || maxUtility == null) findMinMaxUtility();
		
		if ((maxUtility-minUtility) != 0)
			value = (utilityValue-minUtility)/(maxUtility-minUtility);
		
		if (Double.isNaN(value) || (value == 0.0)) return 0.0;

		return (utilityValue-minUtility)/(maxUtility-minUtility);
	}
	
	private void findMinMaxUtility() throws Exception
	{
		double utility;
		
		BidIterator biditer=new BidIterator(utilitySpace.getDomain());
		
		minUtility = 1.0;  maxUtility = 0.0;

		while (biditer.hasNext())
		{
			utility=getUtility(biditer.next());
			
			if (minUtility > utility) minUtility = utility;
			if (maxUtility < utility) maxUtility = utility;
		}
	}
}